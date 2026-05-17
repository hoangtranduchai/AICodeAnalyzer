package com.example.aicodeanalyzer.service;

import com.example.aicodeanalyzer.model.SourceCodeDetail;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Builds deterministic local analysis JSON when no remote AI provider should be called.
 */
class MockAiAnalysisBuilder {
    private final ObjectMapper objectMapper;

    MockAiAnalysisBuilder(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    String build(SourceCodeDetail sourceCodeDetail) {
        String code = sourceCodeDetail.codeContent() == null ? "" : sourceCodeDetail.codeContent();
        String normalized = code.toLowerCase(Locale.ROOT);
        boolean shortCode = code.strip().length() < 80;

        ObjectNode root = objectMapper.createObjectNode();
        root.put("language", inferLanguage(sourceCodeDetail.language(), normalized));
        root.set("algorithms", textArray(detectAlgorithms(normalized)));
        root.set("data_structures", textArray(detectDataStructures(normalized)));
        root.put("complexity_time", normalized.contains("sort(") ? "O(n log n)" : normalized.contains("for") ? "O(n)" : "unknown");
        root.put("complexity_space", normalized.contains("vector") || normalized.contains("arraylist") ? "O(n)" : "O(1)");
        root.put("problem_solving_level", shortCode ? "beginner" : "intermediate");
        root.put("code_quality_score", shortCode ? 45 : 72);
        root.put("algorithm_score", shortCode ? 35 : 68);
        root.put("ds_score", shortCode ? 30 : 64);
        root.put("ai_generated_probability", 15);
        root.set("ai_usage_evidence", textArray(List.of("no_clear_evidence")));
        root.put(
                "explanation_vi",
                "Ket qua mock mode: he thong chua co API key AI nen chi tao phan tich gia lap de test luong ung dung. "
                        + "Khong nen dung ket qua nay lam danh gia chinh thuc."
        );
        ArrayNode warnings = root.putArray("warnings");
        warnings.add("Dang chay mock mode vi thieu API key hoac ai.mock-mode=true.");
        if (shortCode) {
            warnings.add("Source code qua ngan nen confidence duoc giam.");
        }
        root.put("confidence", shortCode ? 35 : 55);
        root.put("mock", true);

        return root.toString();
    }

    private List<String> detectAlgorithms(String normalizedCode) {
        List<String> algorithms = new ArrayList<>();
        if (normalizedCode.contains("sort(") || normalizedCode.contains(".sort(")) {
            algorithms.add("sorting");
        }
        if (normalizedCode.contains("lower_bound") || normalizedCode.contains("binary_search")) {
            algorithms.add("binary_search");
        }
        if (normalizedCode.contains("dfs")) {
            algorithms.add("dfs");
        }
        if (normalizedCode.contains("bfs")) {
            algorithms.add("bfs");
        }
        if (algorithms.isEmpty()) {
            algorithms.add("unknown");
        }
        return algorithms;
    }

    private List<String> detectDataStructures(String normalizedCode) {
        List<String> dataStructures = new ArrayList<>();
        if (normalizedCode.contains("vector")) {
            dataStructures.add("vector");
        }
        if (normalizedCode.contains("arraylist") || normalizedCode.contains("[]")) {
            dataStructures.add("array");
        }
        if (normalizedCode.contains("map") || normalizedCode.contains("dict")) {
            dataStructures.add("map");
        }
        if (normalizedCode.contains("queue")) {
            dataStructures.add("queue");
        }
        if (dataStructures.isEmpty()) {
            dataStructures.add("unknown");
        }
        return dataStructures;
    }

    private ArrayNode textArray(List<String> values) {
        ArrayNode array = objectMapper.createArrayNode();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private String inferLanguage(String declaredLanguage, String normalizedCode) {
        String declared = declaredLanguage == null ? "" : declaredLanguage.toLowerCase(Locale.ROOT);
        if (declared.contains("c++") || declared.contains("cpp") || normalizedCode.contains("#include")) {
            return "cpp";
        }
        if (declared.contains("java") || normalizedCode.contains("public class")) {
            return "java";
        }
        if (declared.contains("python") || normalizedCode.contains("def ")) {
            return "python";
        }
        return "unknown";
    }
}
