package com.example.aicodeanalyzer.service;

import com.example.aicodeanalyzer.model.AiAnalysisResult;
import com.example.aicodeanalyzer.model.SkillScore;
import com.example.aicodeanalyzer.model.Submission;
import com.example.aicodeanalyzer.repository.AiAnalysisResultRepository;
import com.example.aicodeanalyzer.repository.SkillScoreRepository;
import com.example.aicodeanalyzer.repository.SubmissionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aggregates submission metadata and analyzer outputs into per-handle skill scores.
 */
public class SkillScoringService {
    private static final int MIN_RELIABLE_SUBMISSIONS = 10;
    private static final int MIN_RELIABLE_ACCEPTED = 5;
    private static final int MIN_RELIABLE_ANALYSES = 5;

    private static final Pattern ALGORITHM_SCORE_PATTERN = Pattern.compile("algorithm_score=(\\d{1,3})");
    private static final Pattern DS_SCORE_PATTERN = Pattern.compile("ds_score=(\\d{1,3})");

    private final AiAnalysisResultRepository aiAnalysisResultRepository;
    private final SubmissionRepository submissionRepository;
    private final SkillScoreRepository skillScoreRepository;
    private final SkillFeedbackGenerator skillFeedbackGenerator = new SkillFeedbackGenerator();

    public SkillScoringService() {
        this(new AiAnalysisResultRepository(), new SubmissionRepository(), new SkillScoreRepository());
    }

    public static SkillScoringService calculationOnly() {
        return new SkillScoringService(null, null, null, false);
    }

    public SkillScoringService(
            AiAnalysisResultRepository aiAnalysisResultRepository,
            SubmissionRepository submissionRepository,
            SkillScoreRepository skillScoreRepository
    ) {
        this(aiAnalysisResultRepository, submissionRepository, skillScoreRepository, true);
    }

    private SkillScoringService(
            AiAnalysisResultRepository aiAnalysisResultRepository,
            SubmissionRepository submissionRepository,
            SkillScoreRepository skillScoreRepository,
            boolean repositoryBacked
    ) {
        if (!repositoryBacked) {
            this.aiAnalysisResultRepository = null;
            this.submissionRepository = null;
            this.skillScoreRepository = null;
            return;
        }
        this.aiAnalysisResultRepository = Objects.requireNonNull(
                aiAnalysisResultRepository,
                "aiAnalysisResultRepository must not be null"
        );
        this.submissionRepository = Objects.requireNonNull(submissionRepository, "submissionRepository must not be null");
        this.skillScoreRepository = Objects.requireNonNull(skillScoreRepository, "skillScoreRepository must not be null");
    }

    public SkillScore calculateAndSave(long handleId) {
        if (aiAnalysisResultRepository == null || submissionRepository == null || skillScoreRepository == null) {
            throw new IllegalStateException("This SkillScoringService instance was created for calculation only.");
        }
        SkillScore score = calculateScore(
                handleId,
                aiAnalysisResultRepository.findByHandleId(handleId),
                submissionRepository.findByHandleId(handleId)
        );
        return skillScoreRepository.saveOrUpdateByHandleAndPeriod(score);
    }

