package com.example.aicodeanalyzer.report;

import com.example.aicodeanalyzer.exception.ReportException;
import com.example.aicodeanalyzer.model.SkillScore;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exports evaluation summaries to PDF using OpenPDF.
 */
public class PdfReportExporter implements ReportExporter {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Override
    public void export(EvaluationReportData reportData, Path outputFile) {
        if (reportData == null) {
            throw new ReportException("Report data must not be null.");
        }
        if (outputFile == null) {
            throw new ReportException("Output PDF path must not be null.");
        }

        try {
            Files.createDirectories(outputFile.toAbsolutePath().getParent());
        } catch (IOException ex) {
            throw new ReportException("Cannot create reports directory: " + outputFile.toAbsolutePath().getParent(), ex);
        }

        try (OutputStream outputStream = Files.newOutputStream(outputFile)) {
            Document document = new Document(PageSize.A4.rotate(), 28, 28, 28, 28);
            PdfWriter.getInstance(document, outputStream);
            document.open();

            Fonts fonts = Fonts.load();
            addTitle(document, reportData, fonts);
            addExecutiveSummary(document, reportData.handles(), fonts);
            addMethodology(document, fonts);
            addHandleList(document, reportData.handles(), fonts);
            addSubmissionStats(document, reportData.handles(), fonts);
            addDetectedItems(document, reportData.handles(), fonts);
            addScoreTable(document, reportData.handles(), fonts);
            addComparisonChart(document, reportData.handles(), fonts);
            addFeedback(document, reportData.handles(), fonts);
            addLimitations(document, fonts);
            addConclusion(document, fonts);

            document.close();
        } catch (IOException | DocumentException ex) {
            throw new ReportException("Cannot export PDF report: " + outputFile.toAbsolutePath(), ex);
        }
    }

    private void addTitle(Document document, EvaluationReportData reportData, Fonts fonts) throws DocumentException {
        Paragraph title = new Paragraph(reportData.title(), fonts.title());
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(10);
        document.add(title);

        String period = "Khoảng thời gian: %s - %s"
                .formatted(reportData.periodStart().format(DATE_FORMATTER), reportData.periodEnd().format(DATE_FORMATTER));
        document.add(paragraph(period, fonts.normal()));
        document.add(paragraph("Thời điểm sinh báo cáo: " + reportData.generatedAt().format(DATE_TIME_FORMATTER), fonts.normal()));
        document.add(paragraph("Số nick trong báo cáo: " + reportData.handles().size(), fonts.normal()));
        addSpacer(document);
    }

    private void addMethodology(Document document, Fonts fonts) throws DocumentException {
        addSection(document, "2. Phương pháp thu thập dữ liệu", fonts);
        document.add(paragraph(
                "Dữ liệu được tổng hợp từ submission công khai hoặc file import do người dùng có quyền truy cập hợp lệ cung cấp. "
                        + "Hệ thống lọc dữ liệu theo khoảng thời gian và danh sách handle được chọn.",
                fonts.normal()
        ));
        addSection(document, "3. Phương pháp phân tích AI", fonts);
        document.add(paragraph(
                "Source code được phân tích bằng AI Analyzer hoặc Rule-based Analyzer. Kết quả AI usage risk chỉ là xác suất/dấu hiệu tham khảo, "
                        + "không phải kết luận chắc chắn về việc sử dụng AI.",
                fonts.normal()
        ));
        addSpacer(document);
    }

    private void addHandleList(Document document, List<HandleReportRow> rows, Fonts fonts) throws DocumentException {
        addSection(document, "4. Danh sách nick thử nghiệm", fonts);
        PdfPTable table = table(new float[]{1, 3, 4});
        addHeader(table, fonts, "STT", "Platform", "Handle");
        for (int i = 0; i < rows.size(); i++) {
            HandleReportRow row = rows.get(i);
            addCell(table, fonts.normal(), String.valueOf(i + 1));
            addCell(table, fonts.normal(), row.platformName());
            addCell(table, fonts.normal(), row.handle());
        }
        document.add(table);
        addSpacer(document);
    }

    private void addSubmissionStats(Document document, List<HandleReportRow> rows, Fonts fonts) throws DocumentException {
        addSection(document, "5. Bảng thống kê submission", fonts);
        PdfPTable table = table(new float[]{3, 2, 2, 2, 2});
        addHeader(table, fonts, "Handle", "Tổng", "Accepted", "Tỉ lệ AC", "Source phân tích");
        for (HandleReportRow row : rows) {
            addCell(table, fonts.normal(), row.handle());
            addCell(table, fonts.normal(), String.valueOf(row.totalSubmissions()));
            addCell(table, fonts.normal(), String.valueOf(row.acceptedSubmissions()));
            addCell(table, fonts.normal(), "%.2f%%".formatted(row.acceptedRate()));
            addCell(table, fonts.normal(), String.valueOf(row.analyzedSourceCount()));
        }
        document.add(table);
        addSpacer(document);
    }

