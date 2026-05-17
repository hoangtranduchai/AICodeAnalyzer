package com.example.aicodeanalyzer.service;

import com.example.aicodeanalyzer.exception.AnalyzerException;
import com.example.aicodeanalyzer.exception.AiRateLimitException;
import com.example.aicodeanalyzer.model.AiAnalysisResult;
import com.example.aicodeanalyzer.config.AiConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calls Google Gemini directly through java.net.http.HttpClient and maps the JSON result.
 */
public class GeminiAnalyzerService {
    private static final String ANALYZER_TYPE = "GEMINI";
    private static final String ANALYZER_VERSION = "1.0.0";
    private static final String DEFAULT_MODEL = "gemini-2.5-flash";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration DEFAULT_RATE_LIMIT_RETRY = Duration.ofSeconds(30);
    private static final Duration MAX_RATE_LIMIT_RETRY = Duration.ofSeconds(75);
    private static final int DEFAULT_MAX_RETRIES = 1;
    private static final Pattern RETRY_IN_PATTERN = Pattern.compile(
            "(?i)retry in\\s+([0-9]+(?:\\.[0-9]+)?)\\s*(ms|milliseconds?|s|sec|secs|seconds?)"
    );
    private static final Pattern RETRY_DELAY_JSON_PATTERN = Pattern.compile(
            "\"retryDelay\"\\s*:\\s*\"([0-9]+(?:\\.[0-9]+)?)(ms|s)\""
    );
    private static final String PROMPT_PREFIX =
            "Hãy phân tích đoạn mã nguồn C++/Java/Python sau. "
                    + "Trả về ĐÚNG định dạng JSON với 3 key: "
                    + "'data_structures' (mảng string các CTDL đã dùng), "
                    + "'algorithms' (mảng string các thuật toán đã dùng), "
                    + "và 'ai_generated_probability' (số nguyên 0-100 đánh giá khả năng code này do AI viết "
                    + "dựa trên comment, cấu trúc, tên biến).";

    private final String apiKey;
    private final String model;
    private final URI endpoint;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final int maxRetries;

