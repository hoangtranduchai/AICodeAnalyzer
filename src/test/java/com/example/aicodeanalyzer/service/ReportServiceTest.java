package com.example.aicodeanalyzer.service;

import com.example.aicodeanalyzer.model.SkillScore;
import com.example.aicodeanalyzer.report.EvaluationReportData;
import com.example.aicodeanalyzer.report.HandleReportRow;
import com.example.aicodeanalyzer.report.PdfReportExporter;
import com.example.aicodeanalyzer.report.ReportExportResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void exportPdfWritesReportFileWithVietnameseContent() throws Exception {
        ReportService reportService = new ReportService(new PdfReportExporter());

        ReportExportResult result = reportService.exportPdf(reportData(), tempDir, false);

        assertTrue(Files.exists(result.filePath()));
        assertTrue(Files.size(result.filePath()) > 1024);
        assertTrue(result.filePath().getFileName().toString().endsWith(".pdf"));
        assertTrue(result.message().contains("Đã xuất báo cáo PDF"));
        assertFalse(result.opened());
    }

    private static EvaluationReportData reportData() {
        SkillScore adaScore = score(78.60, 78.50, 81.20, 76.40, 84.00, 72.30, 22.00,
                "Phân loại: Tốt. Điểm mạnh: nền tảng thuật toán ổn định. AI usage risk chưa có tín hiệu nổi bật.");
        SkillScore brunoScore = score(48.90, 45.20, 49.80, 46.10, 58.00, 41.50, 18.00,
                "Phân loại: Trung bình. Lộ trình gợi ý: luyện sorting, binary search và DFS/BFS cơ bản.");

        return new EvaluationReportData(
                "Báo cáo kết quả đánh giá nick lập trình",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 13),
                LocalDateTime.of(2026, 5, 13, 21, 30),
                List.of(
                        new HandleReportRow(
                                1L,
                                "Codeforces",
                                "cf_ada_demo",
                                42,
                                31,
                                73.81,
                                30,
                                List.of("sorting", "binary_search", "dfs", "greedy"),
                                List.of("vector", "map", "queue", "graph"),
                                adaScore,
                                adaScore.getSummary()
                        ),
                        new HandleReportRow(
                                2L,
                                "VJudge",
                                "vj_bruno_demo",
                                18,
                                9,
                                50.00,
                                12,
                                List.of("sorting", "brute_force"),
                                List.of("array", "vector"),
                                brunoScore,
                                brunoScore.getSummary()
                        )
                )
        );
    }

    private static SkillScore score(
            double overall,
            double ds,
            double algorithm,
            double problem,
            double quality,
            double consistency,
            double aiRisk,
            String summary
    ) {
        SkillScore score = new SkillScore();
        score.setOverallScore(decimal(overall));
        score.setDataStructureScore(decimal(ds));
        score.setAlgorithmScore(decimal(algorithm));
        score.setProblemSolvingScore(decimal(problem));
        score.setCodeQualityScore(decimal(quality));
        score.setPracticeConsistencyScore(decimal(consistency));
        score.setAiUsageRiskScore(decimal(aiRisk));
        score.setSummary(summary);
        return score;
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value);
    }

}
