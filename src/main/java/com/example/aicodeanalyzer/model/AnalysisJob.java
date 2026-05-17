package com.example.aicodeanalyzer.model;

import java.time.LocalDateTime;

/**
 * Persistent queue state for AI/rule analysis work.
 */
public class AnalysisJob {
    private Long analysisJobId;
    private Long sourceCodeId;
    private Long submissionId;
    private String status;
    private int attemptCount;
    private LocalDateTime nextRetryAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long lastAnalysisId;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AnalysisJob(
            Long analysisJobId,
            Long sourceCodeId,
            Long submissionId,
            String status,
            int attemptCount,
            LocalDateTime nextRetryAt,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            Long lastAnalysisId,
            String lastError,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.analysisJobId = analysisJobId;
        this.sourceCodeId = sourceCodeId;
        this.submissionId = submissionId;
        this.status = status;
        this.attemptCount = attemptCount;
        this.nextRetryAt = nextRetryAt;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.lastAnalysisId = lastAnalysisId;
        this.lastError = lastError;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getAnalysisJobId() {
        return analysisJobId;
    }

    public Long getSourceCodeId() {
        return sourceCodeId;
    }

    public Long getSubmissionId() {
        return submissionId;
    }

    public String getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public Long getLastAnalysisId() {
        return lastAnalysisId;
    }

    public String getLastError() {
        return lastError;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