    public GeminiAnalyzerService() {
        this(
                resolveApiKey(),
                resolveModel(),
                null,
                DEFAULT_TIMEOUT,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),
                new ObjectMapper(),
                DEFAULT_MAX_RETRIES
        );
    }

    public GeminiAnalyzerService(String apiKey) {
        this(
                apiKey,
                resolveModel(),
                null,
                DEFAULT_TIMEOUT,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),
                new ObjectMapper(),
                DEFAULT_MAX_RETRIES
        );
    }

    public GeminiAnalyzerService(AiConfig aiConfig) {
        this(
                Objects.requireNonNull(aiConfig, "aiConfig must not be null").apiKey(),
                aiConfig.model(),
                aiConfig.endpoint(),
                aiConfig.timeout(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),
                new ObjectMapper(),
                aiConfig.maxRetries()
        );
        if (!aiConfig.isGeminiProvider()) {
            throw new AnalyzerException("GeminiAnalyzerService requires a Gemini provider configuration.");
        }
        if (aiConfig.useMockMode()) {
            throw new AnalyzerException("GeminiAnalyzerService requires a real GEMINI_API_KEY. Mock mode is not a Gemini API call.");
        }
    }

    public GeminiAnalyzerService(
            String apiKey,
            String model,
            URI endpoint,
            Duration timeout,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this(apiKey, model, endpoint, timeout, httpClient, objectMapper, DEFAULT_MAX_RETRIES);
    }

    public GeminiAnalyzerService(
            String apiKey,
            String model,
            URI endpoint,
            Duration timeout,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            int maxRetries
    ) {
        this.apiKey = requireText(apiKey, "Gemini API key must be configured.");
        this.model = hasText(model) ? model.trim() : DEFAULT_MODEL;
        this.endpoint = endpoint == null ? defaultEndpoint(this.model) : endpoint;
        this.timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        if (this.timeout.isZero() || this.timeout.isNegative()) {
            throw new AnalyzerException("Gemini timeout must be positive.");
        }
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        if (maxRetries < 1) {
            throw new AnalyzerException("Gemini maxRetries must be at least 1.");
        }
        this.maxRetries = maxRetries;
    }

    public GeminiAnalysisResult analyze(String sourceCode) {
        String requestBody = buildRequestBody(requireText(sourceCode, "sourceCode must not be blank."));
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = send(request);
        String analysisJson = extractAnalysisJson(response.body());
        GeminiAnalysisResult result = parseAnalysisJson(analysisJson, response.body());
        return new GeminiAnalysisResult(
                result.dataStructures(),
                result.algorithms(),
                result.aiGeneratedProbability(),
                analysisJson,
                response.body()
        );
    }

    public AiAnalysisResult analyzeForSubmission(long submissionId, String sourceCode) {
        GeminiAnalysisResult result = analyze(sourceCode);
        int probability = result.aiGeneratedProbability();
        return new AiAnalysisResult(
                null,
                submissionId,
                ANALYZER_TYPE,
                ANALYZER_VERSION,
                model,
                join(result.dataStructures()),
                join(result.algorithms()),
                null,
                null,
                BigDecimal.valueOf(probability),
                riskLevel(probability),
                "Gemini source-code analysis generated structured JSON.",
                result.rawResponse(),
                promptHash(sourceCode, result.analysisJson()),
                null,
                null
        );
    }

    private String buildRequestBody(String sourceCode) {
        try {
            ObjectNode root = objectMapper.createObjectNode();

            ArrayNode contents = root.putArray("contents");
            ObjectNode content = contents.addObject();
            content.put("role", "user");
            ArrayNode parts = content.putArray("parts");
            parts.addObject().put("text", PROMPT_PREFIX + "\n\n```text\n" + sourceCode + "\n```");

            ObjectNode generationConfig = root.putObject("generationConfig");
            generationConfig.put("temperature", 0.1);
            generationConfig.put("responseMimeType", "application/json");
            generationConfig.set("responseSchema", responseSchema());

            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new AnalyzerException("Cannot build Gemini request JSON.", ex);
        }
    }

    private ObjectNode responseSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ArrayNode required = schema.putArray("required");
        required.add("data_structures");
        required.add("algorithms");
        required.add("ai_generated_probability");

        ObjectNode properties = schema.putObject("properties");
        ObjectNode dataStructures = properties.putObject("data_structures");
        dataStructures.put("type", "array");
        dataStructures.putObject("items").put("type", "string");

        ObjectNode algorithms = properties.putObject("algorithms");
        algorithms.put("type", "array");
        algorithms.putObject("items").put("type", "string");

        ObjectNode probability = properties.putObject("ai_generated_probability");
        probability.put("type", "integer");
        probability.put("minimum", 0);
        probability.put("maximum", 100);

        return schema;
    }

    private HttpResponse<String> send(HttpRequest request) {
        int attempts = Math.max(1, maxRetries);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response;
            }

                if (response.statusCode() == 429) {
                    Duration retryAfter = retryAfter(response);
                    if (attempt < attempts) {
                        waitBeforeRetry(retryAfter);
                        continue;
                    }
                    throw rateLimitException(response, retryAfter);
                }

                if (isTransientServerError(response.statusCode()) && attempt < attempts) {
                    waitBeforeRetry(serverErrorBackoff(attempt));
                    continue;
                }

                throw new AnalyzerException("Gemini API returned HTTP " + response.statusCode()
                        + ". Body: " + truncate(response.body(), 500));
            } catch (IOException ex) {
                throw new AnalyzerException("Cannot call Gemini API due to a network error.", ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AnalyzerException("Gemini API request was interrupted.", ex);
            }
        }
        throw new AnalyzerException("Gemini API request failed after " + attempts + " attempts.");
    }

    private AiRateLimitException rateLimitException(HttpResponse<String> response, Duration retryAfter) {
        long retrySeconds = Math.max(1, retryAfter.toSeconds());
        return new AiRateLimitException(
                "Gemini API quota/rate limit reached. Retry after about " + retrySeconds
                        + " seconds. Body: " + truncate(response.body(), 500),
                response.statusCode(),
                retryAfter
        );
    }

    /*
     * EN: Gemini may return Retry-After headers, retryDelay JSON, or plain text such as "Please retry in 25s".
     * VI: Gemini có thể trả Retry-After, retryDelay JSON hoặc chuỗi như "Please retry in 25s".
     */
    private Duration retryAfter(HttpResponse<String> response) {
        Optional<String> retryAfterHeader = response.headers().firstValue("Retry-After");
        if (retryAfterHeader.isPresent()) {
            Duration parsedHeader = parseRetryDuration(retryAfterHeader.get(), "s");
            if (!parsedHeader.isZero()) {
                return clampRetryDelay(parsedHeader);
            }
        }

        Duration parsedBody = parseRetryDelayFromBody(response.body());
        return parsedBody.isZero() ? DEFAULT_RATE_LIMIT_RETRY : clampRetryDelay(parsedBody);
    }

    private Duration parseRetryDelayFromBody(String body) {
        if (!hasText(body)) {
            return Duration.ZERO;
        }

        Matcher retryDelayMatcher = RETRY_DELAY_JSON_PATTERN.matcher(body);
        if (retryDelayMatcher.find()) {
            return parseRetryDuration(retryDelayMatcher.group(1), retryDelayMatcher.group(2));
        }

        Matcher retryInMatcher = RETRY_IN_PATTERN.matcher(body);
        if (retryInMatcher.find()) {
            return parseRetryDuration(retryInMatcher.group(1), retryInMatcher.group(2));
        }

        return Duration.ZERO;
    }

    private Duration parseRetryDuration(String rawValue, String rawUnit) {
        try {
            double value = Double.parseDouble(rawValue.trim());
            String unit = rawUnit == null ? "s" : rawUnit.trim().toLowerCase();
            long millis = unit.startsWith("ms") || unit.startsWith("milli")
                    ? Math.round(value)
                    : Math.round(value * 1000);
            return Duration.ofMillis(Math.max(1, millis));
        } catch (NumberFormatException ex) {
            return Duration.ZERO;
        }
    }

    private Duration clampRetryDelay(Duration retryAfter) {
        if (retryAfter.isZero() || retryAfter.isNegative()) {
            return DEFAULT_RATE_LIMIT_RETRY;
        }
        return retryAfter.compareTo(MAX_RATE_LIMIT_RETRY) > 0 ? MAX_RATE_LIMIT_RETRY : retryAfter;
    }

    private void waitBeforeRetry(Duration delay) throws InterruptedException {
        Thread.sleep(Math.max(1, delay.toMillis()));
    }

    private boolean isTransientServerError(int statusCode) {
        return statusCode == 500 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    private Duration serverErrorBackoff(int attempt) {
        long millis = Math.min(5_000, 750L * Math.max(1, attempt));
        return Duration.ofMillis(millis);
    }

    private String extractAnalysisJson(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.hasNonNull("error")) {
                throw new AnalyzerException("Gemini API returned an error: " + truncate(root.get("error").toString(), 500));
            }
            if (looksLikeAnalysisJson(root)) {
                return root.toString();
            }

            StringBuilder textBuilder = new StringBuilder();
            JsonNode candidates = root.get("candidates");
            if (candidates != null && candidates.isArray()) {
                for (JsonNode candidate : candidates) {
                    JsonNode parts = candidate.path("content").path("parts");
                    if (!parts.isArray()) {
                        continue;
                    }
                    for (JsonNode part : parts) {
                        if (part.hasNonNull("text")) {
                            textBuilder.append(part.get("text").asText());
                        }
                    }
                }
            }

            String text = cleanJsonText(textBuilder.toString());
            if (hasText(text)) {
                return text;
            }
            throw new AnalyzerException("Gemini response does not contain analysis JSON.");
        } catch (JsonProcessingException ex) {
            throw new AnalyzerException("Gemini response is not valid JSON.", ex);
        }
    }

    private GeminiAnalysisResult parseAnalysisJson(String analysisJson, String rawResponse) {
        try {
            JsonNode root = objectMapper.readTree(cleanJsonText(analysisJson));
            List<String> dataStructures = readTextArray(root, "data_structures");
            List<String> algorithms = readTextArray(root, "algorithms");
            int probability = readProbability(root);
            return new GeminiAnalysisResult(dataStructures, algorithms, probability, root.toString(), rawResponse);
        } catch (JsonProcessingException ex) {
            throw new AnalyzerException("Cannot parse Gemini analysis JSON.", ex);
        }
    }

    private List<String> readTextArray(JsonNode root, String fieldName) {
        JsonNode node = root.get(fieldName);
        if (node == null || !node.isArray()) {
            throw new AnalyzerException("Gemini analysis JSON is missing array field: " + fieldName + ".");
        }

        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || !hasText(item.asText())) {
                throw new AnalyzerException("Gemini analysis JSON field " + fieldName + " must contain only strings.");
            }
            values.add(item.asText().trim());
        }
        return List.copyOf(values);
    }

    private int readProbability(JsonNode root) {
        JsonNode node = root.get("ai_generated_probability");
        if (node == null || !node.canConvertToInt()) {
            throw new AnalyzerException("Gemini analysis JSON is missing numeric field: ai_generated_probability.");
        }
        int value = node.asInt();
        if (value < 0 || value > 100) {
            throw new AnalyzerException("ai_generated_probability must be between 0 and 100.");
        }
        return value;
    }

    private boolean looksLikeAnalysisJson(JsonNode root) {
        return root != null
                && root.isObject()
                && root.has("data_structures")
                && root.has("algorithms")
                && root.has("ai_generated_probability");
    }

    private String cleanJsonText(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            int firstNewLine = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (firstNewLine >= 0 && lastFence > firstNewLine) {
                cleaned = cleaned.substring(firstNewLine + 1, lastFence).trim();
            }
        }
        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return cleaned.substring(firstBrace, lastBrace + 1).trim();
        }
        return cleaned;
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "-";
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (String value : values) {
            joiner.add(value);
        }
        return joiner.toString();
    }

    private String riskLevel(int probability) {
        if (probability >= 70) {
            return "HIGH";
        }
        if (probability >= 40) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String promptHash(String sourceCode, String analysisJson) {
        return sha256(sourceCode + "|" + analysisJson + "|" + ANALYZER_VERSION);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte currentByte : bytes) {
                builder.append(String.format("%02x", currentByte));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available in this Java runtime.", ex);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private static URI defaultEndpoint(String model) {
        return URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent");
    }

    private static String resolveApiKey() {
        String value = System.getProperty("gemini.api.key");
        if (hasText(value)) {
            return value.trim();
        }
        value = System.getenv("GEMINI_API_KEY");
        if (hasText(value)) {
            return value.trim();
        }
        throw new AnalyzerException("Gemini API key is missing. Set GEMINI_API_KEY or -Dgemini.api.key.");
    }

    private static String resolveModel() {
        String value = System.getProperty("gemini.model");
        if (hasText(value)) {
            return value.trim();
        }
        value = System.getenv("GEMINI_MODEL");
        return hasText(value) ? value.trim() : DEFAULT_MODEL;
    }

    private static String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new AnalyzerException(message);
        }
        return value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public record GeminiAnalysisResult(
            List<String> dataStructures,
            List<String> algorithms,
            int aiGeneratedProbability,
            String analysisJson,
            String rawResponse
    ) {
        public GeminiAnalysisResult {
            dataStructures = dataStructures == null ? List.of() : List.copyOf(dataStructures);
            algorithms = algorithms == null ? List.of() : List.copyOf(algorithms);
            if (aiGeneratedProbability < 0 || aiGeneratedProbability > 100) {
                throw new IllegalArgumentException("aiGeneratedProbability must be between 0 and 100.");
            }
        }
    }
}
