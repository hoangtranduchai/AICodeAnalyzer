package com.example.aicodeanalyzer.report;

import java.nio.file.Path;

/**
 * Common contract for report exporters.
 */
public interface ReportExporter {
    void export(EvaluationReportData reportData, Path outputFile);
}
