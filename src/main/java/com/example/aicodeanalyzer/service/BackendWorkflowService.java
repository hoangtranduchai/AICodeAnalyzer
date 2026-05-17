package com.example.aicodeanalyzer.service;

import com.example.aicodeanalyzer.exception.AiRateLimitException;
import com.example.aicodeanalyzer.model.AiAnalysisResult;
import com.example.aicodeanalyzer.model.CrawlLog;
import com.example.aicodeanalyzer.model.SourceCode;
import com.example.aicodeanalyzer.crawler.CrawlRequest;
import com.example.aicodeanalyzer.repository.SourceCodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Runs the Stage 4 backend workflow: crawl active handles, persist sources, analyze pending sources.
 */
public class BackendWorkflowService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BackendWorkflowService.class);

    private static final int DEFAULT_MAX_SUBMISSIONS = CrawlRequest.UNLIMITED_SUBMISSIONS;
    private static final int DEFAULT_ANALYSIS_LIMIT = 100;

    private final CrawlService crawlService;
    private final SourceCodeRepository sourceCodeRepository;
    private final AnalysisService analysisService;
    private final List<Consumer<String>> progressListeners = new CopyOnWriteArrayList<>();

    public BackendWorkflowService() {
        this(new CrawlService(), new SourceCodeRepository(), new AnalysisService());
    }

    public BackendWorkflowService(
            CrawlService crawlService,
            SourceCodeRepository sourceCodeRepository,
            AnalysisService analysisService
    ) {
        this.crawlService = Objects.requireNonNull(crawlService, "crawlService must not be null");
        this.sourceCodeRepository = Objects.requireNonNull(sourceCodeRepository, "sourceCodeRepository must not be null");
        this.analysisService = Objects.requireNonNull(analysisService, "analysisService must not be null");
        this.crawlService.addProgressListener(this::emitProgress);
    }

    public BackendWorkflowResult runOnce(String jobType, int maxSubmissions, int analysisLimit) {
        int effectiveMaxSubmissions = maxSubmissions <= 0 ? DEFAULT_MAX_SUBMISSIONS : maxSubmissions;
        int effectiveAnalysisLimit = analysisLimit <= 0 ? DEFAULT_ANALYSIS_LIMIT : analysisLimit;

        emitProgress("Backend workflow started. jobType=" + jobType
                + ", maxSubmissions=" + effectiveMaxSubmissions
                + ", analysisLimit=" + effectiveAnalysisLimit + ".");
        CrawlLog crawlLog = crawlService.crawlAllActiveHandles(jobType, effectiveMaxSubmissions);
        emitProgress("Crawl stage finished. status=" + crawlLog.getStatus()
                + ", handles=" + crawlLog.getTotalHandles()
                + ", newSubmissions=" + crawlLog.getTotalNewSubmissions()
                + ", errors=" + crawlLog.getTotalErrors() + ".");
        AnalysisQueueResult analysisResult = analyzePendingSources(effectiveAnalysisLimit);
        emitProgress("Analysis stage finished. pending=" + analysisResult.pendingCount()
                + ", analyzed=" + analysisResult.analyzedCount()
                + ", errors=" + analysisResult.failedCount() + ".");
        return new BackendWorkflowResult(crawlLog, analysisResult);
    }

    public void addProgressListener(Consumer<String> listener) {
        if (listener != null) {
            progressListeners.add(listener);
        }
    }

    public void removeProgressListener(Consumer<String> listener) {
        progressListeners.remove(listener);
    }

    public boolean ensureHeadlessBotBrowserReady(Duration timeout) {
        return crawlService.ensureHeadlessBotBrowserReady(timeout);
    }

    public boolean ensureVisibleBotBrowserReady(Duration timeout) {
        return crawlService.ensureVisibleBotBrowserReady(timeout);
    }

    public AnalysisQueueResult analyzePendingSources(int limit) {
        int effectiveLimit = limit <= 0 ? DEFAULT_ANALYSIS_LIMIT : limit;
        List<SourceCode> pendingSources = sourceCodeRepository.findUnanalyzedSourceCodes(effectiveLimit);
        emitProgress("Found " + pendingSources.size() + " pending source(s) for AI/rule analysis.");

        int analyzedCount = 0;
        int failedCount = 0;
        for (SourceCode sourceCode : pendingSources) {
            if (sourceCode.getSourceCodeId() == null || !hasText(sourceCode.getCodeContent())) {
                emitProgress("Skipping source without code content. submission_id=" + sourceCode.getSubmissionId() + ".");
                continue;
            }
            try {
                emitProgress("Analyzing source_code_id=" + sourceCode.getSourceCodeId()
                        + ", submission_id=" + sourceCode.getSubmissionId() + ".");
                AiAnalysisResult ignored = analysisService.analyzeSourceCode(sourceCode.getSourceCodeId());
                analyzedCount++;
                emitProgress("Analyzed source_code_id=" + sourceCode.getSourceCodeId()
                        + ", submission_id=" + sourceCode.getSubmissionId()
                        + ", totalAnalyzed=" + analyzedCount + ".");
            } catch (AiRateLimitException ex) {
                failedCount++;
                String conciseMessage = conciseMessage(ex.getMessage());
                emitProgress("AI quota/rate limit reached at source_code_id=" + sourceCode.getSourceCodeId()
                        + ", submission_id=" + sourceCode.getSubmissionId()
                        + ". retryAfter=" + Math.max(1, ex.retryAfter().toSeconds())
                        + "s. Stopping this analysis batch. " + conciseMessage);
                LOGGER.warn(
                        "AI provider rate limit reached at source_code_id={} submission_id={}. "
                                + "Stopping this analysis batch to avoid repeated quota errors. retryAfter={}s. message={}",
                        sourceCode.getSourceCodeId(),
                        sourceCode.getSubmissionId(),
                        Math.max(1, ex.retryAfter().toSeconds()),
                        conciseMessage
                );
                break;
            } catch (RuntimeException ex) {
                failedCount++;
                String conciseMessage = conciseMessage(ex.getMessage());
                emitProgress("Analysis failed for source_code_id=" + sourceCode.getSourceCodeId()
                        + ", submission_id=" + sourceCode.getSubmissionId()
                        + ". Reason: " + conciseMessage);
                LOGGER.warn(
                        "Cannot analyze pending source_code_id={} submission_id={}.",
                        sourceCode.getSourceCodeId(),
                        sourceCode.getSubmissionId(),
                        ex
                );
            }
        }
        return new AnalysisQueueResult(pendingSources.size(), analyzedCount, failedCount);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void emitProgress(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        for (Consumer<String> listener : progressListeners) {
            listener.accept(message);
        }
    }

    private String conciseMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown error.";
        }
        String withoutBody = message.replaceAll("(?s)\\s*Body:\\s*\\{.*", "");
        return withoutBody.length() <= 240 ? withoutBody : withoutBody.substring(0, 237) + "...";
    }

    public record BackendWorkflowResult(CrawlLog crawlLog, AnalysisQueueResult analysisQueueResult) {
    }

    public record AnalysisQueueResult(int pendingCount, int analyzedCount, int failedCount) {
    }
}
