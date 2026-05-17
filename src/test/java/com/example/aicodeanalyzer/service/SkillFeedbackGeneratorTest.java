package com.example.aicodeanalyzer.service;

import com.example.aicodeanalyzer.model.SkillScore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillFeedbackGeneratorTest {
    private final SkillFeedbackGenerator generator = new SkillFeedbackGenerator();

    @Test
    void generateUsesVeryGoodTemplate() {
        SkillFeedbackGenerator.SkillFeedback feedback = generator.generate(score(90, 88, 92, 91, 86, 88, 18), 30, 24, 20);

        assertEquals("Rất tốt", feedback.level());
        assertTrue(feedback.fullText().contains("Năng lực tổng thể nổi bật"));
        assertTrue(feedback.fullText().contains("Điểm mạnh:"));
        assertTrue(feedback.fullText().contains("Lộ trình gợi ý:"));
    }

    @Test
    void generateUsesGoodTemplate() {
        SkillFeedbackGenerator.SkillFeedback feedback = generator.generate(score(76, 75, 78, 74, 73, 70, 25), 20, 15, 15);

        assertEquals("Tốt", feedback.level());
        assertTrue(feedback.fullText().contains("Năng lực tổng thể tốt"));
        assertTrue(feedback.roadmap().stream().anyMatch(item -> item.contains("graph + greedy") || item.contains("DP + binary search")));
    }

    @Test
    void generateUsesFairTemplate() {
        SkillFeedbackGenerator.SkillFeedback feedback = generator.generate(score(62, 61, 63, 60, 58, 57, 28), 15, 9, 8);

        assertEquals("Khá", feedback.level());
        assertTrue(feedback.fullText().contains("Năng lực ở mức khá"));
        assertTrue(feedback.fullText().contains("upsolve"));
    }

    @Test
    void generateUsesAverageTemplate() {
        SkillFeedbackGenerator.SkillFeedback feedback = generator.generate(score(47, 45, 43, 48, 50, 46, 30), 12, 6, 6);

        assertEquals("Trung bình", feedback.level());
        assertTrue(feedback.fullText().contains("Năng lực đang ở mức nền tảng"));
        assertTrue(feedback.fullText().contains("DFS/BFS"));
    }

    @Test
    void generateUsesWeakTemplateAndDataWarnings() {
        SkillFeedbackGenerator.SkillFeedback feedback = generator.generate(score(30, 25, 28, 30, 32, 20, 20), 3, 1, 1);

        assertEquals("Yếu", feedback.level());
        assertTrue(feedback.fullText().contains("giai đoạn khởi đầu"));
        assertTrue(feedback.fullText().contains("Dữ liệu submission còn ít"));
        assertTrue(feedback.fullText().contains("Số bài accepted chưa đủ lớn"));
        assertTrue(feedback.fullText().contains("Số source code đã phân tích còn ít"));
    }

    @Test
    void generateUsesVerificationLanguageForAiRiskOnly() {
        SkillFeedbackGenerator.SkillFeedback feedback = generator.generate(score(72, 75, 76, 70, 72, 68, 82), 20, 14, 12);

        assertTrue(feedback.aiRiskComment().contains(SkillFeedbackGenerator.AI_REVIEW_PHRASE));
        assertTrue(feedback.fullText().contains("không phải kết luận chắc chắn"));
        assertFalse(feedback.fullText().contains("code này do AI viết"));
        assertFalse(feedback.fullText().contains("gian lận"));
        assertFalse(feedback.fullText().contains("buộc tội"));
    }

    private static SkillScore score(
            double overall,
            double ds,
            double algorithm,
            double problem,
            double quality,
            double consistency,
            double aiRisk
    ) {
        SkillScore score = new SkillScore();
        score.setOverallScore(decimal(overall));
        score.setDataStructureScore(decimal(ds));
        score.setAlgorithmScore(decimal(algorithm));
        score.setProblemSolvingScore(decimal(problem));
        score.setCodeQualityScore(decimal(quality));
        score.setPracticeConsistencyScore(decimal(consistency));
        score.setAiUsageRiskScore(decimal(aiRisk));
        return score;
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value);
    }
}
