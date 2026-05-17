package com.example.aicodeanalyzer.analyzer;

import com.example.aicodeanalyzer.model.SourceCodeDetail;
import com.example.aicodeanalyzer.model.Submission;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Estimates AI-use probability from source-code and historical submission signals.
 *
 * <p>This module intentionally returns probability and evidence only. It must not be used to claim that a
 * submission was written by AI.</p>
 */
public class AIDetectionHeuristics {
    public static final String ETHICAL_WARNING = "AI detection is probabilistic and must not be used as a definitive "
            + "authorship or misconduct conclusion. Always combine it with human review and user consent.";

    private static final Pattern COMMENT_PATTERN = Pattern.compile("(?m)^\\s*(//|#|/\\*|\\*)\\s*(.+)$");
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_]{8,}\\b");
    private static final Pattern STYLE_TOKEN_PATTERN = Pattern.compile(
            "\\b(vector|map|set|queue|stack|sort|ArrayList|HashMap|unordered_map|priority_queue|"
                    + "PriorityQueue|BufferedReader|Scanner|ios::sync_with_stdio|StringBuilder|Collections\\.sort|"
                    + "Arrays\\.sort|def|public static void main)\\b"
    );
    private static final Set<String> COMMON_CONTEST_IDENTIFIERS = Set.of(
            "include", "bits", "stdc", "using", "namespace", "public", "static", "void", "main",
            "return", "integer", "string", "vector", "arraylist", "hashmap", "scanner",
            "bufferedreader", "priorityqueue", "collections", "arrays"
    );

    public AIDetectionResult evaluate(
            SourceCodeDetail currentSource,
            List<SourceCodeDetail> sourceHistory,
            List<Submission> submissionHistory,
            int codeQualityScore,
            int algorithmScore,
            int dataStructureScore
    ) {
        Objects.requireNonNull(currentSource, "currentSource must not be null");

        String code = currentSource.codeContent() == null ? "" : currentSource.codeContent();
        List<SourceCodeDetail> safeSourceHistory = sourceHistory == null ? List.of() : sourceHistory;
        List<Submission> safeSubmissionHistory = submissionHistory == null ? List.of() : submissionHistory;

        List<String> evidence = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        warnings.add(ETHICAL_WARNING);

        int probability = 8;
        int confidence = 22;

        int regularCommentScore = scoreRegularComments(code);
        if (regularCommentScore >= 18) {
            probability += regularCommentScore;
            confidence += 8;
            evidence.add("comment_pattern_regular_or_template_like");
        }

        int descriptiveVariableScore = scoreDescriptiveVariableNames(code);
        if (descriptiveVariableScore >= 12) {
            probability += descriptiveVariableScore;
            confidence += 7;
            evidence.add("unusually_descriptive_variable_names_for_contest_code");
        }

        int abilityMismatchScore = scoreAbilityMismatch(
                currentSource,
                safeSubmissionHistory,
                codeQualityScore,
                algorithmScore,
                dataStructureScore
        );
        if (abilityMismatchScore >= 12) {
            probability += abilityMismatchScore;
            confidence += 10;
            evidence.add("current_code_quality_or_difficulty_may_not_match_submission_history");
        }

        int similarStyleScore = scoreSimilarStyleAcrossSolutions(code, safeSourceHistory);
        if (similarStyleScore >= 12) {
            probability += similarStyleScore;
            confidence += 10;
            evidence.add("multiple_solutions_have_unusually_similar_style");
        }

        int fastHardSubmissionScore = scoreFastHardSubmissions(currentSource, safeSubmissionHistory);
        if (fastHardSubmissionScore >= 12) {
            probability += fastHardSubmissionScore;
            confidence += 12;
            evidence.add("many_hard_submissions_in_short_time_window");
        }

        if (evidence.isEmpty()) {
            evidence.add("no_clear_evidence");
            probability = Math.min(probability, 20);
        }

        if (safeSubmissionHistory.size() < 5) {
            confidence -= 12;
            warnings.add("Historical submission data is limited, so probability confidence is reduced.");
        }
        if (safeSourceHistory.size() < 3) {
            confidence -= 8;
            warnings.add("Historical source-code style data is limited.");
        }
        if (code.strip().length() < 120) {
            confidence -= 10;
            warnings.add("Source code is short, so style-based AI detection is weak.");
        }

        return new AIDetectionResult(
                clamp(probability),
                evidence,
                warnings,
                clamp(confidence)
        );
    }

    private int scoreRegularComments(String code) {
        Matcher matcher = COMMENT_PATTERN.matcher(code);
        List<String> comments = new ArrayList<>();
        while (matcher.find()) {
            String text = matcher.group(2).trim().toLowerCase(Locale.ROOT);
            if (!text.isBlank()) {
                comments.add(text);
            }
        }

        if (comments.size() < 4) {
            return 0;
        }

        double averageLength = comments.stream().mapToInt(String::length).average().orElse(0);
        long closeToAverage = comments.stream()
                .filter(comment -> Math.abs(comment.length() - averageLength) <= 12)
                .count();
        long templateLike = comments.stream()
                .filter(comment -> comment.contains("step")
                        || comment.contains("approach")
                        || comment.contains("time complexity")
                        || comment.contains("space complexity")
                        || comment.contains("initialize")
                        || comment.contains("iterate"))
                .count();

        int score = 0;
        if (closeToAverage >= Math.ceil(comments.size() * 0.75)) {
            score += 12;
        }
        if (templateLike >= 2) {
            score += 12;
        }
        if (comments.size() >= 8) {
            score += 6;
        }
        return Math.min(28, score);
    }

    private int scoreDescriptiveVariableNames(String code) {
        Matcher matcher = IDENTIFIER_PATTERN.matcher(code);
        int longIdentifiers = 0;
        int descriptiveIdentifiers = 0;

        while (matcher.find()) {
            String identifier = matcher.group();
            String lowerIdentifier = identifier.toLowerCase(Locale.ROOT);
            if (COMMON_CONTEST_IDENTIFIERS.contains(lowerIdentifier)) {
                continue;
            }
            longIdentifiers++;
            if (identifier.contains("_") || identifier.matches(".*[a-z][A-Z].*")) {
                descriptiveIdentifiers++;
            }
        }

        if (longIdentifiers < 6) {
            return 0;
        }

        double ratio = descriptiveIdentifiers / (double) longIdentifiers;
        if (ratio >= 0.65) {
            return 18;
        }
        if (ratio >= 0.45) {
            return 12;
        }
        return 0;
    }

    private int scoreAbilityMismatch(
            SourceCodeDetail currentSource,
            List<Submission> submissionHistory,
            int codeQualityScore,
            int algorithmScore,
            int dataStructureScore
    ) {
        List<Submission> priorRatedSubmissions = submissionHistory.stream()
                .filter(submission -> !isCurrentSubmission(submission, currentSource))
                .filter(submission -> submission.getProblemRating() != null)
                .toList();

        if (priorRatedSubmissions.size() < 5) {
            return 0;
        }

        double averageRating = priorRatedSubmissions.stream()
                .mapToInt(Submission::getProblemRating)
                .average()
                .orElse(0);
        int maxPriorRating = priorRatedSubmissions.stream()
                .mapToInt(Submission::getProblemRating)
                .max()
                .orElse(0);

        Integer currentRating = submissionHistory.stream()
                .filter(submission -> isCurrentSubmission(submission, currentSource))
                .map(Submission::getProblemRating)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        int sophistication = (codeQualityScore + algorithmScore + dataStructureScore) / 3;
        if (currentRating != null && currentRating >= averageRating + 500 && currentRating >= maxPriorRating + 300) {
            return sophistication >= 75 ? 24 : 16;
        }
        if (averageRating > 0 && averageRating < 1100 && sophistication >= 82) {
            return 16;
        }
        return 0;
    }

    private int scoreSimilarStyleAcrossSolutions(String currentCode, List<SourceCodeDetail> sourceHistory) {
        if (sourceHistory.size() < 3 || currentCode.isBlank()) {
            return 0;
        }

        Set<String> currentTokens = styleTokens(currentCode);
        if (currentTokens.size() < 4) {
            return 0;
        }

        long similarCount = sourceHistory.stream()
                .filter(source -> source.codeContent() != null && !source.codeContent().equals(currentCode))
                .filter(source -> jaccard(currentTokens, styleTokens(source.codeContent())) >= 0.72)
                .count();

        if (similarCount >= 4) {
            return 22;
        }
        if (similarCount >= 2) {
            return 14;
        }
        return 0;
    }

    private int scoreFastHardSubmissions(SourceCodeDetail currentSource, List<Submission> submissionHistory) {
        if (currentSource.submittedAt() == null || submissionHistory.size() < 5) {
            return 0;
        }

        LocalDateTime start = currentSource.submittedAt().minusHours(2);
        LocalDateTime end = currentSource.submittedAt().plusHours(2);
        long hardSolvedInWindow = submissionHistory.stream()
                .filter(submission -> "OK".equalsIgnoreCase(submission.getVerdict()))
                .filter(submission -> submission.getProblemRating() != null && submission.getProblemRating() >= 1600)
                .filter(submission -> submission.getSubmittedAt() != null)
                .filter(submission -> !submission.getSubmittedAt().isBefore(start) && !submission.getSubmittedAt().isAfter(end))
                .count();

        if (hardSolvedInWindow >= 5) {
            return 26;
        }
        if (hardSolvedInWindow >= 3) {
            return 16;
        }

        long veryCloseAccepted = submissionHistory.stream()
                .filter(submission -> "OK".equalsIgnoreCase(submission.getVerdict()))
                .filter(submission -> submission.getSubmittedAt() != null)
                .filter(submission -> Math.abs(Duration.between(currentSource.submittedAt(), submission.getSubmittedAt()).toMinutes()) <= 20)
                .count();
        return veryCloseAccepted >= 4 ? 12 : 0;
    }

    private boolean isCurrentSubmission(Submission submission, SourceCodeDetail currentSource) {
        return submission.getSubmissionId() != null && submission.getSubmissionId().equals(currentSource.submissionId());
    }

    private Set<String> styleTokens(String code) {
        Set<String> tokens = new HashSet<>();
        Matcher matcher = STYLE_TOKEN_PATTERN.matcher(code);
        while (matcher.find()) {
            tokens.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        if (code.contains("\n    ")) {
            tokens.add("four_space_indent");
        }
        if (code.contains("\n\t")) {
            tokens.add("tab_indent");
        }
        if (code.contains("var ")) {
            tokens.add("var_keyword");
        }
        if (code.contains("auto ")) {
            tokens.add("auto_keyword");
        }
        return tokens;
    }

    private double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }

        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return intersection.size() / (double) union.size();
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