    private void addDetectedItems(Document document, List<HandleReportRow> rows, Fonts fonts) throws DocumentException {
        addSection(document, "6. Thuật toán và cấu trúc dữ liệu phát hiện", fonts);
        PdfPTable table = table(new float[]{3, 6, 6});
        addHeader(table, fonts, "Handle", "Thuật toán", "Cấu trúc dữ liệu");
        for (HandleReportRow row : rows) {
            addCell(table, fonts.normal(), row.handle());
            addCell(table, fonts.normal(), joinOrDash(row.algorithms()));
            addCell(table, fonts.normal(), joinOrDash(row.dataStructures()));
        }
        document.add(table);
        addSpacer(document);
    }

    private void addScoreTable(Document document, List<HandleReportRow> rows, Fonts fonts) throws DocumentException {
        addSection(document, "7. Điểm đánh giá từng nick", fonts);
        PdfPTable table = table(new float[]{2.5f, 1.3f, 1.3f, 1.5f, 1.4f, 1.4f, 1.3f, 1.4f});
        addHeader(table, fonts, "Handle", "DS", "Algo", "Problem", "Quality", "Consistency", "AI Risk", "Overall");
        for (HandleReportRow row : rows) {
            SkillScore score = row.skillScore();
            addCell(table, fonts.normal(), row.handle());
            addCell(table, fonts.normal(), score(score.getDataStructureScore()));
            addCell(table, fonts.normal(), score(score.getAlgorithmScore()));
            addCell(table, fonts.normal(), score(score.getProblemSolvingScore()));
            addCell(table, fonts.normal(), score(score.getCodeQualityScore()));
            addCell(table, fonts.normal(), score(score.getPracticeConsistencyScore()));
            addCell(table, fonts.normal(), score(score.getAiUsageRiskScore()));
            addCell(table, fonts.normal(), score(score.getOverallScore()));
        }
        document.add(table);
        addSpacer(document);
    }

    private void addComparisonChart(Document document, List<HandleReportRow> rows, Fonts fonts) throws DocumentException {
        addSection(document, "8. Biểu đồ so sánh Overall Score", fonts);
        PdfPTable table = table(new float[]{3, 2, 8});
        addHeader(table, fonts, "Handle", "Overall", "Biểu đồ");
        for (HandleReportRow row : rows) {
            double overall = row.skillScore().getOverallScore() == null
                    ? 0
                    : row.skillScore().getOverallScore().doubleValue();
            addCell(table, fonts.normal(), row.handle());
            addCell(table, fonts.normal(), "%.2f".formatted(overall));
            addCell(table, fonts.normal(), bar(overall));
        }
        document.add(table);
        addSpacer(document);
    }

    private void addFeedback(Document document, List<HandleReportRow> rows, Fonts fonts) throws DocumentException {
        addSection(document, "9. Nhận xét từng nick", fonts);
        if (rows.isEmpty()) {
            document.add(paragraph("Không có dữ liệu handle phù hợp với bộ lọc.", fonts.normal()));
            return;
        }
        for (HandleReportRow row : rows) {
            Paragraph handleTitle = new Paragraph(row.platformName() + " / " + row.handle(), fonts.subTitle());
            handleTitle.setSpacingBefore(6);
            handleTitle.setSpacingAfter(4);
            document.add(handleTitle);
            document.add(paragraph(row.feedback(), fonts.normal()));
        }
        addSpacer(document);
    }

    private void addLimitations(Document document, Fonts fonts) throws DocumentException {
        addSection(document, "10. Cảnh báo giới hạn của hệ thống", fonts);
        document.add(paragraph(
                "- Kết quả phụ thuộc vào dữ liệu đã crawl/import và source code khả dụng.\n"
                        + "- Nếu dữ liệu quá ít, điểm đánh giá chỉ nên dùng để tham khảo.\n"
                        + "- AI usage risk chỉ phản ánh dấu hiệu cần kiểm chứng thêm, không dùng để quy kết tiêu cực.\n"
                        + "- Nên kết hợp kết quả hệ thống với review thủ công hoặc phỏng vấn kỹ thuật.",
                fonts.normal()
        ));
        addSpacer(document);
    }

    private void addConclusion(Document document, Fonts fonts) throws DocumentException {
        addSection(document, "11. Kết luận và đề xuất", fonts);
        document.add(paragraph(
                "Báo cáo hỗ trợ so sánh năng lực lập trình dựa trên submission, kết quả phân tích source code và điểm kỹ năng tổng hợp. "
                        + "Trong giai đoạn tiếp theo, có thể bổ sung AST parser, biểu đồ tiến bộ theo tháng và bộ lọc nhóm/lớp để phục vụ báo cáo đồ án chi tiết hơn.",
                fonts.normal()
        ));
    }

