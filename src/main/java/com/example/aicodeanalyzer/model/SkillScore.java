package com.example.aicodeanalyzer.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Maps to SQL Server table dbo.user_skill_scores.
 */
public class SkillScore {
    private Long scoreId;
    private Long handleId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal dataStructureScore;
    private BigDecimal algorithmScore;
    private BigDecimal problemSolvingScore;
    private BigDecimal codeQualityScore;
    private BigDecimal practiceConsistencyScore;
    private BigDecimal aiUsageRiskScore;
    private BigDecimal overallScore;
    private String summary;
    private LocalDateTime generatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SkillScore() {
    }

    public SkillScore(
            Long scoreId,
            Long handleId,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal dataStructureScore,
            BigDecimal algorithmScore,
            BigDecimal problemSolvingScore,
            BigDecimal codeQualityScore,
            BigDecimal practiceConsistencyScore,
            BigDecimal aiUsageRiskScore,
            BigDecimal overallScore,
            String summary,
            LocalDateTime generatedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.scoreId = scoreId;
        this.handleId = handleId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.dataStructureScore = dataStructureScore;
        this.algorithmScore = algorithmScore;
        this.problemSolvingScore = problemSolvingScore;
        this.codeQualityScore = codeQualityScore;
        this.practiceConsistencyScore = practiceConsistencyScore;
        this.aiUsageRiskScore = aiUsageRiskScore;
        this.overallScore = overallScore;
        this.summary = summary;
        this.generatedAt = generatedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getScoreId() {
        return scoreId;
    }

    public void setScoreId(Long scoreId) {
        this.scoreId = scoreId;
    }

    public Long getHandleId() {
        return handleId;
    }

    public void setHandleId(Long handleId) {
        this.handleId = handleId;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public BigDecimal getDataStructureScore() {
        return dataStructureScore;
    }

    public void setDataStructureScore(BigDecimal dataStructureScore) {
        this.dataStructureScore = dataStructureScore;
    }

    public BigDecimal getAlgorithmScore() {
        return algorithmScore;
    }

    public void setAlgorithmScore(BigDecimal algorithmScore) {
        this.algorithmScore = algorithmScore;
    }

    public BigDecimal getProblemSolvingScore() {
        return problemSolvingScore;
    }

    public void setProblemSolvingScore(BigDecimal problemSolvingScore) {
        this.problemSolvingScore = problemSolvingScore;
    }

    public BigDecimal getCodeQualityScore() {
        return codeQualityScore;
    }

    public void setCodeQualityScore(BigDecimal codeQualityScore) {
        this.codeQualityScore = codeQualityScore;
    }

    public BigDecimal getPracticeConsistencyScore() {
        return practiceConsistencyScore;
    }

    public void setPracticeConsistencyScore(BigDecimal practiceConsistencyScore) {
        this.practiceConsistencyScore = practiceConsistencyScore;
    }

    public BigDecimal getAiUsageRiskScore() {
        return aiUsageRiskScore;
    }

    public void setAiUsageRiskScore(BigDecimal aiUsageRiskScore) {
        this.aiUsageRiskScore = aiUsageRiskScore;
    }

    public BigDecimal getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(BigDecimal overallScore) {
        this.overallScore = overallScore;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
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
        return "SkillScore{" +
                "scoreId=" + scoreId +
                ", handleId=" + handleId +
                ", periodStart=" + periodStart +
                ", periodEnd=" + periodEnd +
                ", dataStructureScore=" + dataStructureScore +
                ", algorithmScore=" + algorithmScore +
                ", problemSolvingScore=" + problemSolvingScore +
                ", codeQualityScore=" + codeQualityScore +
                ", practiceConsistencyScore=" + practiceConsistencyScore +
                ", aiUsageRiskScore=" + aiUsageRiskScore +
                ", overallScore=" + overallScore +
                ", summary='" + summary + '\'' +
                ", generatedAt=" + generatedAt +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
