package com.example.aicodeanalyzer.service;

import com.example.aicodeanalyzer.model.AiAnalysisResult;
import com.example.aicodeanalyzer.model.HandleAccount;
import com.example.aicodeanalyzer.model.Platform;
import com.example.aicodeanalyzer.model.SkillScore;
import com.example.aicodeanalyzer.model.Submission;
import com.example.aicodeanalyzer.report.ExcelReportData;
import com.example.aicodeanalyzer.report.ReportExportResult;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelReportServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void exportExcelCreatesWorkbookWithRequiredSheetsAndHeaders() throws Exception {
        ExcelReportService service = new ExcelReportService(true);

        ReportExportResult result = service.exportExcel(sampleData(), tempDir, false);

        assertTrue(Files.exists(result.filePath()));
        assertTrue(Files.size(result.filePath()) > 1024);
        assertTrue(result.filePath().getFileName().toString().endsWith(".xlsx"));
        assertFalse(result.opened());

        try (Workbook workbook = WorkbookFactory.create(result.filePath().toFile())) {
            assertEquals(5, workbook.getNumberOfSheets());
            assertNotNull(workbook.getSheet("Tong quan"));
            assertNotNull(workbook.getSheet("Danh sach handles"));
            assertNotNull(workbook.getSheet("Submissions"));
            assertNotNull(workbook.getSheet("AI Analysis"));
            assertNotNull(workbook.getSheet("Skill Scores"));

            assertEquals("Ngày xuất báo cáo", workbook.getSheet("Tong quan").getRow(2).getCell(0).getStringCellValue());
            assertEquals("Handle ID", workbook.getSheet("Danh sach handles").getRow(0).getCell(0).getStringCellValue());
            assertEquals("Codeforces", workbook.getSheet("Danh sach handles").getRow(1).getCell(1).getStringCellValue());
            assertEquals("cf_ada_demo", workbook.getSheet("Submissions").getRow(1).getCell(2).getStringCellValue());
            assertEquals("sorting, binary_search", workbook.getSheet("AI Analysis").getRow(1).getCell(6).getStringCellValue());
            assertEquals(78.60, workbook.getSheet("Skill Scores").getRow(1).getCell(11).getNumericCellValue(), 0.001);
            assertTrue(workbook.getSheet("Skill Scores").getColumnWidth(13) > 2800);
        }
    }

    private static ExcelReportData sampleData() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 13, 21, 45);
        Platform codeforces = new Platform(
                1L,
                "CODEFORCES",
                "Codeforces",
                "https://codeforces.com",
                "https://codeforces.com/api",
                true,
                now,
                now
        );
        HandleAccount handle = new HandleAccount(
                10L,
                1L,
                "cf_ada_demo",
                "Ada Demo",
                "Demo",
                "PUBLIC_DATA",
                true,
                now,
                "Nick thử nghiệm",
                now,
                now
        );
        Submission submission = new Submission(
                100L,
                10L,
                "245123456",
                "1703A",
                "YES or YES?",
                "1703",
                "GNU C++17",
                "OK",
                now.minusDays(1),
                46,
                1024L,
                1200,
                "implementation,strings",
                "https://codeforces.com/contest/1703/submission/245123456",
                now.minusDays(1),
                now.minusDays(1)
        );
        AiAnalysisResult analysis = new AiAnalysisResult(
                1000L,
                100L,
                "RULE_BASED",
                "1.0",
                "local",
                "vector, map",
                "sorting, binary_search",
                "time=O(n log n); space=O(n)",
                BigDecimal.valueOf(82),
                BigDecimal.valueOf(22),
                "LOW",
                "Phân tích thử nghiệm",
                "{}",
                "hash",
                now,
                now
        );
        SkillScore score = new SkillScore(
                2000L,
                10L,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 13),
                BigDecimal.valueOf(78.50),
                BigDecimal.valueOf(81.20),
                BigDecimal.valueOf(76.40),
                BigDecimal.valueOf(84.00),
                BigDecimal.valueOf(72.30),
                BigDecimal.valueOf(22.00),
                BigDecimal.valueOf(78.60),
                "Phân loại: Tốt. Có nền tảng thuật toán ổn định.",
                now,
                now,
                now
        );

        return new ExcelReportData(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 13),
                now,
                List.of(codeforces),
                List.of(handle),
                List.of(submission),
                List.of(analysis),
                List.of(score)
        );
    }
}