    public SkillScore calculateScore(
            long handleId,
            List<AiAnalysisResult> analysisResults,
            List<Submission> submissions
    ) {
        List<AiAnalysisResult> analyses = analysisResults == null ? List.of() : analysisResults;
        List<Submission> safeSubmissions = submissions == null ? List.of() : submissions;

        int submissionCount = safeSubmissions.size();
        int acceptedCount = (int) safeSubmissions.stream().filter(this::isAccepted).count();
        Set<Long> acceptedSubmissionIds = acceptedSubmissionIds(safeSubmissions);
        LocalDate periodStart = periodStart(safeSubmissions);
        LocalDate periodEnd = periodEnd(safeSubmissions);

        double dataStructureScore = dataStructureScore(analyses, acceptedSubmissionIds, safeSubmissions);
        double algorithmScore = algorithmScore(analyses, acceptedSubmissionIds, safeSubmissions);
        double problemSolvingScore = problemSolvingScore(safeSubmissions);
        double codeQualityScore = weightedAverage(analyses, this::codeQualityValue);
        double consistencyScore = consistencyScore(safeSubmissions, analyses);
        double aiUsageRiskScore = aiUsageRiskScore(analyses);

        double baseOverall = 0.18 * dataStructureScore
                + 0.22 * algorithmScore
                + 0.25 * problemSolvingScore
                + 0.15 * codeQualityScore
                + 0.15 * consistencyScore;
        double aiPenalty = Math.max(0, aiUsageRiskScore - 50) * 0.20;
        double dataPenalty = dataReliabilityPenalty(submissionCount, acceptedCount, analyses.size());
        double overallScore = clamp(baseOverall - aiPenalty - dataPenalty);

        SkillScore score = new SkillScore(
                null,
                handleId,
                periodStart,
                periodEnd,
                decimal(dataStructureScore),
                decimal(algorithmScore),
                decimal(problemSolvingScore),
                decimal(codeQualityScore),
                decimal(consistencyScore),
                decimal(aiUsageRiskScore),
                decimal(overallScore),
                buildVietnameseSummary(
                        submissionCount,
                        acceptedCount,
                        analyses.size(),
                        dataStructureScore,
                        algorithmScore,
                        problemSolvingScore,
                        codeQualityScore,
                        consistencyScore,
                        aiUsageRiskScore,
                        overallScore
                ),
                LocalDateTime.now(),
                null,
                null
        );

        return score;
    }

    private double dataStructureScore(
            List<AiAnalysisResult> analyses,
            Set<Long> acceptedSubmissionIds,
            List<Submission> submissions
    ) {
        if (analyses.isEmpty()) {
            return 0;
        }

        Set<String> structures = tokens(analyses, AiAnalysisResult::getDataStructures);
        double coverage = clamp(weightedStructureCoverage(structures));
        List<AiAnalysisResult> withSignals = analyses.stream()
                .filter(analysis -> !tokens(analysis.getDataStructures()).isEmpty())
                .toList();
        double success = withSignals.isEmpty()
                ? 0
                : withSignals.stream()
                .filter(analysis -> acceptedSubmissionIds.contains(analysis.getSubmissionId()))
                .count() * 100.0 / withSignals.size();
        double difficulty = acceptedDifficultyScore(submissions, acceptedSubmissionIds);
        double variety = clamp(structures.size() / 8.0 * 100);

        return clamp(0.40 * coverage + 0.30 * success + 0.20 * difficulty + 0.10 * variety);
    }

    private double algorithmScore(
            List<AiAnalysisResult> analyses,
            Set<Long> acceptedSubmissionIds,
            List<Submission> submissions
    ) {
        if (analyses.isEmpty()) {
            return 0;
        }

        Set<String> algorithms = tokens(analyses, AiAnalysisResult::getAlgorithms);
        double coverage = clamp(weightedAlgorithmCoverage(algorithms));
        List<AiAnalysisResult> withSignals = analyses.stream()
                .filter(analysis -> !tokens(analysis.getAlgorithms()).isEmpty())
                .toList();
        double success = withSignals.isEmpty()
                ? 0
                : withSignals.stream()
                .filter(analysis -> acceptedSubmissionIds.contains(analysis.getSubmissionId()))
                .count() * 100.0 / withSignals.size();
        double difficulty = acceptedDifficultyScore(submissions, acceptedSubmissionIds);
        double complexity = complexityScore(analyses);
        double embeddedAnalyzerScore = averageParsedScore(analyses, ALGORITHM_SCORE_PATTERN);

        double formulaScore = 0.35 * coverage + 0.30 * success + 0.25 * difficulty + 0.10 * complexity;
        return embeddedAnalyzerScore > 0 ? clamp(0.75 * formulaScore + 0.25 * embeddedAnalyzerScore) : clamp(formulaScore);
    }

