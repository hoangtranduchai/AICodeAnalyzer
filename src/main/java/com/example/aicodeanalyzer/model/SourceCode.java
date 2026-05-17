package com.example.aicodeanalyzer.model;

import java.time.LocalDateTime;

/**
 * Maps to SQL Server table dbo.source_codes.
 */
public class SourceCode {
    private Long sourceCodeId;
    private Long submissionId;
    private String codeContent;
    private String codeHash;
    private Integer lineCount;
    private Integer charCount;
    private LocalDateTime fetchedAt;
    private String storageType;
    private boolean encrypted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SourceCode() {
    }

    public SourceCode(
            Long sourceCodeId,
            Long submissionId,
            String codeContent,
            String codeHash,
            Integer lineCount,
            Integer charCount,
            LocalDateTime fetchedAt,
            String storageType,
            boolean encrypted,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.sourceCodeId = sourceCodeId;
        this.submissionId = submissionId;
        this.codeContent = codeContent;
        this.codeHash = codeHash;
        this.lineCount = lineCount;
        this.charCount = charCount;
        this.fetchedAt = fetchedAt;
        this.storageType = storageType;
        this.encrypted = encrypted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getSourceCodeId() {
        return sourceCodeId;
    }

    public void setSourceCodeId(Long sourceCodeId) {
        this.sourceCodeId = sourceCodeId;
    }

    public Long getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(Long submissionId) {
        this.submissionId = submissionId;
    }

    public String getCodeContent() {
        return codeContent;
    }

    public void setCodeContent(String codeContent) {
        this.codeContent = codeContent;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    public Integer getLineCount() {
        return lineCount;
    }

    public void setLineCount(Integer lineCount) {
        this.lineCount = lineCount;
    }

    public Integer getCharCount() {
        return charCount;
    }

    public void setCharCount(Integer charCount) {
        this.charCount = charCount;
    }

    public LocalDateTime getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(LocalDateTime fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public boolean isEncrypted() {
        return encrypted;
    }

    public void setEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
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
        return "SourceCode{" +
                "sourceCodeId=" + sourceCodeId +
                ", submissionId=" + submissionId +
                ", codeHash='" + codeHash + '\'' +
                ", lineCount=" + lineCount +
                ", charCount=" + charCount +
                ", fetchedAt=" + fetchedAt +
                ", storageType='" + storageType + '\'' +
                ", encrypted=" + encrypted +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
