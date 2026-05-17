package com.example.aicodeanalyzer.service;

import com.example.aicodeanalyzer.analyzer.AnalysisPromptBuilder;
import com.example.aicodeanalyzer.config.AiConfig;
import com.example.aicodeanalyzer.exception.AnalyzerException;
import com.example.aicodeanalyzer.model.AiAnalysisResult;
import com.example.aicodeanalyzer.model.SourceCodeDetail;
import com.example.aicodeanalyzer.util.SecretUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * Sends source code to the configured AI provider and maps structured JSON output to AiAnalysisResult.
 */
public class OpenAIAnalyzerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAIAnalyzerService.class);
    private static final String ANALYZER_TYPE = "OPENAI";
    private static final String ANALYZER_VERSION = "1.0.0";
    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofMillis(500);

    private final AiConfig aiConfig;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AnalysisPromptBuilder promptBuilder;
    private final Duration retryDelay;
    private final AiAnalysisJsonMapper analysisJsonMapper;
    private final MockAiAnalysisBuilder mockAnalysisBuilder;

    public OpenAIAnalyzerService() {
        this(
                AiConfig.load(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),
                new ObjectMapper(),
                new AnalysisPromptBuilder(),
                DEFAULT_RETRY_DELAY
        );
    }

    public OpenAIAnalyzerService(AiConfig aiConfig) {
        this(
                aiConfig,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),
                new ObjectMapper(),
                new AnalysisPromptBuilder(),
                DEFAULT_RETRY_DELAY
        );
    }

    public OpenAIAnalyzerService(
            AiConfig aiConfig,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            AnalysisPromptBuilder promptBuilder,
            Duration retryDelay
    ) {
        this.aiConfig = Objects.requireNonNull(aiConfig, "aiConfig must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.promptBuilder = promptBuilder == null ? new AnalysisPromptBuilder() : promptBuilder;
        this.retryDelay = retryDelay == null ? DEFAULT_RETRY_DELAY : retryDelay;
        this.analysisJsonMapper = new AiAnalysisJsonMapper(this.objectMapper);
        this.mockAnalysisBuilder = new MockAiAnalysisBuilder(this.objectMapper);
    }

    public AiAnalysisResult analyze(SourceCodeDetail sourceCodeDetail) {
        Objects.requireNonNull(sourceCodeDetail, "sourceCodeDetail must not be null");

        if (aiConfig.useMockMode()) {
            LOGGER.info("{} analyzer is running in mock mode. Reason: explicit mock mode or missing API key.",
                    providerName());
            return mapAnalysisJson(mockAnalysisBuilder.build(sourceCodeDetail), sourceCodeDetail, mockModelName());
        }

        String requestBody = buildRequestBody(sourceCodeDetail);
        HttpRequest request = buildHttpRequest(requestBody);

        HttpResponse<String> response = sendWithRetry(request);
        String analysisJson = extractAnalysisJson(response.body());
        return mapAnalysisJson(analysisJson, sourceCodeDetail, aiConfig.model());
    }

    private HttpRequest buildHttpRequest(String requestBody) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(aiConfig.endpoint())
                .timeout(aiConfig.timeout())
                .header("Content-Type", "application/json");
        if (aiConfig.isGeminiProvider()) {
            builder.header("x-goog-api-key", aiConfig.apiKey());
        } else {
            builder.header("Authorization", "Bearer " + aiConfig.apiKey());
        }
        return builder.POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8)).build();
    }

    private String buildRequestBody(SourceCodeDetail sourceCodeDetail) {
        if (aiConfig.isGeminiProvider()) {
            return buildGeminiRequestBody(sourceCodeDetail);
        }
        return buildOpenAiRequestBody(sourceCodeDetail);
    }

    private String buildOpenAiRequestBody(SourceCodeDetail sourceCodeDetail) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", aiConfig.model());
            root.put("temperature", 0.1);

            ArrayNode input = root.putArray("input");
            input.add(message("system", promptBuilder.systemPrompt()));
            input.add(message("user", promptBuilder.userPrompt(sourceCodeDetail)));

            ObjectNode text = root.putObject("text");
            ObjectNode format = text.putObject("format");
            format.put("type", "json_schema");
            format.put("name", "ai_code_analysis_result");
            format.put("strict", true);
            format.set("schema", objectMapper.readTree(promptBuilder.jsonSchema()));

            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new AnalyzerException("Cannot build OpenAI analysis request JSON.", ex);
        }
    }

    private String buildGeminiRequestBody(SourceCodeDetail sourceCodeDetail) {
        try {
            ObjectNode root = objectMapper.createObjectNode();

            ObjectNode systemInstruction = root.putObject("systemInstruction");
            ArrayNode systemParts = systemInstruction.putArray("parts");
            systemParts.addObject().put("text", promptBuilder.systemPrompt());

            ArrayNode contents = root.putArray("contents");
            ObjectNode userContent = contents.addObject();
            userContent.put("role", "user");
            ArrayNode userParts = userContent.putArray("parts");
            userParts.addObject().put("text", promptBuilder.userPrompt(sourceCodeDetail));

            ObjectNode generationConfig = root.putObject("generationConfig");
            generationConfig.put("temperature", 0.1);
            generationConfig.put("responseMimeType", "application/json");

            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new AnalyzerException("Cannot build Gemini analysis request JSON.", ex);
        }
    }

    private ObjectNode message(String role, String text) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", role);
        ArrayNode content = message.putArray("content");
        ObjectNode inputText = content.addObject();
        inputText.put("type", "input_text");
        inputText.put("text", text);
        return message;
    }

    private HttpResponse<String> sendWithRetry(HttpRequest request) {
        for (int attempt = 1; attempt <= aiConfig.maxRetries(); attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (isSuccessful(response.statusCode())) {
                    return response;
                }
                if (!isRetryableStatus(response.statusCode()) || attempt == aiConfig.maxRetries()) {
                    throw new AnalyzerException(
                            providerName() + " API returned HTTP " + response.statusCode()
                                    + ". Body: " + truncate(response.body(), 400)
                    );
                }
                LOGGER.warn("{} API returned retryable HTTP {} on attempt {}.",
                        providerName(), response.statusCode(), attempt);
            } catch (IOException ex) {
                if (attempt == aiConfig.maxRetries()) {
                    throw new AnalyzerException("Cannot call " + providerName()
                            + " API after retrying network errors.", ex);
                }
                LOGGER.warn("Network error while calling {} API on attempt {}: {}",
                        providerName(), attempt, ex.toString());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AnalyzerException(providerName() + " API request was interrupted.", ex);
            }

            sleepBeforeRetry(attempt);
        }

        throw new AnalyzerException("Cannot call " + providerName() + " API because retry attempts were exhausted.");
    }

    private boolean isSuccessful(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 408
                || statusCode == 429
                || statusCode == 500
                || statusCode == 502
                || statusCode == 503
                || statusCode == 504;
    }

    private void sleepBeforeRetry(int attempt) {
        if (retryDelay.isZero() || retryDelay.isNegative()) {
            return;
        }

        try {
            Thread.sleep(retryDelay.toMillis() * attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AnalyzerException(providerName() + " retry delay was interrupted.", ex);
        }
    }

    private String extractAnalysisJson(String responseBody) {
        if (aiConfig.isGeminiProvider()) {
            return extractGeminiAnalysisJson(responseBody);
        }
        return extractOpenAiAnalysisJson(responseBody);
    }

    private String extractOpenAiAnalysisJson(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.hasNonNull("error")) {
                throw new AnalyzerException("OpenAI API returned an error: " + truncate(root.get("error").toString(), 400));
            }

            if (isAnalysisJson(root)) {
                return root.toString();
            }

            JsonNode outputTextNode = root.get("output_text");
            if (outputTextNode != null && outputTextNode.isTextual()) {
                return cleanJsonText(outputTextNode.asText());
            }

            JsonNode output = root.get("output");
            if (output != null && output.isArray()) {
                for (JsonNode outputItem : output) {
                    JsonNode content = outputItem.get("content");
                    if (content == null || !content.isArray()) {
                        continue;
                    }
                    for (JsonNode contentItem : content) {
                        String type = contentItem.path("type").asText("");
                        if ("refusal".equals(type)) {
                            throw new AnalyzerException(
                                    "OpenAI refused the analysis request: "
                                            + truncate(contentItem.path("refusal").asText("No refusal text."), 400)
                            );
                        }
                        if ("output_text".equals(type) && contentItem.hasNonNull("text")) {
                            return cleanJsonText(contentItem.get("text").asText());
                        }
                    }
                }
            }
        } catch (JsonProcessingException ex) {
            throw new AnalyzerException("OpenAI API response is not valid JSON.", ex);
        }

        throw new AnalyzerException("OpenAI API response does not contain structured analysis JSON.");
    }

    private String extractGeminiAnalysisJson(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.hasNonNull("error")) {
                throw new AnalyzerException("Gemini API returned an error: " + truncate(root.get("error").toString(), 400));
            }

            if (isAnalysisJson(root)) {
                return root.toString();
            }

            StringBuilder textBuilder = new StringBuilder();
            JsonNode candidates = root.get("candidates");
            if (candidates != null && candidates.isArray()) {
                for (JsonNode candidate : candidates) {
                    JsonNode parts = candidate.path("content").path("parts");
                    if (parts == null || !parts.isArray()) {
                        continue;
                    }
                    for (JsonNode part : parts) {
                        if (part.hasNonNull("text")) {
                            textBuilder.append(part.get("text").asText());
                        }
                    }
                }
            }

            String text = textBuilder.toString();
            if (SecretUtils.hasText(text)) {
                return cleanJsonText(text);
            }

            JsonNode promptFeedback = root.get("promptFeedback");
            if (promptFeedback != null && !promptFeedback.isMissingNode() && !promptFeedback.isNull()) {
                throw new AnalyzerException("Gemini API did not return analysis content. Prompt feedback: "
                        + truncate(promptFeedback.toString(), 400));
            }
        } catch (JsonProcessingException ex) {
            throw new AnalyzerException("Gemini API response is not valid JSON.", ex);
        }

        throw new AnalyzerException("Gemini API response does not contain structured analysis JSON.");
    }

    private AiAnalysisResult mapAnalysisJson(
            String analysisJson,
            SourceCodeDetail sourceCodeDetail,
            String modelName
    ) {
        return analysisJsonMapper.map(analysisJson, sourceCodeDetail, modelName, analyzerType(), ANALYZER_VERSION);
    }

    private boolean isAnalysisJson(JsonNode root) {
        return root != null
                && root.isObject()
                && root.has("language")
                && root.has("algorithms")
                && root.has("data_structures")
                && root.has("explanation_vi");
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
        return cleaned;
    }

    private String mockModelName() {
        return "mock-" + aiConfig.model();
    }

    private String analyzerType() {
        if (aiConfig.isGeminiProvider()) {
            return "GEMINI";
        }
        return ANALYZER_TYPE;
    }

    private String providerName() {
        if (aiConfig.isGeminiProvider()) {
            return "Gemini";
        }
        if (aiConfig.isOpenAiProvider()) {
            return "OpenAI";
        }
        return aiConfig.provider();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
