package com.example.aicodeanalyzer.service;

import com.example.aicodeanalyzer.model.DashboardSnapshot;
import com.example.aicodeanalyzer.repository.DashboardRepository;

import java.util.Objects;

/**
 * Builds summary metrics and chart datasets for the dashboard screen.
 */
public class DashboardService {
    public static final int DEFAULT_TOP_LIMIT = 10;
    private static final int CHART_LIMIT = 20;

    private final DashboardRepository dashboardRepository;

    public DashboardService() {
        this(new DashboardRepository());
    }

    public DashboardService(DashboardRepository dashboardRepository) {
        this.dashboardRepository = Objects.requireNonNull(
                dashboardRepository,
                "dashboardRepository must not be null"
        );
    }

    public DashboardSnapshot loadDashboard() {
        return loadDashboard(DEFAULT_TOP_LIMIT);
    }

    public DashboardSnapshot loadDashboard(int topLimit) {
        return loadDashboard(topLimit, topLimit);
    }

    public DashboardSnapshot loadDashboard(int overallLimit, int aiRiskLimit) {
        int effectiveOverallLimit = Math.max(1, overallLimit);
        int effectiveAiRiskLimit = Math.max(1, aiRiskLimit);
        return new DashboardSnapshot(
                dashboardRepository.findSummary(),
                dashboardRepository.findSubmissionsByPlatform(),
                dashboardRepository.findAverageAlgorithmScoresByHandle(CHART_LIMIT),
                dashboardRepository.findTopHandlesByOverallScore(effectiveOverallLimit),
                dashboardRepository.findTopHandlesByAiRisk(effectiveAiRiskLimit)
        );
    }
}
