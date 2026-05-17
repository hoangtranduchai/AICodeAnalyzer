package com.example.aicodeanalyzer.app;

import com.example.aicodeanalyzer.repository.AiAnalysisResultRepository;
import com.example.aicodeanalyzer.repository.HandleAccountRepository;
import com.example.aicodeanalyzer.repository.PlatformRepository;
import com.example.aicodeanalyzer.repository.SkillScoreRepository;
import com.example.aicodeanalyzer.repository.SourceCodeRepository;
import com.example.aicodeanalyzer.repository.SubmissionRepository;
import com.example.aicodeanalyzer.scheduler.SchedulerConfig;
import com.example.aicodeanalyzer.scheduler.SchedulerManager;
import com.example.aicodeanalyzer.scheduler.SchedulerSettingsService;
import com.example.aicodeanalyzer.service.AnalysisService;
import com.example.aicodeanalyzer.service.BackendWorkflowService;
import com.example.aicodeanalyzer.service.CrawlService;
import com.example.aicodeanalyzer.service.DashboardService;
import com.example.aicodeanalyzer.service.ExcelReportService;
import com.example.aicodeanalyzer.service.HandleAccountService;
import com.example.aicodeanalyzer.service.ReportService;
import com.example.aicodeanalyzer.service.SourceCodeDetailService;

/**
 * Lightweight composition root for sharing service, repository, scheduler, and UI dependencies.
 */
public class ApplicationContext {
    private final SubmissionRepository submissionRepository = new SubmissionRepository();
    private final SourceCodeRepository sourceCodeRepository = new SourceCodeRepository();
    private final AiAnalysisResultRepository aiAnalysisResultRepository = new AiAnalysisResultRepository();
    private final SkillScoreRepository skillScoreRepository = new SkillScoreRepository();
    private final HandleAccountRepository handleAccountRepository = new HandleAccountRepository();
    private final PlatformRepository platformRepository = new PlatformRepository();
    private final CrawlService crawlService = new CrawlService();
    private final AnalysisService analysisService = new AnalysisService();
    private final BackendWorkflowService backendWorkflowService = new BackendWorkflowService(
            crawlService,
            sourceCodeRepository,
            analysisService
    );
    private final SchedulerManager schedulerManager = new SchedulerManager(
            SchedulerConfig.load(),
            backendWorkflowService
    );
    private final SchedulerSettingsService schedulerSettingsService = new SchedulerSettingsService();
    private final DashboardService dashboardService = new DashboardService();
    private final HandleAccountService handleAccountService = new HandleAccountService();
    private final SourceCodeDetailService sourceCodeDetailService = new SourceCodeDetailService();
    private final ReportService reportService = new ReportService();
    private final ExcelReportService excelReportService = new ExcelReportService();

    public SchedulerManager schedulerManager() {
        return schedulerManager;
    }

    public SchedulerSettingsService schedulerSettingsService() {
        return schedulerSettingsService;
    }

    public DashboardService dashboardService() {
        return dashboardService;
    }

    public CrawlService crawlService() {
        return crawlService;
    }

    public HandleAccountService handleAccountService() {
        return handleAccountService;
    }

    public SourceCodeDetailService sourceCodeDetailService() {
        return sourceCodeDetailService;
    }

    public AnalysisService analysisService() {
        return analysisService;
    }

    public ReportService reportService() {
        return reportService;
    }

    public ExcelReportService excelReportService() {
        return excelReportService;
    }

    public SubmissionRepository submissionRepository() {
        return submissionRepository;
    }

    public SourceCodeRepository sourceCodeRepository() {
        return sourceCodeRepository;
    }

    public AiAnalysisResultRepository aiAnalysisResultRepository() {
        return aiAnalysisResultRepository;
    }

    public SkillScoreRepository skillScoreRepository() {
        return skillScoreRepository;
    }

    public HandleAccountRepository handleAccountRepository() {
        return handleAccountRepository;
    }

    public PlatformRepository platformRepository() {
        return platformRepository;
    }
}
