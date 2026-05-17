package com.example.aicodeanalyzer.report;

import java.nio.file.Path;

/**
 * Result returned after a report export attempt.
 */
public record ReportExportResult(
        Path filePath,
        boolean opened,
        String message
) {
}