    private double problemSolvingScore(List<Submission> submissions) {
        if (submissions.isEmpty()) {
            return 0;
        }

        int submissionCount = submissions.size();
        int acceptedCount = (int) submissions.stream().filter(this::isAccepted).count();
        double acceptedRate = acceptedCount * 100.0 / submissionCount;
        double difficulty = acceptedDifficultyScore(submissions, acceptedSubmissionIds(submissions));
        double acceptedVolume = clamp(Math.log10(acceptedCount + 1) / Math.log10(201) * 100);
        double topicVariety = topicVarietyScore(submissions);
        double progress = progressScore(submissions);

        return clamp(0.30 * acceptedRate
                + 0.25 * difficulty
                + 0.20 * acceptedVolume
                + 0.15 * topicVariety
                + 0.10 * progress);
    }

    private double consistencyScore(List<Submission> submissions, List<AiAnalysisResult> analyses) {
        if (submissions.isEmpty()) {
            return 0;
        }

        double activity = activityConsistencyScore(submissions);
        double acceptedConsistency = acceptedConsistencyScore(submissions);
        double difficultyStability = difficultyStabilityScore(submissions);
        double styleConsistency = styleConsistencyScore(analyses);
        double progressStability = progressStabilityScore(submissions);

        return clamp(0.30 * activity
                + 0.25 * acceptedConsistency
                + 0.20 * difficultyStability
                + 0.15 * styleConsistency
                + 0.10 * progressStability);
    }

