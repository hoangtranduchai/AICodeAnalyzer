package com.example.aicodeanalyzer.crawler;

import com.example.aicodeanalyzer.model.SourceCode;
import com.example.aicodeanalyzer.model.Submission;

import java.time.LocalDateTime;

/**
 * Normalized submission and optional source code fetched from an online judge.
 */
public record CrawledSubmission(
        String platformSubmissionId,
        String problemCode,
        String problemName,
        String contestId,
        String language,
        String verdict,
        LocalDateTime submittedAt,
        Integer executionTimeMs,
        Long memoryBytes,
        Integer problemRating,
        String problemTags,
        String sourceUrl,
        SourceAvailability sourceAvailability,
        SourceOrigin sourceOrigin,
        String sourceCode,
        String sourceUnavailableReason
) {
    public CrawledSubmission(
            String platformSubmissionId,
            String problemCode,
            String problemName,
            String contestId,
            String language,
            String verdict,
            LocalDateTime submittedAt,
            Integer executionTimeMs,
            Long memoryBytes,
            Integer problemRating,
            String problemTags,
            String sourceUrl,
            SourceAvailability sourceAvailability,
            String sourceCode,
            String sourceUnavailableReason
    ) {
        this(
                platformSubmissionId,
                problemCode,
                problemName,
                contestId,
                language,
                verdict,
                submittedAt,
                executionTimeMs,
                memoryBytes,
                problemRating,
                problemTags,
                sourceUrl,
                sourceAvailability,
                SourceOrigin.UNKNOWN,
                sourceCode,
                sourceUnavailableReason
        );
    }

    public CrawledSubmission {
        if (platformSubmissionId == null || platformSubmissionId.trim().isEmpty()) {
            throw new IllegalArgumentException("platformSubmissionId is required.");
        }
        platformSubmissionId = platformSubmissionId.trim();
        sourceAvailability = sourceAvailability == null
                ? SourceAvailability.SOURCE_NOT_AVAILABLE
                : sourceAvailability;
        sourceOrigin = sourceOrigin == null ? SourceOrigin.UNKNOWN : sourceOrigin;
    }

    public Submission toSubmission(long handleId) {
        return new Submission(
                null,
                handleId,
                platformSubmissionId,
                problemCode,
                problemName,
                contestId,
                language,
                verdict,
                submittedAt,
                executionTimeMs,
                memoryBytes,
                problemRating,
                problemTags,
                sourceUrl,
                null,
                null
        );
    }

    public SourceCode toSourceCode(long submissionId) {
        boolean hasSource = sourceAvailability == SourceAvailability.AVAILABLE
                && sourceCode != null
                && !sourceCode.isBlank();
        String normalizedSource = hasSource ? sourceCode : null;

        return new SourceCode(
                null,
                submissionId,
                normalizedSource,
                null,
                hasSource ? countLines(normalizedSource) : 0,
                hasSource ? normalizedSource.length() : 0,
                LocalDateTime.now(),
                hasSource ? sourceOrigin.name() : sourceAvailability.name(),
                false,
                null,
                null
        );
    }

    private int countLines(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.split("\\R", -1).length;
    }
}
