package com.example.aicodeanalyzer.model;

import java.time.LocalDateTime;

/**
 * Read model for viewing source code with submission and analysis context.
 */
public record SourceCodeDetail(
        Long sourceCodeId,
        Long submissionId,
        String platformCode,
        String platformName,
        String handle,
        String remoteId,
        String problemCode,
        String problemName,
        String language,
        String verdict,
        LocalDateTime submittedAt,
        String sourceCrawlStatus,
        String sourceCrawlError,
        String codeContent,
        String codeHash,
        Integer lineCount,
        Integer charCount,
        LocalDateTime fetchedAt,
        AiAnalysisResult latestAnalysis,
        String analysisJobStatus
) {
    public String displayLabel() {
        return "%s / %s / %s / %s".formatted(
                valueOrDash(platformName),
                valueOrDash(handle),
                valueOrDash(remoteId),
                valueOrDash(problemCode)
        );
    }

    public String problemDisplay() {
        if (hasText(problemCode) && hasText(problemName)) {
            return problemCode + " - " + problemName;
        }
        if (hasText(problemCode)) {
            return problemCode;
        }
        return valueOrDash(problemName);
    }

    private String valueOrDash(String value) {
        return hasText(value) ? value : "-";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
