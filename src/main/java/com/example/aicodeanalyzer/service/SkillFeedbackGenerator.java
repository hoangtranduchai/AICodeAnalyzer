package com.example.aicodeanalyzer.service;

import com.example.aicodeanalyzer.model.SkillScore;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Generates neutral educational feedback from per-handle skill scores.
 */
public class SkillFeedbackGenerator {
    public static final String AI_REVIEW_PHRASE = "có dấu hiệu cần kiểm chứng thêm";

    public SkillFeedback generate(SkillScore score) {
        return generate(score, 0, 0, 0);
    }

    public SkillFeedback generate(
            SkillScore score,
            int submissionCount,
            int acceptedCount,
            int analysisCount
    ) {
        Objects.requireNonNull(score, "score must not be null");

        double overall = value(score.getOverallScore());
        String level = classify(overall);
        ScoreTemplate template = templateFor(overall);
        List<String> strengths = strengths(score, template);
        List<String> improvementAreas = improvementAreas(score, template, submissionCount, acceptedCount, analysisCount);
        List<String> roadmap = roadmap(score, template);
        String aiRiskComment = aiRiskComment(value(score.getAiUsageRiskScore()));
        String fullText = buildFullText(score, level, template, strengths, improvementAreas, roadmap, aiRiskComment);

        return new SkillFeedback(level, strengths, improvementAreas, roadmap, aiRiskComment, fullText);
    }

    public String generateSummary(
            double dataStructureScore,
            double algorithmScore,
            double problemSolvingScore,
            double codeQualityScore,
            double consistencyScore,
            double aiUsageRiskScore,
            double overallScore,
            int submissionCount,
            int acceptedCount,
            int analysisCount
    ) {
        SkillScore score = new SkillScore();
        score.setDataStructureScore(decimal(dataStructureScore));
        score.setAlgorithmScore(decimal(algorithmScore));
        score.setProblemSolvingScore(decimal(problemSolvingScore));
        score.setCodeQualityScore(decimal(codeQualityScore));
        score.setPracticeConsistencyScore(decimal(consistencyScore));
        score.setAiUsageRiskScore(decimal(aiUsageRiskScore));
        score.setOverallScore(decimal(overallScore));
        return generate(score, submissionCount, acceptedCount, analysisCount).fullText();
    }

    public String classify(double score) {
        if (score >= 85) {
            return "Rất tốt";
        }
        if (score >= 70) {
            return "Tốt";
        }
        if (score >= 55) {
            return "Khá";
        }
        if (score >= 40) {
            return "Trung bình";
        }
        return "Yếu";
    }

    private ScoreTemplate templateFor(double score) {
        if (score >= 85) {
            return new ScoreTemplate(
                    "Năng lực tổng thể nổi bật, thể hiện khả năng xử lý bài toán có độ khó cao và duy trì chất lượng ổn định.",
                    "Tiếp tục mở rộng chiều sâu thuật toán và luyện giải thích lời giải ở mức hệ thống.",
                    "Ưu tiên bài nâng cao, bài tổng hợp nhiều kỹ thuật, review lại lời giải để chuẩn hóa tư duy trình bày."
            );
        }
        if (score >= 70) {
            return new ScoreTemplate(
                    "Năng lực tổng thể tốt, có nền tảng thuật toán và cấu trúc dữ liệu khá chắc.",
                    "Có thể tăng thêm độ ổn định ở các nhóm bài khó hoặc các chủ đề ít xuất hiện.",
                    "Duy trì luyện tập đều, bổ sung dynamic programming, graph nâng cao và bài có ràng buộc chặt."
            );
        }
        if (score >= 55) {
            return new ScoreTemplate(
                    "Năng lực ở mức khá, đã có khả năng vận dụng một số kỹ thuật quen thuộc vào bài thi đấu.",
                    "Cần củng cố thêm độ bao phủ thuật toán, chất lượng code và tính ổn định khi gặp bài mới.",
                    "Tập trung upsolve, luyện theo chủ đề theo tuần và ghi chú lại mẫu sai thường gặp."
            );
        }
        if (score >= 40) {
            return new ScoreTemplate(
                    "Năng lực đang ở mức nền tảng, đã có dữ liệu thể hiện quá trình luyện tập ban đầu.",
                    "Cần xây chắc các kỹ thuật cơ bản trước khi mở rộng sang nhóm bài khó hơn.",
                    "Ưu tiên bài dễ đến trung bình: implementation, sorting, binary search, greedy cơ bản và DFS/BFS đơn giản."
            );
        }
        return new ScoreTemplate(
                "Năng lực hiện ở giai đoạn khởi đầu hoặc dữ liệu đánh giá còn hạn chế.",
                "Cần bổ sung thêm submission accepted và source code đã phân tích để đánh giá ổn định hơn.",
                "Bắt đầu với bài cơ bản, viết lại lời giải sau khi accepted và duy trì lịch luyện tập ngắn nhưng đều."
        );
    }

