package com.example.aicodeanalyzer.scheduler;

import com.example.aicodeanalyzer.model.CrawlLog;
import com.example.aicodeanalyzer.service.BackendWorkflowService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulerManagerTest {

    @Test
    void triggerCrawlNowPreparesVisibleChromeBeforeWorkflow() throws Exception {
        FakeBackendWorkflowService workflowService = new FakeBackendWorkflowService(1);
        SchedulerManager schedulerManager = new SchedulerManager(SchedulerConfig.defaults(), workflowService);

        try {
            schedulerManager.triggerCrawlNow();

            assertTrue(workflowService.awaitFinished(), "manual workflow should finish");
            assertEquals(1, workflowService.ensureVisibleCalls.get());
            assertEquals(0, workflowService.ensureHeadlessCalls.get());
            assertEquals(1, workflowService.runOnceCalls.get());
            assertEquals("MANUAL", workflowService.lastJobType);
            assertTrue(
                    workflowService.firstEnsureOrder.get() < workflowService.firstRunOrder.get(),
                    "visible Chrome readiness must be checked before running crawl/analyze workflow"
            );
        } finally {
            schedulerManager.shutdown();
        }
    }

    @Test
    void triggerCrawlNowDoesNotRunWorkflowsInParallel() throws Exception {
        FakeBackendWorkflowService workflowService = new FakeBackendWorkflowService(1);
        SchedulerManager schedulerManager = new SchedulerManager(SchedulerConfig.defaults(), workflowService);

        try {
            schedulerManager.triggerCrawlNow();
            assertTrue(workflowService.awaitFirstStarted(), "first workflow should start");

            schedulerManager.triggerCrawlNow();
            workflowService.releaseWorkflows();

            assertTrue(workflowService.awaitFinished(), "queued workflows should finish");
            assertEquals(1, workflowService.runOnceCalls.get());
            assertEquals(1, workflowService.maxConcurrentRuns.get());
        } finally {
            schedulerManager.shutdown();
        }
    }

    @Test
    void configureDailyCrawlKeepsTwentyFourHourScheduleState() {
        FakeBackendWorkflowService workflowService = new FakeBackendWorkflowService(0);
        SchedulerManager schedulerManager = new SchedulerManager(SchedulerConfig.defaults(), workflowService);
        LocalTime dailyTime = LocalTime.of(1, 0);

        try {
            schedulerManager.configureDailyCrawl(dailyTime, true);

            SchedulerStatus status = schedulerManager.status();
            assertTrue(status.started());
            assertTrue(status.autoCrawlEnabled());
            assertEquals(dailyTime, status.dailyRunTime());
        } finally {
            schedulerManager.shutdown();
        }
    }

    private static final class FakeBackendWorkflowService extends BackendWorkflowService {
        private final CountDownLatch firstStarted = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch finished;
        private final AtomicInteger sequence = new AtomicInteger();
        private final AtomicInteger concurrentRuns = new AtomicInteger();
        private final AtomicInteger ensureHeadlessCalls = new AtomicInteger();
        private final AtomicInteger ensureVisibleCalls = new AtomicInteger();
        private final AtomicInteger runOnceCalls = new AtomicInteger();
        private final AtomicInteger firstEnsureOrder = new AtomicInteger();
        private final AtomicInteger firstRunOrder = new AtomicInteger();
        private final AtomicInteger maxConcurrentRuns = new AtomicInteger();
        private volatile String lastJobType;

        private FakeBackendWorkflowService(int expectedRuns) {
            this.finished = new CountDownLatch(expectedRuns);
            if (expectedRuns == 0) {
                releaseWorkflows();
            }
        }

        @Override
        public boolean ensureHeadlessBotBrowserReady(Duration timeout) {
            ensureHeadlessCalls.incrementAndGet();
            return true;
        }

        @Override
        public boolean ensureVisibleBotBrowserReady(Duration timeout) {
            int order = sequence.incrementAndGet();
            firstEnsureOrder.compareAndSet(0, order);
            ensureVisibleCalls.incrementAndGet();
            return true;
        }

        @Override
        public BackendWorkflowResult runOnce(String jobType, int maxSubmissions, int analysisLimit) {
            int order = sequence.incrementAndGet();
            firstRunOrder.compareAndSet(0, order);
            runOnceCalls.incrementAndGet();
            lastJobType = jobType;

            int concurrent = concurrentRuns.incrementAndGet();
            maxConcurrentRuns.accumulateAndGet(concurrent, Math::max);
            firstStarted.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
                return new BackendWorkflowResult(successfulCrawlLog(jobType), new AnalysisQueueResult(0, 0, 0));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while testing scheduler workflow.", ex);
            } finally {
                concurrentRuns.decrementAndGet();
                finished.countDown();
            }
        }

        private boolean awaitFirstStarted() throws InterruptedException {
            return firstStarted.await(2, TimeUnit.SECONDS);
        }

        private boolean awaitFinished() throws InterruptedException {
            releaseWorkflows();
            return finished.await(2, TimeUnit.SECONDS);
        }

        private void releaseWorkflows() {
            release.countDown();
        }

        private CrawlLog successfulCrawlLog(String jobType) {
            LocalDateTime now = LocalDateTime.now();
            CrawlLog crawlLog = new CrawlLog();
            crawlLog.setJobType(jobType);
            crawlLog.setStatus("SUCCESS");
            crawlLog.setStartedAt(now);
            crawlLog.setFinishedAt(now);
            crawlLog.setTotalHandles(1);
            crawlLog.setTotalNewSubmissions(1);
            crawlLog.setTotalErrors(0);
            crawlLog.setMessage("ok");
            return crawlLog;
        }
    }
}