    private void addExecutiveSummary(Document document, List<HandleReportRow> rows, Fonts fonts) throws DocumentException {
        addSection(document, "1. Tóm tắt điều hành", fonts);
        if (rows.isEmpty()) {
            document.add(paragraph("Không có dữ liệu phù hợp với bộ lọc báo cáo.", fonts.normal()));
            addSpacer(document);
            return;
        }
        int totalSubmissions = rows.stream().mapToInt(HandleReportRow::totalSubmissions).sum();
        int acceptedSubmissions = rows.stream().mapToInt(HandleReportRow::acceptedSubmissions).sum();
        int analyzedSources = rows.stream().mapToInt(HandleReportRow::analyzedSourceCount).sum();
        double acceptedRate = totalSubmissions == 0 ? 0 : acceptedSubmissions * 100.0 / totalSubmissions;
        document.add(paragraph(
                "Báo cáo bao gồm %d handle, %d submission, %d bài accepted và %d source code đã phân tích. Tỉ lệ accepted tổng hợp: %.2f%%."
                        .formatted(rows.size(), totalSubmissions, acceptedSubmissions, analyzedSources, acceptedRate),
                fonts.normal()
        ));
        document.add(paragraph(
                "Các nhận xét tập trung vào năng lực cấu trúc dữ liệu, thuật toán, chất lượng code, độ ổn định luyện tập và tín hiệu cần review thủ công. "
                        + "Kết quả không thay thế phỏng vấn kỹ thuật hoặc review source code trực tiếp.",
                fonts.normal()
        ));
        addSpacer(document);
    }

    private void addSection(Document document, String title, Fonts fonts) throws DocumentException {
        Paragraph paragraph = new Paragraph(title, fonts.section());
        paragraph.setSpacingBefore(8);
        paragraph.setSpacingAfter(6);
        document.add(paragraph);
    }

    private Paragraph paragraph(String text, Font font) {
        Paragraph paragraph = new Paragraph(text == null ? "" : text, font);
        paragraph.setLeading(14);
        paragraph.setSpacingAfter(4);
        return paragraph;
    }

    private void addSpacer(Document document) throws DocumentException {
        document.add(new Paragraph(" "));
    }

    private PdfPTable table(float[] widths) throws DocumentException {
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setSpacingAfter(6);
        return table;
    }

    private void addHeader(PdfPTable table, Fonts fonts, String... values) {
        for (String value : values) {
            PdfPCell cell = new PdfPCell(new Phrase(value, fonts.tableHeader()));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setBackgroundColor(new Color(230, 236, 245));
            cell.setPadding(5);
            table.addCell(cell);
        }
    }

    private void addCell(PdfPTable table, Font font, String value) {
        PdfPCell cell = new PdfPCell(new Phrase(value == null || value.isBlank() ? "-" : value, font));
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(4);
        table.addCell(cell);
    }

    private String joinOrDash(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "-";
        }
        return String.join(", ", values);
    }

    private String score(BigDecimal value) {
        return value == null ? "-" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String bar(double score) {
        int blocks = (int) Math.round(Math.max(0, Math.min(100, score)) / 5.0);
        return "█".repeat(blocks) + "░".repeat(Math.max(0, 20 - blocks));
    }

    private record Fonts(
            Font title,
            Font section,
            Font subTitle,
            Font normal,
            Font tableHeader
    ) {
        static Fonts load() {
            BaseFont baseFont = createUnicodeBaseFont();
            return new Fonts(
                    new Font(baseFont, 17, Font.BOLD),
                    new Font(baseFont, 12, Font.BOLD),
                    new Font(baseFont, 10, Font.BOLD),
                    new Font(baseFont, 9, Font.NORMAL),
                    new Font(baseFont, 8, Font.BOLD)
            );
        }

        private static BaseFont createUnicodeBaseFont() {
            List<Path> candidates = List.of(
                    Path.of("C:/Windows/Fonts/arial.ttf"),
                    Path.of("C:/Windows/Fonts/segoeui.ttf"),
                    Path.of("C:/Windows/Fonts/tahoma.ttf"),
                    Path.of("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
                    Path.of("/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf")
            );
            for (Path candidate : candidates) {
                if (Files.exists(candidate)) {
                    try {
                        return BaseFont.createFont(candidate.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    } catch (DocumentException | IOException ignored) {
                        // Try the next candidate.
                    }
                }
            }
            try {
                return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (DocumentException | IOException ex) {
                throw new ReportException("Cannot load a PDF font.", ex);
            }
        }
    }
}
