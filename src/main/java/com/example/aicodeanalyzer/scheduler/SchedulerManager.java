package com.example.aicodeanalyzer.scheduler;

import com.example.aicodeanalyzer.service.BackendWorkflowService;
import com.example.aicodeanalyzer.crawler.CrawlRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Runs the daily crawl/analyze/persist workflow on a single ScheduledExecutorService worker.
 */
public class SchedulerManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerManager.class);

    private static final int DEFAULT_MAX_SUBMISSIONS = CrawlRequest.UNLIMITED_SUBMISSIONS;
    private static final int DEFAULT_ANALYSIS_LIMIT = 100;
    private static final Duration CHROME_READY_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration ONE_DAY = Duration.ofHours(24);
    private static final DateTimeFormatter LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BackendWorkflowService backendWorkflowService;
    private final AtomicBoolean workflowRunning = new AtomicBoolean(false);
    private final List<Consumer<String>> workflowListeners = new CopyOnWriteArrayList<>();

    private ScheduledExecutorService executorService;
    private ScheduledFuture<?> dailyFuture;
    private LocalTime dailyRunTime;
    private boolean autoCrawlEnabled;
    private LocalDateTime lastManualTriggerAt;

    public SchedulerManager() {
        this(SchedulerConfig.load(), new BackendWorkflowService());
    }

    public SchedulerManager(SchedulerConfig schedulerConfig) {
        this(schedulerConfig, new BackendWorkflowService());
    }

    public SchedulerManager(SchedulerConfig schedulerConfig, BackendWorkflowService backendWorkflowService) {
        SchedulerConfig config = Objects.requireNonNullElseGet(schedulerConfig, SchedulerConfig::defaults);
        this.dailyRunTime = config.dailyRunTime();
        this.autoCrawlEnabled = config.autoCrawlEnabled();
        this.backendWorkflowService = Objects.requireNonNull(
                backendWorkflowService,
                "backendWorkflowService must not be null"
        );
    }

    public synchronized void start() {
        ensureExecutor();
        if (autoCrawlEnabled) {
            scheduleDailyCrawl(dailyRunTime);
        }
    }

    public synchronized void configureDailyCrawl(LocalTime runTime, boolean enabled) {
        this.dailyRunTime = Objects.requireNonNull(runTime, "runTime must not be null");
        this.autoCrawlEnabled = enabled;
        ensureExecutor();
        if (enabled) {
            scheduleDailyCrawl(runTime);
        } else {
            cancelDailyCrawl();
        }
    }

    public synchronized boolean triggerCrawlNow() {
        ensureExecutor();
        if (!workflowRunning.compareAndSet(false, true)) {
            emitWorkflowLog("Workflow is already running or queued. Ignoring duplicate manual trigger.");
            return false;
        }
        emitWorkflowLog("Manual workflow queued.");
        executorService.submit(() -> runWorkflow("MANUAL", true));
        lastManualTriggerAt = LocalDateTime.now();
        return true;
    }

    public void addWorkflowListener(Consumer<String> listener) {
        if (listener != null) {
            workflowListeners.add(listener);
            backendWorkflowService.addProgressListener(listener);
        }
    }

    public void removeWorkflowListener(Consumer<String> listener) {
        workflowListeners.remove(listener);
        backendWorkflowService.removeProgressListener(listener);
    }

    public synchronized SchedulerStatus status() {
        return new SchedulerStatus(
                executorService != null && !executorService.isShutdown(),
                autoCrawlEnabled,
                dailyRunTime,
                lastManualTriggerAt
        );
    }

    public synchronized void shutdown() {
        cancelDailyCrawl();
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
    }

    private void ensureExecutor() {
        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory());
        }
    }

    private void scheduleDailyCrawl(LocalTime runTime) {
        cancelDailyCrawl();
        long initialDelaySeconds = secondsUntil(runTime);
        dailyFuture = executorService.scheduleAtFixedRate(
                () -> runWorkflow("SCHEDULED", false),
                initialDelaySeconds,
                ONE_DAY.toSeconds(),
                TimeUnit.SECONDS
        );
        LOGGER.info(
                "Scheduled daily crawl workflow at {}. Initial delay: {} seconds.",
                runTime,
                initialDelaySeconds
        );
    }

    private void cancelDailyCrawl() {
        if (dailyFuture != null) {
            dailyFuture.cancel(false);
            dailyFuture = null;
        }
    }

    private void runWorkflow(String jobType, boolean alreadyReserved) {
        if (!alreadyReserved && !workflowRunning.compareAndSet(false, true)) {
            LOGGER.warn("Skipping {} workflow because a previous workflow is still running.", jobType);
            emitWorkflowLog("Skipping " + jobType + " workflow because a previous workflow is still running.");
            return;
        }

        try {
            LOGGER.info("Starting {} ScheduledExecutorService workflow.", jobType);
            emitWorkflowLog("Starting " + jobType + " workflow.");
            boolean manualWorkflow = "MANUAL".equalsIgnoreCase(jobType);
            emitWorkflowLog("Checking " + (manualWorkflow ? "visible" : "headless") + " Chrome CDP readiness.");
            boolean chromeReady = manualWorkflow
                    ? backendWorkflowService.ensureVisibleBotBrowserReady(CHROME_READY_TIMEOUT)
                    : backendWorkflowService.ensureHeadlessBotBrowserReady(CHROME_READY_TIMEOUT);
            if (!chromeReady) {
                throw new IllegalStateException(
                        (manualWorkflow ? "Visible" : "Headless")
                                + " Chrome CDP was not ready after "
                                + CHROME_READY_TIMEOUT.toSeconds()
                                + " seconds."
                );
            }
            emitWorkflowLog("Chrome CDP is ready. Crawling active handles.");

            BackendWorkflowService.BackendWorkflowResult result = backendWorkflowService.runOnce(
                    jobType,
                    DEFAULT_MAX_SUBMISSIONS,
                    DEFAULT_ANALYSIS_LIMIT
            );
            emitWorkflowLog(
                    "Finished " + jobType
                            + " workflow. crawlStatus=" + result.crawlLog().getStatus()
                            + ", handles=" + result.crawlLog().getTotalHandles()
                            + ", new=" + result.crawlLog().getTotalNewSubmissions()
                            + ", crawlErrors=" + result.crawlLog().getTotalErrors()
                            + ", pendingAnalysis=" + result.analysisQueueResult().pendingCount()
                            + ", analyzed=" + result.analysisQueueResult().analyzedCount()
                            + ", analysisErrors=" + result.analysisQueueResult().failedCount() + "."
            );
            LOGGER.info(
                    "Finished {} workflow. crawlStatus={}, handles={}, new={}, crawlErrors={}, pendingAnalysis={}, analyzed={}, analysisErrors={}.",
                    jobType,
                    result.crawlLog().getStatus(),
                    result.crawlLog().getTotalHandles(),
                    result.crawlLog().getTotalNewSubmissions(),
                    result.crawlLog().getTotalErrors(),
                    result.analysisQueueResult().pendingCount(),
                    result.analysisQueueResult().analyzedCount(),
                    result.analysisQueueResult().failedCount()
            );
        } catch (RuntimeException ex) {
            LOGGER.error("{} ScheduledExecutorService workflow failed.", jobType, ex);
            emitWorkflowLog(jobType + " workflow failed: " + ex.getMessage());
        } finally {
            workflowRunning.set(false);
        }
    }

    private void emitWorkflowLog(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        String line = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).format(LOG_TIME_FORMATTER)
                + " | " + message;
        for (Consumer<String> listener : workflowListeners) {
            listener.accept(line);
        }
    }

    private long secondsUntil(LocalTime runTime) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.with(runTime).truncatedTo(ChronoUnit.SECONDS);
        if (!nextRun.isAfter(now)) {
            nextRun = nextRun.plusDays(1);
        }
        return Math.max(1, Duration.between(now, nextRun).toSeconds());
    }

    private ThreadFactory daemonThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "daily-crawl-scheduler");
            thread.setDaemon(true);
            return thread;
        };
    }
}
