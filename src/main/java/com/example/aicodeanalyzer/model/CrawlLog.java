package com.example.aicodeanalyzer.model;

import java.time.LocalDateTime;

/**
 * Maps to SQL Server table dbo.crawl_logs.
 */
public class CrawlLog {
    private Long crawlLogId;
    private String jobType;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private int totalHandles;
    private int totalNewSubmissions;
    private int totalErrors;
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public CrawlLog() {
    }

    public CrawlLog(
            Long crawlLogId,
            String jobType,
            String status,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            int totalHandles,
            int totalNewSubmissions,
            int totalErrors,
            String message,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.crawlLogId = crawlLogId;
        this.jobType = jobType;
        this.status = status;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.totalHandles = totalHandles;
        this.totalNewSubmissions = totalNewSubmissions;
        this.totalErrors = totalErrors;
        this.message = message;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getCrawlLogId() {
        return crawlLogId;
    }

    public void setCrawlLogId(Long crawlLogId) {
        this.crawlLogId = crawlLogId;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public int getTotalHandles() {
        return totalHandles;
    }

    public void setTotalHandles(int totalHandles) {
        this.totalHandles = totalHandles;
    }

    public int getTotalNewSubmissions() {
        return totalNewSubmissions;
    }

    public void setTotalNewSubmissions(int totalNewSubmissions) {
        this.totalNewSubmissions = totalNewSubmissions;
    }

    public int getTotalErrors() {
        return totalErrors;
    }

    public void setTotalErrors(int totalErrors) {
        this.totalErrors = totalErrors;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "CrawlLog{" +
                "crawlLogId=" + crawlLogId +
                ", jobType='" + jobType + '\'' +
                ", status='" + status + '\'' +
                ", startedAt=" + startedAt +
                ", finishedAt=" + finishedAt +
                ", totalHandles=" + totalHandles +
                ", totalNewSubmissions=" + totalNewSubmissions +
                ", totalErrors=" + totalErrors +
                ", message='" + message + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
