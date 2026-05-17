package com.example.aicodeanalyzer.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Maps to SQL Server table dbo.ai_analysis_results.
 */
public class AiAnalysisResult {
    private Long analysisId;
    private Long submissionId;
    private String analyzerType;
    private String analyzerVersion;
    private String modelName;
    private String dataStructures;
    private String algorithms;
    private String complexityEstimate;
    private BigDecimal codeQualityScore;
    private BigDecimal aiRiskScore;
    private String aiRiskLevel;
    private String summary;
    private String rawResponse;
    private String promptHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AiAnalysisResult() {
    }

    public AiAnalysisResult(
            Long analysisId,
            Long submissionId,
            String analyzerType,
            String analyzerVersion,
            String modelName,
            String dataStructures,
            String algorithms,
            String complexityEstimate,
            BigDecimal codeQualityScore,
            BigDecimal aiRiskScore,
            String aiRiskLevel,
            String summary,
            String rawResponse,
            String promptHash,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.analysisId = analysisId;
        this.submissionId = submissionId;
        this.analyzerType = analyzerType;
        this.analyzerVersion = analyzerVersion;
        this.modelName = modelName;
        this.dataStructures = dataStructures;
        this.algorithms = algorithms;
        this.complexityEstimate = complexityEstimate;
        this.codeQualityScore = codeQualityScore;
        this.aiRiskScore = aiRiskScore;
        this.aiRiskLevel = aiRiskLevel;
        this.summary = summary;
        this.rawResponse = rawResponse;
        this.promptHash = promptHash;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getAnalysisId() {
        return analysisId;
    }

    public void setAnalysisId(Long analysisId) {
        this.analysisId = analysisId;
    }

    public Long getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(Long submissionId) {
        this.submissionId = submissionId;
    }

    public String getAnalyzerType() {
        return analyzerType;
    }

    public void setAnalyzerType(String analyzerType) {
        this.analyzerType = analyzerType;
    }

    public String getAnalyzerVersion() {
        return analyzerVersion;
    }

    public void setAnalyzerVersion(String analyzerVersion) {
        this.analyzerVersion = analyzerVersion;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getDataStructures() {
        return dataStructures;
    }

    public void setDataStructures(String dataStructures) {
        this.dataStructures = dataStructures;
    }

    public String getAlgorithms() {
        return algorithms;
    }

    public void setAlgorithms(String algorithms) {
        this.algorithms = algorithms;
    }

    public String getComplexityEstimate() {
        return complexityEstimate;
    }

    public void setComplexityEstimate(String complexityEstimate) {
        this.complexityEstimate = complexityEstimate;
    }

    public BigDecimal getCodeQualityScore() {
        return codeQualityScore;
    }

    public void setCodeQualityScore(BigDecimal codeQualityScore) {
        this.codeQualityScore = codeQualityScore;
    }

    public BigDecimal getAiRiskScore() {
        return aiRiskScore;
    }

    public void setAiRiskScore(BigDecimal aiRiskScore) {
        this.aiRiskScore = aiRiskScore;
    }

    public String getAiRiskLevel() {
        return aiRiskLevel;
    }

    public void setAiRiskLevel(String aiRiskLevel) {
        this.aiRiskLevel = aiRiskLevel;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public String getPromptHash() {
        return promptHash;
    }

    public void setPromptHash(String promptHash) {
        this.promptHash = promptHash;
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
        return "AiAnalysisResult{" +
                "analysisId=" + analysisId +
                ", submissionId=" + submissionId +
                ", analyzerType='" + analyzerType + '\'' +
                ", analyzerVersion='" + analyzerVersion + '\'' +
                ", modelName='" + modelName + '\'' +
                ", dataStructures='" + dataStructures + '\'' +
                ", algorithms='" + algorithms + '\'' +
                ", complexityEstimate='" + complexityEstimate + '\'' +
                ", codeQualityScore=" + codeQualityScore +
                ", aiRiskScore=" + aiRiskScore +
                ", aiRiskLevel='" + aiRiskLevel + '\'' +
                ", summary='" + summary + '\'' +
                ", promptHash='" + promptHash + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
