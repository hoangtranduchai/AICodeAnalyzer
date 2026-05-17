package com.example.aicodeanalyzer.model;

import java.time.LocalDateTime;

/**
 * Maps to SQL Server table dbo.submissions.
 */
public class Submission {
    private Long submissionId;
    private Long handleId;
    private String platformSubmissionId;
    private String problemCode;
    private String problemName;
    private String contestId;
    private String language;
    private String verdict;
    private LocalDateTime submittedAt;
    private Integer executionTimeMs;
    private Long memoryBytes;
    private Integer problemRating;
    private String problemTags;
    private String sourceUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Submission() {
    }

    public Submission(
            Long submissionId,
            Long handleId,
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
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.submissionId = submissionId;
        this.handleId = handleId;
        this.platformSubmissionId = platformSubmissionId;
        this.problemCode = problemCode;
        this.problemName = problemName;
        this.contestId = contestId;
        this.language = language;
        this.verdict = verdict;
        this.submittedAt = submittedAt;
        this.executionTimeMs = executionTimeMs;
        this.memoryBytes = memoryBytes;
        this.problemRating = problemRating;
        this.problemTags = problemTags;
        this.sourceUrl = sourceUrl;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(Long submissionId) {
        this.submissionId = submissionId;
    }

    public Long getHandleId() {
        return handleId;
    }

    public void setHandleId(Long handleId) {
        this.handleId = handleId;
    }

    public String getPlatformSubmissionId() {
        return platformSubmissionId;
    }

    public void setPlatformSubmissionId(String platformSubmissionId) {
        this.platformSubmissionId = platformSubmissionId;
    }

    public String getProblemCode() {
        return problemCode;
    }

    public void setProblemCode(String problemCode) {
        this.problemCode = problemCode;
    }

    public String getProblemName() {
        return problemName;
    }

    public void setProblemName(String problemName) {
        this.problemName = problemName;
    }

    public String getContestId() {
        return contestId;
    }

    public void setContestId(String contestId) {
        this.contestId = contestId;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Integer getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(Integer executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public Long getMemoryBytes() {
        return memoryBytes;
    }

    public void setMemoryBytes(Long memoryBytes) {
        this.memoryBytes = memoryBytes;
    }

    public Integer getProblemRating() {
        return problemRating;
    }

    public void setProblemRating(Integer problemRating) {
        this.problemRating = problemRating;
    }

    public String getProblemTags() {
        return problemTags;
    }

    public void setProblemTags(String problemTags) {
        this.problemTags = problemTags;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
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
        return "Submission{" +
                "submissionId=" + submissionId +
                ", handleId=" + handleId +
                ", platformSubmissionId='" + platformSubmissionId + '\'' +
                ", problemCode='" + problemCode + '\'' +
                ", problemName='" + problemName + '\'' +
                ", contestId='" + contestId + '\'' +
                ", language='" + language + '\'' +
                ", verdict='" + verdict + '\'' +
                ", submittedAt=" + submittedAt +
                ", executionTimeMs=" + executionTimeMs +
                ", memoryBytes=" + memoryBytes +
                ", problemRating=" + problemRating +
                ", problemTags='" + problemTags + '\'' +
                ", sourceUrl='" + sourceUrl + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
