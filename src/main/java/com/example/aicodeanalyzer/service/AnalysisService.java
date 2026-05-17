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
                new SkillScoringService()
        );
    }

    public AnalysisService(
            SourceCodeDetailRepository sourceCodeDetailRepository,
            AiAnalysisResultRepository aiAnalysisResultRepository,
            SubmissionRepository submissionRepository,
            CodeAnalyzer codeAnalyzer,
            SkillScoringService skillScoringService
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
    }

    public AiAnalysisResult analyzeSourceCode(long sourceCodeId) {
        SourceCodeDetail sourceCodeDetail = sourceCodeDetailRepository.findBySourceCodeId(sourceCodeId)
                .orElseThrow(() -> new DatabaseException("Cannot find source code id " + sourceCodeId + "."));

        AiAnalysisResult analysisResult = analyzeWithConfiguredAnalyzer(sourceCodeDetail);
        AiAnalysisResult savedResult = aiAnalysisResultRepository.save(analysisResult);
        refreshSkillScore(savedResult);
        return savedResult;
    }

    private AiAnalysisResult analyzeWithConfiguredAnalyzer(SourceCodeDetail sourceCodeDetail) {
        if (codeAnalyzer instanceof RuleBasedCodeAnalyzer ruleBasedCodeAnalyzer) {
            return analyzeWithRuleBased(ruleBasedCodeAnalyzer, sourceCodeDetail);
        }

        try {
            return codeAnalyzer.analyze(sourceCodeDetail);
        } catch (AiRateLimitException ex) {
            LOGGER.warn(
                    "AI provider quota/rate limit reached for source_code_id={}. Falling back to rule-based analyzer.",
                    sourceCodeDetail.sourceCodeId()
            );
            return analyzeWithRuleBased(new RuleBasedCodeAnalyzer(), sourceCodeDetail);
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

    private boolean hasHandleIdentity(SourceCodeDetail sourceCodeDetail) {
        return sourceCodeDetail.platformCode() != null
                && !sourceCodeDetail.platformCode().trim().isEmpty()
                && sourceCodeDetail.handle() != null
                && !sourceCodeDetail.handle().trim().isEmpty();
    }

    private static CodeAnalyzer createDefaultAnalyzer() {
        AiConfig aiConfig = AiConfig.load();
        if ("rule-based".equalsIgnoreCase(aiConfig.provider()) || aiConfig.useMockMode()) {
            return new RuleBasedCodeAnalyzer();
        }
        if (aiConfig.isGeminiProvider()) {
            return new GeminiCodeAnalyzer(new GeminiAnalyzerService(aiConfig));
        }
        return new OpenAiCodeAnalyzer(new OpenAIAnalyzerService(aiConfig));
    }
}
