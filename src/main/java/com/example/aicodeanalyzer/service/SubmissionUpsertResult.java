package com.example.aicodeanalyzer.service;

/**
 * Aggregated persistence statistics for crawl/import submission upsert operations.
 */
public record SubmissionUpsertResult(
        int newCount,
        int updatedCount,
        int skippedCount,
        int failedCount
) {
    public SubmissionUpsertResult {
        newCount = Math.max(0, newCount);
        updatedCount = Math.max(0, updatedCount);
        skippedCount = Math.max(0, skippedCount);
        failedCount = Math.max(0, failedCount);
    }
}
