package com.example.aicodeanalyzer.service;

import com.example.aicodeanalyzer.analyzer.CodeAnalyzer;
import com.example.aicodeanalyzer.analyzer.GeminiCodeAnalyzer;
import com.example.aicodeanalyzer.analyzer.OpenAiCodeAnalyzer;
import com.example.aicodeanalyzer.analyzer.RuleBasedCodeAnalyzer;
import com.example.aicodeanalyzer.config.AiConfig;
import com.example.aicodeanalyzer.exception.AiRateLimitException;
import com.example.aicodeanalyzer.exception.DatabaseException;
import com.example.aicodeanalyzer.model.AiAnalysisResult;
import com.example.aicodeanalyzer.model.SourceCodeDetail;
import com.example.aicodeanalyzer.repository.AiAnalysisResultRepository;
import com.example.aicodeanalyzer.repository.AnalysisJobRepository;
import com.example.aicodeanalyzer.repository.SourceCodeDetailRepository;
import com.example.aicodeanalyzer.repository.SubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Coordinates rule-based and AI analyzers, then persists analysis results.
 */
public class AnalysisService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisService.class);

    private final SourceCodeDetailRepository sourceCodeDetailRepository;
    private final AiAnalysisResultRepository aiAnalysisResultRepository;
    private final SubmissionRepository submissionRepository;
    private final SkillScoringService skillScoringService;
    private final CodeAnalyzer codeAnalyzer;
    private final AnalysisJobRepository analysisJobRepository;

    public AnalysisService() {
        this(
                new SourceCodeDetailRepository(),
                new AiAnalysisResultRepository(),
                new SubmissionRepository(),
                createDefaultAnalyzer()
        );
    }

    public AnalysisService(
            SourceCodeDetailRepository sourceCodeDetailRepository,
            AiAnalysisResultRepository aiAnalysisResultRepository,
            CodeAnalyzer codeAnalyzer
    ) {
        this(
                sourceCodeDetailRepository,
                aiAnalysisResultRepository,
                new SubmissionRepository(),
                codeAnalyzer
        );
    }

    public AnalysisService(
            SourceCodeDetailRepository sourceCodeDetailRepository,
            AiAnalysisResultRepository aiAnalysisResultRepository,
            SubmissionRepository submissionRepository,
            CodeAnalyzer codeAnalyzer
    ) {
        this(
                sourceCodeDetailRepository,
                aiAnalysisResultRepository,
                submissionRepository,
                codeAnalyzer,
                new SkillScoringService(),
                new AnalysisJobRepository()
        );
    }

    public AnalysisService(
            SourceCodeDetailRepository sourceCodeDetailRepository,
            AiAnalysisResultRepository aiAnalysisResultRepository,
            SubmissionRepository submissionRepository,
            CodeAnalyzer codeAnalyzer,
            SkillScoringService skillScoringService
    ) {
        this(
                sourceCodeDetailRepository,
                aiAnalysisResultRepository,
                submissionRepository,
                codeAnalyzer,
                skillScoringService,
                new AnalysisJobRepository()
        );
    }

    public AnalysisService(
            SourceCodeDetailRepository sourceCodeDetailRepository,
            AiAnalysisResultRepository aiAnalysisResultRepository,
            SubmissionRepository submissionRepository,
            CodeAnalyzer codeAnalyzer,
            SkillScoringService skillScoringService,
            AnalysisJobRepository analysisJobRepository
    ) {
        this.sourceCodeDetailRepository = Objects.requireNonNull(
                sourceCodeDetailRepository,
                "sourceCodeDetailRepository must not be null"
        );
        this.aiAnalysisResultRepository = Objects.requireNonNull(
                aiAnalysisResultRepository,
                "aiAnalysisResultRepository must not be null"
        );
        this.submissionRepository = Objects.requireNonNull(submissionRepository, "submissionRepository must not be null");
        this.codeAnalyzer = Objects.requireNonNull(codeAnalyzer, "codeAnalyzer must not be null");
        this.skillScoringService = Objects.requireNonNull(skillScoringService, "skillScoringService must not be null");
        this.analysisJobRepository = Objects.requireNonNull(analysisJobRepository, "analysisJobRepository must not be null");
    }

    public AiAnalysisResult analyzeSourceCode(long sourceCodeId) {
        SourceCodeDetail sourceCodeDetail = sourceCodeDetailRepository.findBySourceCodeId(sourceCodeId)
                .orElseThrow(() -> new DatabaseException("Cannot find source code id " + sourceCodeId + "."));

        markJobRunning(sourceCodeDetail);
        try {
            AiAnalysisResult analysisResult = analyzeWithConfiguredAnalyzer(sourceCodeDetail);
            AiAnalysisResult savedResult = aiAnalysisResultRepository.save(analysisResult);
            markJobSucceeded(sourceCodeId, savedResult.getAnalysisId());
            refreshSkillScore(savedResult);
            return savedResult;
        } catch (AiRateLimitException ex) {
            markJobQuotaDelayed(sourceCodeId, ex);
            throw ex;
        } catch (RuntimeException ex) {
            markJobFailed(sourceCodeId, ex);
            throw ex;
        }
    }

    private AiAnalysisResult analyzeWithConfiguredAnalyzer(SourceCodeDetail sourceCodeDetail) {
        if (codeAnalyzer instanceof RuleBasedCodeAnalyzer ruleBasedCodeAnalyzer) {
            return analyzeWithRuleBased(ruleBasedCodeAnalyzer, sourceCodeDetail);
        }

        try {
            return codeAnalyzer.analyze(sourceCodeDetail);
        } catch (AiRateLimitException ex) {
            LOGGER.warn(
                    "AI provider quota/rate limit reached for source_code_id={}. Marking analysis job as quota-delayed; no rule-based fallback will be saved as an AI result.",
                    sourceCodeDetail.sourceCodeId()
            );
            throw ex;
        }
    }

    private AiAnalysisResult analyzeWithRuleBased(
            RuleBasedCodeAnalyzer ruleBasedCodeAnalyzer,
            SourceCodeDetail sourceCodeDetail
    ) {
        if (!hasHandleIdentity(sourceCodeDetail)) {
            return ruleBasedCodeAnalyzer.analyze(sourceCodeDetail);
        }
        List<SourceCodeDetail> sourceHistory = sourceCodeDetailRepository.findRecentByHandle(
                sourceCodeDetail.platformCode(),
                sourceCodeDetail.handle(),
                50
        );
        return ruleBasedCodeAnalyzer.analyzeWithHistory(
                sourceCodeDetail,
                sourceHistory,
                submissionRepository.findRecentByPlatformAndHandle(
                        sourceCodeDetail.platformCode(),
                        sourceCodeDetail.handle(),
                        100
                )
        );
    }

    private void refreshSkillScore(AiAnalysisResult analysisResult) {
        if (analysisResult.getSubmissionId() == null) {
            return;
        }

        try {
            submissionRepository.findById(analysisResult.getSubmissionId())
                    .ifPresent(submission -> skillScoringService.calculateAndSave(submission.getHandleId()));
        } catch (RuntimeException ex) {
            LOGGER.warn(
                    "Analysis result was saved, but skill score refresh failed for submission {}.",
                    analysisResult.getSubmissionId(),
                    ex
            );
        }
    }

    private void markJobRunning(SourceCodeDetail sourceCodeDetail) {
        if (sourceCodeDetail.sourceCodeId() == null || sourceCodeDetail.submissionId() == null) {
            return;
        }
        try {
            if (analysisJobRepository.findBySourceCodeId(sourceCodeDetail.sourceCodeId()).isEmpty()) {
                analysisJobRepository.markPending(sourceCodeDetail.sourceCodeId(), sourceCodeDetail.submissionId());
            }
            analysisJobRepository.markRunning(sourceCodeDetail.sourceCodeId());
        } catch (RuntimeException ex) {
            LOGGER.debug("Could not mark analysis job RUNNING for source_code_id={}.",
                    sourceCodeDetail.sourceCodeId(), ex);
        }
    }

    private void markJobSucceeded(long sourceCodeId, Long analysisId) {
        if (analysisId == null) {
            return;
        }
        try {
            analysisJobRepository.markSucceeded(sourceCodeId, analysisId);
        } catch (RuntimeException ex) {
            LOGGER.debug("Could not mark analysis job SUCCEEDED for source_code_id={}.", sourceCodeId, ex);
        }
    }

    private void markJobQuotaDelayed(long sourceCodeId, AiRateLimitException ex) {
        try {
            analysisJobRepository.markQuotaDelayed(
                    sourceCodeId,
                    java.time.LocalDateTime.now().plus(ex.retryAfter()),
                    sanitizeMessage(ex.getMessage())
            );
        } catch (RuntimeException jobEx) {
            LOGGER.debug("Could not mark analysis job QUOTA_DELAYED for source_code_id={}.", sourceCodeId, jobEx);
        }
    }

    private void markJobFailed(long sourceCodeId, RuntimeException ex) {
        try {
            analysisJobRepository.markFailed(sourceCodeId, sanitizeMessage(ex.getMessage()));
        } catch (RuntimeException jobEx) {
            LOGGER.debug("Could not mark analysis job FAILED for source_code_id={}.", sourceCodeId, jobEx);
        }
    }

    private String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown analysis error.";
        }
        String sanitized = message.replaceAll("(?i)(password=|token=|api[_-]?key=)[^;\\s]+", "$1****");
        return sanitized.length() <= 1000 ? sanitized : sanitized.substring(0, 997) + "...";
    }

    private boolean hasHandleIdentity(SourceCodeDetail sourceCodeDetail) {
        return sourceCodeDetail.platformCode() != null
                && !sourceCodeDetail.platformCode().trim().isEmpty()
                && sourceCodeDetail.handle() != null
                && !sourceCodeDetail.handle().trim().isEmpty();
    }

    private static CodeAnalyzer createDefaultAnalyzer() {
        AiConfig aiConfig = AiConfig.load();
        if ("rule-based".equalsIgnoreCase(aiConfig.provider())) {
            LOGGER.info("Using rule-based analyzer because ai.provider=rule-based.");
            return new RuleBasedCodeAnalyzer();
        }
        if (aiConfig.useMockMode()) {
            LOGGER.warn(
                    "Using rule-based analyzer because Gemini/OpenAI API is not active. mockMode={}, hasApiKey={}, provider={}, model={}.",
                    aiConfig.mockMode(),
                    aiConfig.hasApiKey(),
                    aiConfig.provider(),
                    aiConfig.model()
            );
            return new RuleBasedCodeAnalyzer();
        }
        if (aiConfig.isGeminiProvider()) {
            LOGGER.info("Using Gemini API analyzer. provider={}, model={}, endpoint={}.",
                    aiConfig.provider(), aiConfig.model(), aiConfig.endpoint());
            return new GeminiCodeAnalyzer(new GeminiAnalyzerService(aiConfig));
        }
        LOGGER.info("Using OpenAI-compatible API analyzer. provider={}, model={}, endpoint={}.",
                aiConfig.provider(), aiConfig.model(), aiConfig.endpoint());
        return new OpenAiCodeAnalyzer(new OpenAIAnalyzerService(aiConfig));
    }
}
