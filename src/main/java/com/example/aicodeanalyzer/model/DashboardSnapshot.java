package com.example.aicodeanalyzer.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read model for the dashboard screen.
 */
public record DashboardSnapshot(
        DashboardSummary summary,
        List<PlatformSubmissionStat> submissionsByPlatform,
        List<HandleAlgorithmStat> averageAlgorithmScores,
        List<HandleScoreStat> topOverallHandles,
        List<HandleScoreStat> topAiRiskHandles
) {
    public DashboardSnapshot {
        summary = summary == null ? DashboardSummary.empty() : summary;
        submissionsByPlatform = List.copyOf(submissionsByPlatform == null ? List.of() : submissionsByPlatform);
        averageAlgorithmScores = List.copyOf(averageAlgorithmScores == null ? List.of() : averageAlgorithmScores);
        topOverallHandles = List.copyOf(topOverallHandles == null ? List.of() : topOverallHandles);
        topAiRiskHandles = List.copyOf(topAiRiskHandles == null ? List.of() : topAiRiskHandles);
    }

    public boolean hasNoData() {
        boolean hasPlatformSubmissions = submissionsByPlatform.stream()
                .anyMatch(stat -> stat.submissionCount() > 0);
        return summary.totalHandles() == 0
                && summary.totalSubmissions() == 0
                && summary.pendingAnalysisSources() == 0
                && summary.analyzedSourceCodes() == 0
                && summary.sourceIssueCount() == 0
                && summary.recentCrawlErrors() == 0
                && !hasPlatformSubmissions
                && averageAlgorithmScores.isEmpty()
                && topOverallHandles.isEmpty()
                && topAiRiskHandles.isEmpty();
    }

    public record DashboardSummary(
            long totalHandles,
            long totalSubmissions,
            long pendingAnalysisSources,
            long analyzedSourceCodes,
            long sourceIssueCount,
            long recentCrawlErrors
    ) {
        public static DashboardSummary empty() {
            return new DashboardSummary(0, 0, 0, 0, 0, 0);
        }
    }

    public record PlatformSubmissionStat(String platformName, long submissionCount) {
    }

    public record HandleAlgorithmStat(String platformName, String handle, BigDecimal averageAlgorithmScore) {
    }

    public record HandleScoreStat(
            String platformName,
            String handle,
            BigDecimal dataStructureScore,
            BigDecimal algorithmScore,
            BigDecimal aiUsageRiskScore,
            BigDecimal overallScore,
            String summary
    ) {
    }
}
