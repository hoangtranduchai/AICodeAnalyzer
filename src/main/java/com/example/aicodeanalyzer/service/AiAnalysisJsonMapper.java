package com.example.aicodeanalyzer.service;

import com.example.aicodeanalyzer.exception.AnalyzerException;
import com.example.aicodeanalyzer.model.AiAnalysisResult;
import com.example.aicodeanalyzer.model.SourceCodeDetail;
import com.example.aicodeanalyzer.util.SecretUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Converts provider structured analysis JSON into the application analysis model.
 */
class AiAnalysisJsonMapper {
    private final ObjectMapper objectMapper;

    AiAnalysisJsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    AiAnalysisResult map(
            String analysisJson,
            SourceCodeDetail sourceCodeDetail,
            String modelName,
            String analyzerType,
            String analyzerVersion
    ) {
        try {
            JsonNode root = objectMapper.readTree(cleanJsonText(analysisJson));
            requireAnalysisJson(root);

            int codeQualityScore = requiredScore(root, "code_quality_score");
            int aiProbability = requiredScore(root, "ai_generated_probability");
            String complexityEstimate = "time=%s; space=%s; level=%s; algorithm_score=%d; ds_score=%d; confidence=%d"
                    .formatted(
                            requiredText(root, "complexity_time"),
                            requiredText(root, "complexity_space"),
                            requiredText(root, "problem_solving_level"),
                            requiredScore(root, "algorithm_score"),
                            requiredScore(root, "ds_score"),
                            requiredScore(root, "confidence")
                    );

            return new AiAnalysisResult(
                    null,
                    sourceCodeDetail.submissionId(),
                    analyzerType,
                    analyzerVersion,
                    modelName,
                    joinTextArray(root.get("data_structures")),
                    joinTextArray(root.get("algorithms")),
                    complexityEstimate,
                    BigDecimal.valueOf(codeQualityScore),
                    BigDecimal.valueOf(aiProbability),
                    riskLevel(aiProbability),
                    requiredText(root, "explanation_vi"),
                    root.toString(),
                    promptHash(sourceCodeDetail, root, analyzerVersion),
                    null,
                    null
            );
        } catch (JsonProcessingException ex) {
            throw new AnalyzerException("Cannot parse AI analysis JSON.", ex);
        }
    }

    private void requireAnalysisJson(JsonNode root) {
        requiredText(root, "language");
        requireArray(root, "algorithms");
        requireArray(root, "data_structures");
        requiredText(root, "complexity_time");
        requiredText(root, "complexity_space");
        requiredText(root, "problem_solving_level");
        requiredScore(root, "code_quality_score");
        requiredScore(root, "algorithm_score");
        requiredScore(root, "ds_score");
        requiredScore(root, "ai_generated_probability");
        requireArray(root, "ai_usage_evidence");
        requiredText(root, "explanation_vi");
        requireArray(root, "warnings");
        requiredScore(root, "confidence");
    }

    private String requiredText(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.isTextual() || !SecretUtils.hasText(value.asText())) {
            throw new AnalyzerException("AI analysis JSON is missing text field: " + fieldName + ".");
        }
        return value.asText().trim();
    }

    private int requiredScore(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.canConvertToInt()) {
            throw new AnalyzerException("AI analysis JSON is missing numeric field: " + fieldName + ".");
        }
        int score = value.asInt();
        if (score < 0 || score > 100) {
            throw new AnalyzerException("AI analysis JSON field " + fieldName + " must be from 0 to 100.");
        }
        return score;
    }

    private void requireArray(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.isArray()) {
            throw new AnalyzerException("AI analysis JSON is missing array field: " + fieldName + ".");
        }
    }

    private String joinTextArray(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray() || arrayNode.isEmpty()) {
            return "-";
        }

        StringJoiner joiner = new StringJoiner(", ");
        for (JsonNode item : arrayNode) {
            joiner.add(item.asText("unknown"));
        }
        return joiner.toString();
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

    private String riskLevel(int aiProbability) {
        if (aiProbability >= 70) {
            return "HIGH";
        }
        if (aiProbability >= 40) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String promptHash(SourceCodeDetail sourceCodeDetail, JsonNode analysisRoot, String analyzerVersion) {
        String value = (sourceCodeDetail.codeContent() == null ? "" : sourceCodeDetail.codeContent())
                + "|"
                + analysisRoot.path("language").asText("")
                + "|"
                + analyzerVersion;
        return sha256(value);
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
}
