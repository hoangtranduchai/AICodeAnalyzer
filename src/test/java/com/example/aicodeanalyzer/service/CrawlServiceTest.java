package com.example.aicodeanalyzer.service;

import com.example.aicodeanalyzer.crawler.CrawlRequest;
import com.example.aicodeanalyzer.crawler.CrawlResult;
import com.example.aicodeanalyzer.crawler.OnlineJudgeCrawler;
import com.example.aicodeanalyzer.config.DatabaseConnectionFactory;
import com.example.aicodeanalyzer.model.CrawlLog;
import com.example.aicodeanalyzer.model.HandleAccount;
import com.example.aicodeanalyzer.model.Platform;
import com.example.aicodeanalyzer.repository.CrawlLogRepository;
import com.example.aicodeanalyzer.repository.HandleAccountRepository;
import com.example.aicodeanalyzer.repository.PlatformRepository;
import com.example.aicodeanalyzer.repository.RepositoryTestSupport;
import com.example.aicodeanalyzer.repository.SourceCodeRepository;
import com.example.aicodeanalyzer.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrawlServiceTest {

    @Test
    void crawlPassesAuthorizedSourceAccessOnlyForConfirmedConsent() {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = platform();
        HandleAccount confirmed = handle(10L, "confirmed_user", "CONFIRMED", true);
        RecordingCrawler crawler = new RecordingCrawler(false);
        RecordingCrawlLogRepository logRepository = new RecordingCrawlLogRepository(factory);
        CrawlService service = new CrawlService(
                new InMemoryPlatformRepository(factory, platform),
                new InMemoryHandleRepository(factory, List.of(confirmed)),
                new SubmissionRepository(factory),
                new SourceCodeRepository(factory),
                logRepository,
                List.of(crawler)
        );

        CrawlResult result = service.crawlHandleResult(confirmed.getHandleId(), "MANUAL", 25);

        assertTrue(crawler.lastRequest.hasUserConsent());
        assertEquals(25, crawler.lastRequest.maxSubmissions());
        assertEquals("confirmed_user", crawler.lastRequest.handle());
        assertEquals("SUCCESS", logRepository.savedLogs.getFirst().getStatus());
        assertEquals(0, result.failedCount());
    }

    @Test
    void crawlDoesNotGrantProtectedSourceAccessWithoutConfirmedConsent() {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = platform();
        HandleAccount unknownConsent = handle(11L, "unknown_user", "UNKNOWN", true);
        RecordingCrawler crawler = new RecordingCrawler(false);
        CrawlService service = new CrawlService(
                new InMemoryPlatformRepository(factory, platform),
                new InMemoryHandleRepository(factory, List.of(unknownConsent)),
                new SubmissionRepository(factory),
                new SourceCodeRepository(factory),
                new RecordingCrawlLogRepository(factory),
                List.of(crawler)
        );

        service.crawlHandleResult(unknownConsent.getHandleId(), "MANUAL", 10);

        assertFalse(crawler.lastRequest.hasUserConsent());
    }

    @Test
    void crawlFailureSanitizesSecretsBeforeSavingLogMessage() {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = platform();
        HandleAccount handle = handle(12L, "failure_user", "CONFIRMED", true);
        RecordingCrawlLogRepository logRepository = new RecordingCrawlLogRepository(factory);
        CrawlService service = new CrawlService(
                new InMemoryPlatformRepository(factory, platform),
                new InMemoryHandleRepository(factory, List.of(handle)),
                new SubmissionRepository(factory),
                new SourceCodeRepository(factory),
                logRepository,
                List.of(new RecordingCrawler(true))
        );

        CrawlResult result = service.crawlHandleResult(handle.getHandleId(), "SCHEDULED", 5);

        CrawlLog savedLog = logRepository.savedLogs.getFirst();
        assertEquals("FAILED", savedLog.getStatus());
        assertEquals("SCHEDULED", savedLog.getJobType());
        assertEquals(1, result.failedCount());
        assertTrue(savedLog.getMessage().contains("api_key=****"));
        assertTrue(savedLog.getMessage().contains("token=****"));
        assertFalse(savedLog.getMessage().contains("AIzaSy_TEST_SHOULD_NOT_LEAK"));
        assertFalse(savedLog.getMessage().contains("secret-token"));
    }

    @Test
    void crawlAllSkipsInactiveHandlesAndSummarizesPartialFailure() {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = platform();
        HandleAccount active = handle(13L, "active_user", "UNKNOWN", true);
        HandleAccount inactive = handle(14L, "inactive_user", "CONFIRMED", false);
        RecordingCrawler crawler = new RecordingCrawler(true);
        RecordingCrawlLogRepository logRepository = new RecordingCrawlLogRepository(factory);
        CrawlService service = new CrawlService(
                new InMemoryPlatformRepository(factory, platform),
                new InMemoryHandleRepository(factory, List.of(active, inactive)),
                new SubmissionRepository(factory),
                new SourceCodeRepository(factory),
                logRepository,
                List.of(crawler)
        );

        CrawlLog summary = service.crawlAllActiveHandles("manual", 5);

        assertEquals(1, crawler.callCount);
        assertEquals("FAILED", summary.getStatus());
        assertEquals(1, summary.getTotalHandles());
        assertEquals(1, summary.getTotalErrors());
    }

    private Platform platform() {
        return new Platform(1L, "CODEFORCES", "Codeforces", "https://codeforces.com",
                "https://codeforces.com/api", true, null, null);
    }

    private HandleAccount handle(long id, String name, String consentStatus, boolean active) {
        return new HandleAccount(
                id,
                1L,
                name,
                name,
                "qa",
                consentStatus,
                active,
                null,
                null,
                null,
                null
        );
    }

    private static final class RecordingCrawler implements OnlineJudgeCrawler {
        private final boolean fail;
        private CrawlRequest lastRequest;
        private int callCount;

        private RecordingCrawler(boolean fail) {
            this.fail = fail;
        }

        @Override
        public String platformCode() {
            return "CODEFORCES";
        }

        @Override
        public CrawlResult crawl(CrawlRequest request) {
            callCount++;
            lastRequest = request;
            if (fail) {
                throw new RuntimeException("remote rejected api_key=AIzaSy_TEST_SHOULD_NOT_LEAK token=secret-token");
            }
            return new CrawlResult(platformCode(), request.handle(), List.of(), List.of(), LocalDateTime.now());
        }
    }

    private static final class InMemoryPlatformRepository extends PlatformRepository {
        private final Platform platform;

        private InMemoryPlatformRepository(DatabaseConnectionFactory factory, Platform platform) {
            super(factory);
            this.platform = platform;
        }

        @Override
        public Optional<Platform> findById(long platformId) {
            return platform.getPlatformId() == platformId ? Optional.of(platform) : Optional.empty();
        }
    }

    private static final class InMemoryHandleRepository extends HandleAccountRepository {
        private final List<HandleAccount> handles;

        private InMemoryHandleRepository(DatabaseConnectionFactory factory, List<HandleAccount> handles) {
            super(factory);
            this.handles = handles;
        }

        @Override
        public Optional<HandleAccount> findById(long handleId) {
            return handles.stream().filter(handle -> handle.getHandleId() == handleId).findFirst();
        }

        @Override
        public List<HandleAccount> findAll() {
            return handles;
        }
    }

    private static final class RecordingCrawlLogRepository extends CrawlLogRepository {
        private final List<CrawlLog> savedLogs = new ArrayList<>();

        private RecordingCrawlLogRepository(DatabaseConnectionFactory factory) {
            super(factory);
        }

        @Override
        public CrawlLog save(CrawlLog crawlLog) {
            crawlLog.setCrawlLogId((long) savedLogs.size() + 1);
            savedLogs.add(crawlLog);
            return crawlLog;
        }
    }
}
