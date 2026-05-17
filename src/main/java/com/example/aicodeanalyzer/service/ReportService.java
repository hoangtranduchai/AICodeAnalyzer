package com.example.aicodeanalyzer.service;

import com.example.aicodeanalyzer.exception.ReportException;
import com.example.aicodeanalyzer.report.EvaluationReportData;
import com.example.aicodeanalyzer.report.PdfReportExporter;
import com.example.aicodeanalyzer.report.ReportDataBuilder;
import com.example.aicodeanalyzer.report.ReportExportResult;
import com.example.aicodeanalyzer.report.ReportExporter;
import com.example.aicodeanalyzer.report.ReportRequest;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Collects report data and delegates export work to PDF/Excel exporters.
 */
public class ReportService {
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Path DEFAULT_REPORT_DIRECTORY = Path.of("reports");

    private final ReportDataBuilder reportDataBuilder;
    private final ReportExporter pdfReportExporter;

    public ReportService() {
        this(new ReportDataBuilder(), new PdfReportExporter());
    }

    public ReportService(ReportExporter pdfReportExporter) {
        this.reportDataBuilder = null;
        this.pdfReportExporter = Objects.requireNonNull(pdfReportExporter, "pdfReportExporter must not be null");
    }

    public ReportService(ReportDataBuilder reportDataBuilder, ReportExporter pdfReportExporter) {
        this.reportDataBuilder = Objects.requireNonNull(reportDataBuilder, "reportDataBuilder must not be null");
        this.pdfReportExporter = Objects.requireNonNull(pdfReportExporter, "pdfReportExporter must not be null");
    }

    public ReportExportResult exportPdf(LocalDate periodStart, LocalDate periodEnd, List<Long> handleIds) {
        return exportPdf(new ReportRequest(periodStart, periodEnd, handleIds, false));
    }

    public ReportExportResult exportPdf(ReportRequest request) {
        return exportPdf(request, DEFAULT_REPORT_DIRECTORY);
    }

    public ReportExportResult exportPdf(ReportRequest request, Path reportDirectory) {
        if (reportDataBuilder == null) {
            throw new ReportException("This ReportService instance cannot build report data from repositories.");
        }
        EvaluationReportData reportData = reportDataBuilder.build(request);
        return exportPdf(reportData, reportDirectory, request.openAfterExport());
    }

    public ReportExportResult exportPdf(
            EvaluationReportData reportData,
            Path reportDirectory,
            boolean openAfterExport
    ) {
        validate(reportData, reportDirectory);

        try {
            Files.createDirectories(reportDirectory);
        } catch (IOException ex) {
            throw new ReportException("Cannot create reports directory: " + reportDirectory.toAbsolutePath(), ex);
        }

        Path outputFile = reportDirectory.resolve(defaultFileName(reportData)).toAbsolutePath().normalize();
        pdfReportExporter.export(reportData, outputFile);
        boolean opened = openAfterExport && openFile(outputFile);
        return new ReportExportResult(outputFile, opened, message(outputFile, openAfterExport, opened));
    }

    private void validate(EvaluationReportData reportData, Path reportDirectory) {
        if (reportData == null) {
            throw new ReportException("Report data must not be null.");
        }
        if (reportDirectory == null) {
            throw new ReportException("Report directory must not be null.");
        }
    }

    private String defaultFileName(EvaluationReportData reportData) {
        String period = reportData.periodStart() == null || reportData.periodEnd() == null
                ? "all"
                : reportData.periodStart() + "_to_" + reportData.periodEnd();
        return "ai_code_analyzer_report_%s_%s.pdf".formatted(period, LocalDateTime.now().format(FILE_TIMESTAMP));
    }

    private boolean openFile(Path outputFile) {
        if (!Desktop.isDesktopSupported()) {
            return false;
        }
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.OPEN)) {
            return false;
        }
        try {
            desktop.open(outputFile.toFile());
            return true;
        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }

    private String message(Path outputFile, boolean openAfterExport, boolean opened) {
        if (openAfterExport && opened) {
            return "Đã xuất báo cáo PDF và đã gửi yêu cầu mở file: " + outputFile;
        }
        if (openAfterExport) {
            return "Đã xuất báo cáo PDF: " + outputFile
                    + ". Không thể tự mở file trên môi trường hiện tại, vui lòng mở từ thư mục reports.";
        }
        return "Đã xuất báo cáo PDF: " + outputFile + ". File đã sẵn sàng trong thư mục reports.";
    }
}