    private double aiUsageRiskScore(List<AiAnalysisResult> analyses) {
        if (analyses.isEmpty()) {
            return 0;
        }
        double average = analyses.stream()
                .map(AiAnalysisResult::getAiRiskScore)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0);
        double max = analyses.stream()
                .map(AiAnalysisResult::getAiRiskScore)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .max()
                .orElse(0);
        return clamp(0.70 * average + 0.30 * max);
    }

    private double weightedStructureCoverage(Set<String> structures) {
        double weight = 0;
        for (String structure : structures) {
            if (containsAny(structure, "priority_queue", "heap", "graph", "tree", "disjoint", "segment", "fenwick")) {
                weight += 1.7;
            } else if (containsAny(structure, "map", "set", "queue", "stack")) {
                weight += 1.2;
            } else if (containsAny(structure, "array", "vector", "list", "string")) {
                weight += 0.8;
            } else {
                weight += 0.6;
            }
        }
        return weight / 10.0 * 100;
    }

    private double weightedAlgorithmCoverage(Set<String> algorithms) {
        double weight = 0;
        for (String algorithm : algorithms) {
            if (containsAny(algorithm, "dynamic", "dijkstra", "floyd", "union", "bitmask", "backtracking")) {
                weight += 1.8;
            } else if (containsAny(algorithm, "dfs", "bfs", "graph", "greedy", "binary")) {
                weight += 1.3;
            } else if (containsAny(algorithm, "sorting", "prefix", "two_pointers", "sliding")) {
                weight += 0.9;
            } else {
                weight += 0.6;
            }
        }
        return weight / 11.0 * 100;
    }

    private double acceptedDifficultyScore(List<Submission> submissions, Set<Long> acceptedSubmissionIds) {
        double averageRating = submissions.stream()
                .filter(submission -> submission.getSubmissionId() != null)
                .filter(submission -> acceptedSubmissionIds.contains(submission.getSubmissionId()))
                .map(Submission::getProblemRating)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);
        if (averageRating <= 0) {
            return acceptedSubmissionIds.isEmpty() ? 0 : 45;
        }
        return clamp((averageRating - 800) / (2400 - 800) * 100);
    }

    private double complexityScore(List<AiAnalysisResult> analyses) {
        if (analyses.isEmpty()) {
            return 0;
        }
        double total = analyses.stream()
                .map(AiAnalysisResult::getComplexityEstimate)
                .mapToDouble(this::complexityValue)
                .sum();
        return clamp(total / analyses.size());
    }

    private double complexityValue(String complexity) {
        String value = complexity == null ? "" : complexity.toLowerCase(Locale.ROOT);
        if (containsAny(value, "o(v + e)", "o(e log v)", "dynamic", "log n) per")) {
            return 80;
        }
        if (containsAny(value, "o(n log n)", "o(log n)")) {
            return 68;
        }
        if (containsAny(value, "o(n^2)", "o(n)")) {
            return 52;
        }
        if (containsAny(value, "unknown")) {
            return 30;
        }
        return 45;
    }

    private double topicVarietyScore(List<Submission> submissions) {
        Set<String> tags = new LinkedHashSet<>();
        for (Submission submission : submissions) {
            tags.addAll(tokens(submission.getProblemTags()));
        }
        return clamp(tags.size() / 12.0 * 100);
    }

    private double progressScore(List<Submission> submissions) {
        List<Submission> sorted = sortedByTime(submissions);
        if (sorted.size() < 4) {
            return 35;
        }

        int middle = sorted.size() / 2;
        List<Submission> early = sorted.subList(0, middle);
        List<Submission> recent = sorted.subList(middle, sorted.size());
        double earlyRating = averageAcceptedRating(early);
        double recentRating = averageAcceptedRating(recent);
        double earlyAcceptedRate = acceptedRate(early);
        double recentAcceptedRate = acceptedRate(recent);

        double ratingGain = earlyRating <= 0 || recentRating <= 0 ? 0 : (recentRating - earlyRating) / 600 * 60;
        double acceptedGain = (recentAcceptedRate - earlyAcceptedRate) * 0.40;
        return clamp(45 + ratingGain + acceptedGain);
    }

    private double activityConsistencyScore(List<Submission> submissions) {
        List<Submission> dated = submissions.stream()
                .filter(submission -> submission.getSubmittedAt() != null)
                .toList();
        if (dated.size() < 2) {
            return 25;
        }

        LocalDate first = dated.stream().map(submission -> submission.getSubmittedAt().toLocalDate()).min(LocalDate::compareTo).orElse(LocalDate.now());
        LocalDate last = dated.stream().map(submission -> submission.getSubmittedAt().toLocalDate()).max(LocalDate::compareTo).orElse(first);
        long weeks = Math.max(1, ChronoUnit.WEEKS.between(first, last) + 1);
        Set<String> activeWeeks = new LinkedHashSet<>();
        for (Submission submission : dated) {
            long weekIndex = ChronoUnit.WEEKS.between(first, submission.getSubmittedAt().toLocalDate());
            activeWeeks.add(String.valueOf(weekIndex));
        }
        return clamp(activeWeeks.size() * 100.0 / weeks);
    }

    private double acceptedConsistencyScore(List<Submission> submissions) {
        double rate = acceptedRate(submissions);
        if (submissions.size() < 6) {
            return Math.min(60, rate);
        }
        return clamp(100 - Math.abs(70 - rate));
    }

    private double difficultyStabilityScore(List<Submission> submissions) {
        List<Integer> ratings = submissions.stream()
                .filter(this::isAccepted)
                .map(Submission::getProblemRating)
                .filter(Objects::nonNull)
                .toList();
        if (ratings.size() < 3) {
            return 45;
        }

        double average = ratings.stream().mapToDouble(Integer::doubleValue).average().orElse(0);
        double variance = ratings.stream()
                .mapToDouble(rating -> Math.pow(rating - average, 2))
                .average()
                .orElse(0);
        double stdDev = Math.sqrt(variance);
        return clamp(100 - stdDev / 8);
    }

    private double styleConsistencyScore(List<AiAnalysisResult> analyses) {
        if (analyses.size() < 3) {
            return 45;
        }
        double avgQuality = weightedAverage(analyses, this::codeQualityValue);
        double avgRisk = analyses.stream()
                .map(AiAnalysisResult::getAiRiskScore)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0);
        return clamp(avgQuality - Math.max(0, avgRisk - 50) * 0.3);
    }

    private double progressStabilityScore(List<Submission> submissions) {
        double progress = progressScore(submissions);
        return progress >= 45 ? Math.min(100, progress + 10) : progress;
    }

    private double averageParsedScore(List<AiAnalysisResult> analyses, Pattern pattern) {
        return analyses.stream()
                .map(AiAnalysisResult::getComplexityEstimate)
                .mapToDouble(value -> parsedScore(value, pattern))
                .filter(value -> value >= 0)
                .average()
                .orElse(0);
    }

    private double parsedScore(String value, Pattern pattern) {
        if (value == null) {
            return -1;
        }
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? clamp(Integer.parseInt(matcher.group(1))) : -1;
    }

    private Set<String> tokens(List<AiAnalysisResult> analyses, java.util.function.Function<AiAnalysisResult, String> getter) {
        Set<String> result = new LinkedHashSet<>();
        for (AiAnalysisResult analysis : analyses) {
            result.addAll(tokens(getter.apply(analysis)));
        }
        return result;
    }

    private Set<String> tokens(String text) {
        if (text == null || text.trim().isEmpty() || "-".equals(text.trim())) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        Arrays.stream(text.split("[,;/|]"))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .filter(value -> !"unknown".equals(value))
                .forEach(values::add);
        return values;
    }

    private Set<Long> acceptedSubmissionIds(List<Submission> submissions) {
        Set<Long> ids = new LinkedHashSet<>();
        submissions.stream()
                .filter(this::isAccepted)
                .map(Submission::getSubmissionId)
                .filter(Objects::nonNull)
                .forEach(ids::add);
        return ids;
    }

    private boolean isAccepted(Submission submission) {
        return submission != null && "OK".equalsIgnoreCase(submission.getVerdict());
    }

    private double codeQualityValue(AiAnalysisResult analysis) {
        return analysis.getCodeQualityScore() == null ? 0 : analysis.getCodeQualityScore().doubleValue();
    }

    private double weightedAverage(List<AiAnalysisResult> analyses, ToDoubleFunction<AiAnalysisResult> valueGetter) {
        if (analyses.isEmpty()) {
            return 0;
        }

        double weightedTotal = 0;
        double weightTotal = 0;
        for (int i = 0; i < analyses.size(); i++) {
            double weight = 1.0 + i * 0.05;
            weightedTotal += valueGetter.applyAsDouble(analyses.get(i)) * weight;
            weightTotal += weight;
        }
        return clamp(weightedTotal / weightTotal);
    }

    private double dataReliabilityPenalty(int submissionCount, int acceptedCount, int analysisCount) {
        double penalty = 0;
        if (submissionCount < MIN_RELIABLE_SUBMISSIONS) {
            penalty += 8;
        }
        if (acceptedCount < MIN_RELIABLE_ACCEPTED) {
            penalty += 6;
        }
        if (analysisCount < MIN_RELIABLE_ANALYSES) {
            penalty += 6;
        }
        return penalty;
    }

    private String buildVietnameseSummary(
            int submissionCount,
            int acceptedCount,
            int analysisCount,
            double dataStructureScore,
            double algorithmScore,
            double problemSolvingScore,
            double codeQualityScore,
            double consistencyScore,
            double aiUsageRiskScore,
            double overallScore
    ) {
        return skillFeedbackGenerator.generateSummary(
                dataStructureScore,
                algorithmScore,
                problemSolvingScore,
                codeQualityScore,
                consistencyScore,
                aiUsageRiskScore,
                overallScore,
                submissionCount,
                acceptedCount,
                analysisCount
        );
    }

    public String classify(double score) {
        return skillFeedbackGenerator.classify(score);
    }

    private LocalDate periodStart(List<Submission> submissions) {
        return submissions.stream()
                .map(Submission::getSubmittedAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .map(LocalDateTime::toLocalDate)
                .orElse(LocalDate.now());
    }

    private LocalDate periodEnd(List<Submission> submissions) {
        return submissions.stream()
                .map(Submission::getSubmittedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .map(LocalDateTime::toLocalDate)
                .orElse(LocalDate.now());
    }

    private List<Submission> sortedByTime(List<Submission> submissions) {
        return submissions.stream()
                .filter(submission -> submission.getSubmittedAt() != null)
                .sorted(Comparator.comparing(Submission::getSubmittedAt))
                .toList();
    }

    private double acceptedRate(List<Submission> submissions) {
        if (submissions.isEmpty()) {
            return 0;
        }
        return submissions.stream().filter(this::isAccepted).count() * 100.0 / submissions.size();
    }

    private double averageAcceptedRating(List<Submission> submissions) {
        return submissions.stream()
                .filter(this::isAccepted)
                .map(Submission::getProblemRating)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(clamp(value)).setScale(2, RoundingMode.HALF_UP);
    }

    private String format(double value) {
        return decimal(value).toPlainString();
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }
}
