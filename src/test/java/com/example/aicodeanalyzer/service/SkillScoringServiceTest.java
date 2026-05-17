package com.example.aicodeanalyzer.service;

import com.example.aicodeanalyzer.model.AiAnalysisResult;
import com.example.aicodeanalyzer.model.SkillScore;
import com.example.aicodeanalyzer.model.Submission;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillScoringServiceTest {
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 5, 13, 10, 0);
    private final SkillScoringService service = SkillScoringService.calculationOnly();

    @Test
    void calculateScoreRatesStrongHandleAsGoodOrBetter() {
        SkillScore score = service.calculateScore(1L, strongAnalyses(20, 20), strongSubmissions(20));

        assertTrue(value(score.getOverallScore()) >= 70);
        assertTrue(value(score.getDataStructureScore()) >= 70);
        assertTrue(value(score.getAlgorithmScore()) >= 70);
        assertTrue(value(score.getCodeQualityScore()) >= 80);
        assertTrue(score.getSummary().contains("Phân loại: Tốt") || score.getSummary().contains("Phân loại: Rất tốt"));
        assertTrue(score.getSummary().contains("không phải kết luận chắc chắn"));
    }

    @Test
    void calculateScoreAddsWarningsAndPenaltyWhenDataIsTooSmall() {
        List<Submission> submissions = List.of(
                submission(1, 0, "OK", 900, "implementation"),
                submission(2, 1, "WRONG_ANSWER", 1000, "math")
        );
        List<AiAnalysisResult> analyses = List.of(analysis(1, "array", "sorting", "time=O(n log n); algorithm_score=40; ds_score=35", 55, 10));

        SkillScore score = service.calculateScore(2L, analyses, submissions);

        assertTrue(value(score.getOverallScore()) < 55);
        assertTrue(score.getSummary().contains("Dữ liệu submission còn ít"));
        assertTrue(score.getSummary().contains("Số bài accepted chưa đủ lớn"));
        assertTrue(score.getSummary().contains("Số source code đã phân tích còn ít"));
    }

    @Test
    void calculateScoreAppliesSoftPenaltyForHighAiRisk() {
        List<Submission> submissions = strongSubmissions(18);
        SkillScore lowRisk = service.calculateScore(3L, strongAnalyses(18, 15), submissions);
        SkillScore highRisk = service.calculateScore(3L, strongAnalyses(18, 92), submissions);

        assertTrue(value(highRisk.getAiUsageRiskScore()) >= 80);
        assertTrue(value(lowRisk.getAiUsageRiskScore()) < 40);
        assertTrue(value(highRisk.getOverallScore()) < value(lowRisk.getOverallScore()));
        assertTrue(value(lowRisk.getOverallScore()) - value(highRisk.getOverallScore()) >= 5);
    }

    @Test
    void calculateScoreHandlesMissingAnalysisResults() {
        SkillScore score = service.calculateScore(4L, List.of(), strongSubmissions(12));

        assertEquals(0.0, value(score.getDataStructureScore()));
        assertEquals(0.0, value(score.getAlgorithmScore()));
        assertEquals(0.0, value(score.getCodeQualityScore()));
        assertEquals(0.0, value(score.getAiUsageRiskScore()));
        assertTrue(value(score.getProblemSolvingScore()) > 35);
        assertTrue(score.getSummary().contains("Số source code đã phân tích còn ít"));
    }

    @Test
    void calculateScoreHandlesCompletelyEmptyInput() {
        SkillScore score = service.calculateScore(7L, null, null);

        assertEquals(0.0, value(score.getDataStructureScore()));
        assertEquals(0.0, value(score.getAlgorithmScore()));
        assertEquals(0.0, value(score.getProblemSolvingScore()));
        assertEquals(0.0, value(score.getCodeQualityScore()));
        assertEquals(0.0, value(score.getPracticeConsistencyScore()));
        assertEquals(0.0, value(score.getAiUsageRiskScore()));
        assertEquals(0.0, value(score.getOverallScore()));
        assertTrue(score.getSummary().contains("Phân loại: Yếu"));
    }

    @Test
    void calculationOnlyServiceRejectsCalculateAndSave() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.calculateAndSave(1L)
        );

        assertTrue(exception.getMessage().contains("calculation only"));
    }

    @Test
    void calculateScoreRewardsProgressOverTime() {
        List<Submission> improving = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            improving.add(submission(100 + i, i, "OK", 900 + i * 20, "implementation"));
        }
        for (int i = 0; i < 6; i++) {
            improving.add(submission(200 + i, 20 + i, "OK", 1700 + i * 80, "graphs,dp"));
        }

        List<Submission> stagnant = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            stagnant.add(submission(300 + i, i, "OK", 1100, "implementation"));
        }

        SkillScore improvingScore = service.calculateScore(5L, strongAnalyses(12, 20), improving);
        SkillScore stagnantScore = service.calculateScore(6L, strongAnalyses(12, 20), stagnant);

        assertTrue(value(improvingScore.getProblemSolvingScore()) > value(stagnantScore.getProblemSolvingScore()));
        assertTrue(value(improvingScore.getOverallScore()) > value(stagnantScore.getOverallScore()));
    }

    private static List<Submission> strongSubmissions(int count) {
        List<Submission> submissions = new ArrayList<>();
        String[] tags = {
                "graphs,dfs,bfs",
                "dp,math",
                "greedy,sorting",
                "data structures,trees",
                "binary search,two pointers"
        };
        for (int i = 0; i < count; i++) {
            String verdict = i % 5 == 0 ? "WRONG_ANSWER" : "OK";
            int rating = 1200 + (i % 8) * 150;
            submissions.add(submission(i + 1L, i, verdict, rating, tags[i % tags.length]));
        }
        return submissions;
    }

    private static List<AiAnalysisResult> strongAnalyses(int count, int aiRisk) {
        List<AiAnalysisResult> analyses = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            analyses.add(analysis(
                    i + 1L,
                    "array, vector, map, set, queue, stack, priority_queue, graph",
                    "sorting, binary_search, greedy, dfs, bfs, dynamic_programming, graph",
                    "time=O(V + E) possible; space=O(V + E) possible; algorithm_score=88; ds_score=84; confidence=80",
                    82 + (i % 5),
                    aiRisk
            ));
        }
        return analyses;
    }

    private static Submission submission(long id, int dayOffset, String verdict, int rating, String tags) {
        LocalDateTime submittedAt = BASE_TIME.plusDays(dayOffset);
        return new Submission(
                id,
                1L,
                String.valueOf(id),
                "P" + id,
                "Problem " + id,
                "1",
                "GNU C++17",
                verdict,
                submittedAt,
                46,
                1024L,
                rating,
                tags,
                null,
                submittedAt,
                submittedAt
        );
    }

    private static AiAnalysisResult analysis(
            long submissionId,
            String dataStructures,
            String algorithms,
            String complexity,
            int quality,
            int aiRisk
    ) {
        return new AiAnalysisResult(
                null,
                submissionId,
                "RULE_BASED",
                "test",
                "test-model",
                dataStructures,
                algorithms,
                complexity,
                BigDecimal.valueOf(quality),
                BigDecimal.valueOf(aiRisk),
                aiRisk >= 70 ? "HIGH" : aiRisk >= 40 ? "MEDIUM" : "LOW",
                "test summary",
                "{}",
                "hash",
                null,
                null
        );
    }

    private static double value(BigDecimal decimal) {
        return decimal == null ? 0 : decimal.doubleValue();
    }
}
