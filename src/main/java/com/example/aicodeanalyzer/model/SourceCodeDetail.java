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
        String problemCode,
        String problemName,
        String language,
        String verdict,
        LocalDateTime submittedAt,
        String codeContent,
        String codeHash,
        Integer lineCount,
        Integer charCount,
        LocalDateTime fetchedAt,
        AiAnalysisResult latestAnalysis
) {
    public String displayLabel() {
        return "%s / %s / %s".formatted(
                valueOrDash(platformName),
                valueOrDash(handle),
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