    private List<String> strengths(SkillScore score, ScoreTemplate template) {
        List<ComponentScore> components = componentScores(score).stream()
                .filter(component -> component.value() >= 65)
                .sorted(Comparator.comparingDouble(ComponentScore::value).reversed())
                .limit(3)
                .toList();

        List<String> strengths = new ArrayList<>();
        strengths.add(template.strengthTemplate());
        for (ComponentScore component : components) {
            strengths.add(switch (component.name()) {
                case "ds" -> "Điểm cấu trúc dữ liệu tương đối tích cực, cho thấy khả năng nhận diện và sử dụng container phù hợp.";
                case "algorithm" -> "Điểm thuật toán tốt, phản ánh khả năng vận dụng các kỹ thuật giải bài quen thuộc.";
                case "problem" -> "Điểm giải quyết vấn đề nổi bật, đặc biệt ở tỉ lệ accepted, độ khó hoặc tiến bộ theo thời gian.";
                case "quality" -> "Chất lượng code có tín hiệu tốt về tính rõ ràng, cấu trúc và khả năng đọc lại.";
                case "consistency" -> "Độ ổn định luyện tập tốt, phù hợp cho đánh giá năng lực dài hạn.";
                default -> "Có tín hiệu tích cực trong dữ liệu đánh giá.";
            });
        }
        return strengths;
    }

    private List<String> improvementAreas(
            SkillScore score,
            ScoreTemplate template,
            int submissionCount,
            int acceptedCount,
            int analysisCount
    ) {
        List<String> areas = new ArrayList<>();
        areas.add(template.improvementTemplate());

        for (ComponentScore component : componentScores(score).stream()
                .filter(item -> item.value() < 55)
                .sorted(Comparator.comparingDouble(ComponentScore::value))
                .limit(3)
                .toList()) {
            areas.add(switch (component.name()) {
                case "ds" -> "Nên tăng bài có map, set, stack, queue, priority_queue và graph để cải thiện độ bao phủ cấu trúc dữ liệu.";
                case "algorithm" -> "Nên luyện lại sorting, binary search, greedy, DFS/BFS và dynamic programming cơ bản theo từng nhóm.";
                case "problem" -> "Nên tăng số bài accepted và duy trì upsolve để cải thiện năng lực giải quyết vấn đề.";
                case "quality" -> "Nên chú ý đặt tên biến vừa đủ rõ, tách hàm khi lời giải dài và giữ format nhất quán.";
                case "consistency" -> "Nên duy trì lịch luyện tập đều theo tuần để dữ liệu thể hiện tiến bộ rõ hơn.";
                default -> "Nên bổ sung thêm dữ liệu để đánh giá ổn định hơn.";
            });
        }

        if (submissionCount > 0 && submissionCount < 10) {
            areas.add("Dữ liệu submission còn ít nên điểm cần được xem như tham khảo.");
        }
        if (acceptedCount >= 0 && acceptedCount < 5) {
            areas.add("Số bài accepted chưa đủ lớn để đánh giá ổn định.");
        }
        if (analysisCount >= 0 && analysisCount < 5) {
            areas.add("Số source code đã phân tích còn ít, các điểm DS/thuật toán/code quality có confidence thấp.");
        }
        return areas;
    }

    private List<String> roadmap(SkillScore score, ScoreTemplate template) {
        List<String> roadmap = new ArrayList<>();
        roadmap.add(template.roadmapTemplate());

        double overall = value(score.getOverallScore());
        if (overall < 40) {
            roadmap.add("Giai đoạn 1: hoàn thành bài implementation, array/vector, string và sorting cơ bản.");
            roadmap.add("Giai đoạn 2: luyện binary search, greedy đơn giản và DFS/BFS trên graph nhỏ.");
            roadmap.add("Giai đoạn 3: sau mỗi bài, ghi lại ý tưởng, độ phức tạp và lỗi gặp phải.");
        } else if (overall < 55) {
            roadmap.add("Giai đoạn 1: tăng tỉ lệ accepted bằng bài 800-1200 hoặc mức tương đương.");
            roadmap.add("Giai đoạn 2: luyện map/set/queue/stack kèm bài greedy và binary search.");
            roadmap.add("Giai đoạn 3: upsolve các bài chưa accepted trong vòng 24-48 giờ.");
        } else if (overall < 70) {
            roadmap.add("Giai đoạn 1: luyện theo chuyên đề 1-2 tuần cho graph, DP cơ bản và data structures.");
            roadmap.add("Giai đoạn 2: so sánh nhiều lời giải để rút gọn code và cải thiện complexity.");
            roadmap.add("Giai đoạn 3: chọn bài cao hơn mức hiện tại khoảng 100-200 rating để tăng dần độ khó.");
        } else if (overall < 85) {
            roadmap.add("Giai đoạn 1: bổ sung bài phối hợp nhiều kỹ thuật như graph + greedy hoặc DP + binary search.");
            roadmap.add("Giai đoạn 2: review lại code accepted để giảm lỗi biên và cải thiện cấu trúc hàm.");
            roadmap.add("Giai đoạn 3: luyện contest có giới hạn thời gian để giữ độ ổn định.");
        } else {
            roadmap.add("Giai đoạn 1: tập trung bài nâng cao, bài hiếm gặp và bài yêu cầu chứng minh chặt.");
            roadmap.add("Giai đoạn 2: viết editorial ngắn cho các bài khó để kiểm tra độ sâu hiểu thuật toán.");
            roadmap.add("Giai đoạn 3: duy trì benchmark theo thời gian để theo dõi phong độ và độ ổn định.");
        }
        return roadmap;
    }

