package com.example.aicodeanalyzer.model;

import java.time.LocalDateTime;

/**
 * Maps to SQL Server table dbo.programming_handles.
 */
public class HandleAccount {
    private Long handleId;
    private Long platformId;
    private String handle;
    private String displayName;
    private String groupName;
    private Integer rating;
    private String rankName;
    private String generalEvaluation;
    private String consentStatus;
    private boolean active;
    private LocalDateTime lastCrawledAt;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public HandleAccount() {
    }

    public HandleAccount(
            Long handleId,
            Long platformId,
            String handle,
            String displayName,
            String groupName,
            String consentStatus,
            boolean active,
            LocalDateTime lastCrawledAt,
            String notes,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this(
                handleId,
                platformId,
                handle,
                displayName,
                groupName,
                null,
                null,
                null,
                consentStatus,
                active,
                lastCrawledAt,
                notes,
                createdAt,
                updatedAt
        );
    }

    public HandleAccount(
            Long handleId,
            Long platformId,
            String handle,
            String displayName,
            String groupName,
            Integer rating,
            String rankName,
            String generalEvaluation,
            String consentStatus,
            boolean active,
            LocalDateTime lastCrawledAt,
            String notes,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.handleId = handleId;
        this.platformId = platformId;
        this.handle = handle;
        this.displayName = displayName;
        this.groupName = groupName;
        this.rating = rating;
        this.rankName = rankName;
        this.generalEvaluation = generalEvaluation;
        this.consentStatus = consentStatus;
        this.active = active;
        this.lastCrawledAt = lastCrawledAt;
        this.notes = notes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getHandleId() {
        return handleId;
    }

    public void setHandleId(Long handleId) {
        this.handleId = handleId;
    }

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
    }

    public String getHandle() {
        return handle;
    }

    public void setHandle(String handle) {
        this.handle = handle;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getRankName() {
        return rankName;
    }

    public void setRankName(String rankName) {
        this.rankName = rankName;
    }

    public String getGeneralEvaluation() {
        return generalEvaluation;
    }

    public void setGeneralEvaluation(String generalEvaluation) {
        this.generalEvaluation = generalEvaluation;
    }

    public String getConsentStatus() {
        return consentStatus;
    }

    public void setConsentStatus(String consentStatus) {
        this.consentStatus = consentStatus;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getLastCrawledAt() {
        return lastCrawledAt;
    }

    public void setLastCrawledAt(LocalDateTime lastCrawledAt) {
        this.lastCrawledAt = lastCrawledAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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
        return "HandleAccount{" +
                "handleId=" + handleId +
                ", platformId=" + platformId +
                ", handle='" + handle + '\'' +
                ", displayName='" + displayName + '\'' +
                ", groupName='" + groupName + '\'' +
                ", rating=" + rating +
                ", rankName='" + rankName + '\'' +
                ", generalEvaluation='" + generalEvaluation + '\'' +
                ", consentStatus='" + consentStatus + '\'' +
                ", active=" + active +
                ", lastCrawledAt=" + lastCrawledAt +
                ", notes='" + notes + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
