package com.example.aicodeanalyzer.crawler;

import com.example.aicodeanalyzer.config.AiConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

final class GeminiImageOcrClient {
    private final AiConfig aiConfig;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    GeminiImageOcrClient() {
        this(AiConfig.load(), HttpClient.newHttpClient(), new ObjectMapper());
    }

    GeminiImageOcrClient(AiConfig aiConfig, HttpClient httpClient, ObjectMapper objectMapper) {
        this.aiConfig = aiConfig;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    SourceFetchResult extractSourceFromDataUrl(String dataUrl, Duration timeout) {
        if (dataUrl == null || !dataUrl.startsWith("data:") || !dataUrl.contains(";base64,")) {
            return SourceFetchResult.unavailable(
                    SourceAvailability.OCR_REQUIRED,
                    SourceOrigin.VJUDGE_AUTHORIZED_SNAPSHOT_OCR,
                    "VJudge source is an image, but the image could not be read from Chrome."
            );
        }
        if (!aiConfig.isGeminiProvider() || aiConfig.useMockMode()) {
            return SourceFetchResult.unavailable(
                    SourceAvailability.OCR_REQUIRED,
                    SourceOrigin.VJUDGE_AUTHORIZED_SNAPSHOT_OCR,
                    "VJudge source is an image. Configure GEMINI_API_KEY with Gemini provider to OCR it."
            );
        }

        int marker = dataUrl.indexOf(";base64,");
        String mimeType = dataUrl.substring("data:".length(), marker);
        String base64 = dataUrl.substring(marker + ";base64,".length());
        try {
            String body = buildRequestBody(mimeType, base64);
            HttpRequest request = HttpRequest.newBuilder(aiConfig.endpoint())
                    .timeout(timeout == null ? aiConfig.timeout() : timeout)
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", aiConfig.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return SourceFetchResult.unavailable(
                        SourceAvailability.OCR_FAILED,
                        SourceOrigin.VJUDGE_AUTHORIZED_SNAPSHOT_OCR,
                        "Gemini OCR returned HTTP " + response.statusCode() + "."
                );
            }
            String code = extractText(response.body());
            if (code == null || code.isBlank()) {
                return SourceFetchResult.unavailable(
                        SourceAvailability.OCR_FAILED,
                        SourceOrigin.VJUDGE_AUTHORIZED_SNAPSHOT_OCR,
                        "Gemini OCR did not return readable source code."
                );
            }
            return SourceFetchResult.available(SourceOrigin.VJUDGE_AUTHORIZED_SNAPSHOT_OCR, stripMarkdownFence(code));
        } catch (IOException ex) {
            return SourceFetchResult.unavailable(
                    SourceAvailability.OCR_FAILED,
                    SourceOrigin.VJUDGE_AUTHORIZED_SNAPSHOT_OCR,
                    "Gemini OCR request failed: " + ex.getMessage()
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return SourceFetchResult.unavailable(
                    SourceAvailability.OCR_FAILED,
                    SourceOrigin.VJUDGE_AUTHORIZED_SNAPSHOT_OCR,
                    "Gemini OCR request was interrupted."
            );
        }
    }

    private String buildRequestBody(String mimeType, String base64) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode contents = root.putArray("contents");
        ObjectNode content = contents.addObject();
        content.put("role", "user");
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", """
                Extract the programming source code from this screenshot.
                Return only the exact source code text. Do not use Markdown fences.
                Preserve indentation and line breaks as well as possible.
                """);
        ObjectNode imagePart = parts.addObject();
        ObjectNode inlineData = imagePart.putObject("inline_data");
        inlineData.put("mime_type", mimeType == null || mimeType.isBlank() ? "image/png" : mimeType);
        inlineData.put("data", base64);

        ObjectNode generationConfig = root.putObject("generationConfig");
        generationConfig.put("temperature", 0);
        return objectMapper.writeValueAsString(root);
    }

    private String extractText(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            return "";
        }
        return parts.path(0).path("text").asText("");
    }

    private String stripMarkdownFence(String text) {
        String normalized = text == null ? "" : text.strip();
        if (!normalized.startsWith("```")) {
            return normalized;
        }
        int firstLineEnd = normalized.indexOf('\n');
        int lastFence = normalized.lastIndexOf("```");
        if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
            return normalized.substring(firstLineEnd + 1, lastFence).strip();
        }
        return normalized.replace("```", "").strip();
    }
}