    private String aiRiskComment(double aiUsageRiskScore) {
        if (aiUsageRiskScore >= 60) {
            return "AI usage risk: " + AI_REVIEW_PHRASE
                    + "; kết quả này chỉ nên dùng để hỗ trợ trao đổi, phỏng vấn kỹ thuật hoặc review thủ công.";
        }
        if (aiUsageRiskScore >= 35) {
            return "AI usage risk: có một số tín hiệu ở mức vừa phải, " + AI_REVIEW_PHRASE
                    + " nếu dùng trong bối cảnh đánh giá chính thức.";
        }
        return "AI usage risk: chưa có tín hiệu nổi bật; mọi kết quả vẫn chỉ mang tính tham khảo và không phải kết luận chắc chắn.";
    }

    private String buildFullText(
            SkillScore score,
            String level,
            ScoreTemplate template,
            List<String> strengths,
            List<String> improvementAreas,
            List<String> roadmap,
            String aiRiskComment
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("Phân loại: ").append(level).append(". ");
        builder.append("Overall Skill Score đạt ").append(format(value(score.getOverallScore()))).append("/100. ");
        builder.append("DS ").append(format(value(score.getDataStructureScore())))
                .append(", thuật toán ").append(format(value(score.getAlgorithmScore())))
                .append(", giải quyết vấn đề ").append(format(value(score.getProblemSolvingScore())))
                .append(", chất lượng code ").append(format(value(score.getCodeQualityScore())))
                .append(", độ ổn định ").append(format(value(score.getPracticeConsistencyScore())))
                .append(". ");
        builder.append(template.strengthTemplate()).append(" ");
        builder.append("Điểm mạnh: ").append(join(strengths)).append(" ");
        builder.append("Điểm cần cải thiện: ").append(join(improvementAreas)).append(" ");
        builder.append("Lộ trình gợi ý: ").append(join(roadmap)).append(" ");
        builder.append(aiRiskComment).append(" ");
        builder.append("AI Usage Risk Score ").append(format(value(score.getAiUsageRiskScore())))
                .append("/100, chỉ là xác suất/dấu hiệu tham khảo, không phải kết luận chắc chắn.");
        return builder.toString().trim();
    }

    private List<ComponentScore> componentScores(SkillScore score) {
        return List.of(
                new ComponentScore("ds", value(score.getDataStructureScore())),
                new ComponentScore("algorithm", value(score.getAlgorithmScore())),
                new ComponentScore("problem", value(score.getProblemSolvingScore())),
                new ComponentScore("quality", value(score.getCodeQualityScore())),
                new ComponentScore("consistency", value(score.getPracticeConsistencyScore()))
        );
    }

    private String join(List<String> values) {
        StringJoiner joiner = new StringJoiner(" ");
        values.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .forEach(value -> joiner.add(value.trim()));
        return joiner.toString();
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(clamp(value)).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String format(double value) {
        return decimal(value).toPlainString();
    }

    private double value(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }

    public record SkillFeedback(
            String level,
            List<String> strengths,
            List<String> improvementAreas,
            List<String> roadmap,
            String aiRiskComment,
            String fullText
    ) {
        public SkillFeedback {
            strengths = strengths == null ? List.of() : List.copyOf(strengths);
            improvementAreas = improvementAreas == null ? List.of() : List.copyOf(improvementAreas);
            roadmap = roadmap == null ? List.of() : List.copyOf(roadmap);
        }
    }

    private record ScoreTemplate(String strengthTemplate, String improvementTemplate, String roadmapTemplate) {
    }

    private record ComponentScore(String name, double value) {
    }
}
