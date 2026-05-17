package com.example.aicodeanalyzer.crawler;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Carries normalized crawler output such as fetched submissions, source availability, and warnings.
 */
public record CrawlResult(
        String platformCode,
        String handle,
        List<CrawledSubmission> submissions,
        List<String> warnings,
        LocalDateTime crawledAt,
        int newCount,
        int updatedCount,
        int skippedCount,
        int failedCount
) {
    public CrawlResult(
            String platformCode,
            String handle,
            List<CrawledSubmission> submissions,
            List<String> warnings,
            LocalDateTime crawledAt
    ) {
        this(platformCode, handle, submissions, warnings, crawledAt, 0, 0, 0, 0);
    }

    public CrawlResult {
        if (platformCode == null || platformCode.trim().isEmpty()) {
            throw new IllegalArgumentException("platformCode is required.");
        }
        if (handle == null || handle.trim().isEmpty()) {
            throw new IllegalArgumentException("handle is required.");
        }
        platformCode = platformCode.trim();
        handle = handle.trim();
        submissions = List.copyOf(submissions == null ? List.of() : submissions);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        crawledAt = crawledAt == null ? LocalDateTime.now() : crawledAt;
        newCount = Math.max(0, newCount);
        updatedCount = Math.max(0, updatedCount);
        skippedCount = Math.max(0, skippedCount);
        failedCount = Math.max(0, failedCount);
    }

    public int unavailableSourceCount() {
        return (int) submissions.stream()
                .filter(submission -> submission.sourceAvailability() != SourceAvailability.AVAILABLE)
                .count();
    }

    public CrawlResult withStatistics(int newCount, int updatedCount, int skippedCount, int failedCount) {
        return new CrawlResult(
                platformCode,
                handle,
                submissions,
                warnings,
                crawledAt,
                newCount,
                updatedCount,
                skippedCount,
                failedCount
        );
    }
}
