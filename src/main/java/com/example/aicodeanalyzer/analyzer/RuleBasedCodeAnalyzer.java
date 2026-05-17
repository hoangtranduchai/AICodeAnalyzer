package com.example.aicodeanalyzer.analyzer;

import com.example.aicodeanalyzer.model.AiAnalysisResult;
import com.example.aicodeanalyzer.model.SourceCodeDetail;
import com.example.aicodeanalyzer.model.Submission;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * Fallback analyzer that uses regex and deterministic heuristics when no API key is available.
 */
public class RuleBasedCodeAnalyzer implements CodeAnalyzer {
    private static final String VERSION = "1.1.0";
    private static final int MAX_HEURISTIC_CONFIDENCE = 62;

    private static final Pattern LOOP_PATTERN = Pattern.compile("\\b(for|while)\\s*\\(");
    private static final Pattern NESTED_LOOP_PATTERN = Pattern.compile(
            "(?s)\\b(for|while)\\s*\\([^)]*\\).*?\\{.*?\\b(for|while)\\s*\\("
    );
    private static final Pattern ARRAY_PATTERN = Pattern.compile(
            "(\\b(int|long|double|float|char|boolean|bool|string)\\s+\\w+\\s*\\[[^]]*])|(new\\s+\\w+\\s*\\[)|\\[\\s*]"
    );
    private static final Pattern VECTOR_PATTERN = Pattern.compile("\\b(vector\\s*<|ArrayList\\s*<|List\\s*<)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAP_PATTERN = Pattern.compile("\\b(unordered_map|map\\s*<|HashMap\\s*<|TreeMap\\s*<|dict\\s*\\()", Pattern.CASE_INSENSITIVE);
    private static final Pattern SET_PATTERN = Pattern.compile("\\b(unordered_set|set\\s*<|HashSet\\s*<|TreeSet\\s*<|set\\s*\\()", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUEUE_PATTERN = Pattern.compile("\\b(queue\\s*<|Queue\\s*<|ArrayDeque\\s*<|Deque\\s*<|collections\\.deque)", Pattern.CASE_INSENSITIVE);
    private static final Pattern STACK_PATTERN = Pattern.compile("\\b(stack\\s*<|Stack\\s*<|Deque\\s*<|push\\s*\\(|pop\\s*\\()", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRIORITY_QUEUE_PATTERN = Pattern.compile("\\b(priority_queue\\s*<|PriorityQueue\\s*<|heapq\\.)", Pattern.CASE_INSENSITIVE);

    private static final Pattern SORTING_PATTERN = Pattern.compile("\\b(sort\\s*\\(|Arrays\\.sort\\s*\\(|Collections\\.sort\\s*\\(|\\.sort\\s*\\()", Pattern.CASE_INSENSITIVE);
    private static final Pattern BINARY_SEARCH_PATTERN = Pattern.compile(
            "\\b(binary_search|lower_bound|upper_bound|Arrays\\.binarySearch|Collections\\.binarySearch)\\b|while\\s*\\(\\s*(l|left|lo)\\s*<=?\\s*(r|right|hi)"
    );
    private static final Pattern DFS_PATTERN = Pattern.compile("\\b(dfs\\s*\\(|depthFirst|depth_first)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BFS_PATTERN = Pattern.compile("\\b(bfs\\s*\\(|breadthFirst|breadth_first)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DP_PATTERN = Pattern.compile("\\b(dp\\s*\\[|memo\\s*\\[|memoization|dynamic\\s+programming|cache\\s*=)", Pattern.CASE_INSENSITIVE);
    private static final Pattern GRAPH_PATTERN = Pattern.compile("\\b(adj\\s*\\[|adjacency|edges\\b|graph\\b|List\\s*<\\s*List|vector\\s*<\\s*vector)", Pattern.CASE_INSENSITIVE);
    private static final Pattern GREEDY_PATTERN = Pattern.compile("\\b(greedy|Comparator\\s*<|compare\\s*\\(|priority_queue\\s*<|PriorityQueue\\s*<|max\\s*\\(|min\\s*\\()", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;
    private final AIDetectionHeuristics aiDetectionHeuristics;

    public RuleBasedCodeAnalyzer() {
        this(new ObjectMapper(), new AIDetectionHeuristics());
    }

    public RuleBasedCodeAnalyzer(ObjectMapper objectMapper) {
        this(objectMapper, new AIDetectionHeuristics());
    }

    public RuleBasedCodeAnalyzer(ObjectMapper objectMapper, AIDetectionHeuristics aiDetectionHeuristics) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.aiDetectionHeuristics = aiDetectionHeuristics == null ? new AIDetectionHeuristics() : aiDetectionHeuristics;
    }

    @Override
    public AiAnalysisResult analyze(SourceCodeDetail sourceCodeDetail) {
        return analyzeWithHistory(sourceCodeDetail, List.of(), List.of());
    }

    public AiAnalysisResult analyzeWithHistory(
            SourceCodeDetail sourceCodeDetail,
            List<SourceCodeDetail> sourceHistory,
            List<Submission> submissionHistory
    ) {
        Objects.requireNonNull(sourceCodeDetail, "sourceCodeDetail must not be null");

        String code = sourceCodeDetail.codeContent() == null ? "" : sourceCodeDetail.codeContent();

        Set<String> dataStructures = detectDataStructures(code);
        Set<String> algorithms = detectAlgorithms(code);
        String complexity = estimateComplexity(code, algorithms);
        int lineCount = sourceCodeDetail.lineCount() == null ? countLines(code) : sourceCodeDetail.lineCount();
        int codeQualityScore = estimateQuality(code, lineCount);
        int algorithmScore = estimateAlgorithmScore(algorithms, complexity);
        int dataStructureScore = estimateDataStructureScore(dataStructures);
        int confidence = estimateConfidence(code, lineCount, dataStructures, algorithms);
        AIDetectionResult aiDetectionResult = aiDetectionHeuristics.evaluate(
                sourceCodeDetail,
                sourceHistory,
                submissionHistory,
                codeQualityScore,
                algorithmScore,
                dataStructureScore
        );
        int aiRiskScore = aiDetectionResult.aiGeneratedProbability();
        confidence = Math.min(confidence, aiDetectionResult.confidence());
        String problemLevel = estimateProblemLevel(algorithmScore, dataStructureScore, complexity);
        Set<String> warnings = buildWarnings(code, confidence);
        warnings.addAll(aiDetectionResult.warnings());

        String rawResponse = buildRawResponse(
                sourceCodeDetail,
                dataStructures,
                algorithms,
                complexity,
                problemLevel,
                codeQualityScore,
                algorithmScore,
                dataStructureScore,
                aiRiskScore,
                aiDetectionResult.evidence(),
                warnings,
                confidence
        );

        return new AiAnalysisResult(
                null,
                sourceCodeDetail.submissionId(),
                "RULE_BASED",
                VERSION,
                "local-regex-heuristic",
                join(dataStructures),
                join(algorithms),
                "%s; level=%s; algorithm_score=%d; ds_score=%d; confidence=%d"
                        .formatted(complexity, problemLevel, algorithmScore, dataStructureScore, confidence),
                score(codeQualityScore),
                score(aiRiskScore),
                riskLevel(aiRiskScore),
                buildSummary(dataStructures, algorithms, codeQualityScore, aiRiskScore, confidence),
                rawResponse,
                sha256(code + "|" + VERSION),
                null,
                null
        );
    }

    private Set<String> detectDataStructures(String code) {
        Set<String> result = new LinkedHashSet<>();
        addIfMatches(result, "array", ARRAY_PATTERN, code);
        addIfMatches(result, "vector", VECTOR_PATTERN, code);
        addIfMatches(result, "map", MAP_PATTERN, code);
        addIfMatches(result, "set", SET_PATTERN, code);
        addIfMatches(result, "queue", QUEUE_PATTERN, code);
        addIfMatches(result, "stack", STACK_PATTERN, code);
        addIfMatches(result, "priority_queue", PRIORITY_QUEUE_PATTERN, code);

        if (GRAPH_PATTERN.matcher(code).find()) {
            result.add("graph");
        }
        return result;
    }

    private Set<String> detectAlgorithms(String code) {
        Set<String> result = new LinkedHashSet<>();
        addIfMatches(result, "sorting", SORTING_PATTERN, code);
        addIfMatches(result, "binary_search", BINARY_SEARCH_PATTERN, code);
        addIfMatches(result, "dfs", DFS_PATTERN, code);
        addIfMatches(result, "bfs", BFS_PATTERN, code);
        addIfMatches(result, "dynamic_programming", DP_PATTERN, code);
        addIfMatches(result, "graph", GRAPH_PATTERN, code);
        addIfMatches(result, "greedy", GREEDY_PATTERN, code);

        return result;
    }

    private void addIfMatches(Set<String> result, String label, Pattern pattern, String code) {
        if (pattern.matcher(code).find()) {
            result.add(label);
        }
    }

    private int estimateQuality(String code, int lineCount) {
        if (code.isBlank()) {
            return 0;
        }

        long loopCount = LOOP_PATTERN.matcher(code).results().count();
        long longLines = code.lines().filter(line -> line.length() > 140).count();
        long commentLines = code.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("//") || line.startsWith("#") || line.startsWith("/*"))
                .count();

        int score = 52;
        if (lineCount >= 8 && lineCount <= 180) {
            score += 12;
        }
        if (code.contains("\n    ") || code.contains("\n\t")) {
            score += 8;
        }
        if (loopCount > 0) {
            score += 6;
        }
        if (hasTextLikeFunction(code)) {
            score += 6;
        }
        if (commentLines > lineCount / 2 && lineCount > 8) {
            score -= 8;
        }
        if (longLines > 2) {
            score -= 12;
        }
        if (lineCount > 400) {
            score -= 10;
        }
        if (lineCount < 5) {
            score -= 15;
        }
        return clamp(score);
    }

    private int estimateAlgorithmScore(Set<String> algorithms, String complexity) {
        int score = 20 + algorithms.size() * 12;
        if (algorithms.contains("dynamic_programming")) {
            score += 14;
        }
        if (algorithms.contains("dfs") || algorithms.contains("bfs") || algorithms.contains("graph")) {
            score += 10;
        }
        if (algorithms.contains("binary_search")) {
            score += 8;
        }
        if (complexity.contains("O(n^2)") || complexity.contains("O(V + E)") || complexity.contains("O(n log n)")) {
            score += 6;
        }
        return clamp(score);
    }

    private int estimateDataStructureScore(Set<String> dataStructures) {
        int score = 18 + dataStructures.size() * 10;
        if (dataStructures.contains("priority_queue")) {
            score += 10;
        }
        if (dataStructures.contains("graph")) {
            score += 12;
        }
        if (dataStructures.contains("map") || dataStructures.contains("set")) {
            score += 6;
        }
        return clamp(score);
    }

    private int estimateConfidence(String code, int lineCount, Set<String> dataStructures, Set<String> algorithms) {
        if (code.isBlank()) {
            return 8;
        }

        int confidence = 26;
        confidence += Math.min(14, Math.max(0, lineCount / 8));
        confidence += Math.min(12, dataStructures.size() * 3);
        confidence += Math.min(16, algorithms.size() * 4);
        if (lineCount < 5 || code.strip().length() < 80) {
            confidence -= 16;
        }
        return Math.min(MAX_HEURISTIC_CONFIDENCE, clamp(confidence));
    }

    private String estimateComplexity(String code, Set<String> algorithms) {
        if (algorithms.contains("dynamic_programming")) {
            return "time=heuristic unknown; space=O(n) possible";
        }
        if (algorithms.contains("dfs") || algorithms.contains("bfs") || algorithms.contains("graph")) {
            return "time=O(V + E) possible; space=O(V + E) possible";
        }
        if (algorithms.contains("sorting")) {
            return "time=O(n log n) possible; space=O(n) possible";
        }
        if (algorithms.contains("binary_search")) {
            return "time=O(log n) possible; space=O(1) possible";
        }
        if (NESTED_LOOP_PATTERN.matcher(code).find()) {
            return "time=O(n^2) possible; space=heuristic unknown";
        }
        if (LOOP_PATTERN.matcher(code).find()) {
            return "time=O(n) possible; space=heuristic unknown";
        }
        return "time=unknown; space=unknown";
    }

    private String estimateProblemLevel(int algorithmScore, int dataStructureScore, String complexity) {
        int combined = (algorithmScore + dataStructureScore) / 2;
        if (combined >= 75 || complexity.contains("O(V + E)") || complexity.contains("dynamic")) {
            return "advanced";
        }
        if (combined >= 45) {
            return "intermediate";
        }
        return "beginner";
    }

    private Set<String> buildWarnings(String code, int confidence) {
        Set<String> warnings = new LinkedHashSet<>();
        warnings.add("This is a regex/heuristic fallback result, not a full AI analysis.");
        warnings.add("AI-use probability is only a weak signal and must not be treated as a conclusion.");
        if (code.isBlank()) {
            warnings.add("Source code is empty.");
        } else if (code.strip().length() < 80) {
            warnings.add("Source code is too short; confidence was reduced.");
        }
        if (confidence < 45) {
            warnings.add("Low confidence because heuristic evidence is limited.");
        }
        return warnings;
    }

    private String buildSummary(
            Set<String> dataStructures,
            Set<String> algorithms,
            int qualityScore,
            int aiRiskScore,
            int confidence
    ) {
        String structures = dataStructures.isEmpty() ? "no strong data-structure signal" : join(dataStructures);
        String algorithmText = algorithms.isEmpty() ? "no strong algorithm signal" : join(algorithms);
        return "Heuristic fallback analysis detected " + structures
                + " and " + algorithmText
                + ". This result is based on regex/heuristics, so confidence is intentionally lower than AI analysis: "
                + confidence
                + "/100. Quality score: " + qualityScore
                + "/100. AI-use probability signal: " + aiRiskScore
                + "/100, not a conclusion.";
    }

    private String buildRawResponse(
            SourceCodeDetail sourceCodeDetail,
            Set<String> dataStructures,
            Set<String> algorithms,
            String complexity,
            String problemLevel,
            int qualityScore,
            int algorithmScore,
            int dataStructureScore,
            int aiRiskScore,
            List<String> aiEvidence,
            Set<String> warnings,
            int confidence
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("analyzer", "rule_based_regex_heuristic");
        root.put("language", normalizeLanguage(sourceCodeDetail.language(), sourceCodeDetail.codeContent()));
        root.set("algorithms", toArray(algorithms));
        root.set("data_structures", toArray(dataStructures));
        root.put("complexity_estimate", complexity);
        root.put("problem_solving_level", problemLevel);
        root.put("code_quality_score", qualityScore);
        root.put("algorithm_score", algorithmScore);
        root.put("ds_score", dataStructureScore);
        root.put("ai_generated_probability", aiRiskScore);
        root.put("ai_usage_note", "Probability only; heuristic evidence is not a definitive conclusion.");
        root.set("ai_usage_evidence", toArray(aiEvidence));
        root.put(
                "explanation_vi",
                "Ket qua nay duoc tao bang regex/heuristic fallback khi khong co API key. "
                        + "He thong chi phat hien dau hieu trong source code, khong ket luan chac chan ve nang luc hoac viec su dung AI."
        );
        root.set("warnings", toArray(warnings));
        root.put("confidence", confidence);

        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            return "{\"analyzer\":\"rule_based_regex_heuristic\",\"confidence\":" + confidence + "}";
        }
    }

    private ArrayNode toArray(Set<String> values) {
        ArrayNode array = objectMapper.createArrayNode();
        if (values.isEmpty()) {
            array.add("unknown");
            return array;
        }
        values.forEach(array::add);
        return array;
    }

    private ArrayNode toArray(List<String> values) {
        ArrayNode array = objectMapper.createArrayNode();
        if (values == null || values.isEmpty()) {
            array.add("no_clear_evidence");
            return array;
        }
        values.forEach(array::add);
        return array;
    }

    private String normalizeLanguage(String declaredLanguage, String code) {
        String declared = declaredLanguage == null ? "" : declaredLanguage.toLowerCase(Locale.ROOT);
        String normalizedCode = code == null ? "" : code.toLowerCase(Locale.ROOT);
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

    private boolean hasTextLikeFunction(String code) {
        return Pattern.compile("\\b(\\w+\\s+)?\\w+\\s*\\([^)]*\\)\\s*\\{").matcher(code).find()
                || Pattern.compile("\\bdef\\s+\\w+\\s*\\(").matcher(code).find();
    }

    private String join(Set<String> values) {
        if (values.isEmpty()) {
            return "-";
        }

        StringJoiner joiner = new StringJoiner(", ");
        values.forEach(joiner::add);
        return joiner.toString();
    }

    private BigDecimal score(int value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private int countLines(String code) {
        if (code == null || code.isEmpty()) {
            return 0;
        }
        return code.split("\\R", -1).length;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private String riskLevel(int score) {
        if (score >= 70) {
            return "HIGH";
        }
        if (score >= 40) {
            return "MEDIUM";
        }
        return "LOW";
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
