package com.example.aicodeanalyzer.service;

import com.example.aicodeanalyzer.crawler.CodeforcesCrawler;
import com.example.aicodeanalyzer.crawler.CrawlRequest;
import com.example.aicodeanalyzer.crawler.CrawlResult;
import com.example.aicodeanalyzer.crawler.CrawledSubmission;
import com.example.aicodeanalyzer.crawler.CrawlerRateLimiter;
import com.example.aicodeanalyzer.crawler.OnlineJudgeCrawler;
import com.example.aicodeanalyzer.crawler.PlaywrightCdpSourceFetcher;
import com.example.aicodeanalyzer.crawler.SourceAvailability;
import com.example.aicodeanalyzer.crawler.VJudgeCrawler;
import com.example.aicodeanalyzer.exception.DatabaseException;
import com.example.aicodeanalyzer.model.CrawlLog;
import com.example.aicodeanalyzer.model.HandleAccount;
import com.example.aicodeanalyzer.model.Platform;
import com.example.aicodeanalyzer.repository.CrawlLogRepository;
import com.example.aicodeanalyzer.repository.HandleAccountRepository;
import com.example.aicodeanalyzer.repository.PlatformRepository;
import com.example.aicodeanalyzer.repository.SourceCodeRepository;
import com.example.aicodeanalyzer.repository.SubmissionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Coordinates crawler adapters, stores new submissions, and records crawl logs.
 */
public class CrawlService {
    private static final int DEFAULT_MAX_SUBMISSIONS = CrawlRequest.UNLIMITED_SUBMISSIONS;

    private final PlatformRepository platformRepository;
    private final HandleAccountRepository handleAccountRepository;
    private final SubmissionRepository submissionRepository;
    private final SourceCodeRepository sourceCodeRepository;
    private final CrawlLogRepository crawlLogRepository;
    private final SubmissionUpsertService submissionUpsertService;
    private final Map<String, OnlineJudgeCrawler> crawlersByPlatformCode;
    private final PlaywrightCdpSourceFetcher playwrightCdpSourceFetcher;
    private final List<Consumer<String>> progressListeners = new CopyOnWriteArrayList<>();

    public CrawlService() {
        this(new PlaywrightCdpSourceFetcher());
    }

    private CrawlService(PlaywrightCdpSourceFetcher playwrightCdpSourceFetcher) {
        this(
                new PlatformRepository(),
                new HandleAccountRepository(),
                new SubmissionRepository(),
                new SourceCodeRepository(),
                new CrawlLogRepository(),
                defaultCrawlers(playwrightCdpSourceFetcher),
                playwrightCdpSourceFetcher
        );
    }

    public CrawlService(
            PlatformRepository platformRepository,
            HandleAccountRepository handleAccountRepository,
            SubmissionRepository submissionRepository,
            SourceCodeRepository sourceCodeRepository,
            CrawlLogRepository crawlLogRepository,
            List<OnlineJudgeCrawler> crawlers
    ) {
        this(
                platformRepository,
                handleAccountRepository,
                submissionRepository,
                sourceCodeRepository,
                crawlLogRepository,
                crawlers,
                null
        );
    }

    private CrawlService(
            PlatformRepository platformRepository,
            HandleAccountRepository handleAccountRepository,
            SubmissionRepository submissionRepository,
            SourceCodeRepository sourceCodeRepository,
            CrawlLogRepository crawlLogRepository,
            List<OnlineJudgeCrawler> crawlers,
            PlaywrightCdpSourceFetcher playwrightCdpSourceFetcher
    ) {
        this.platformRepository = Objects.requireNonNull(platformRepository, "platformRepository must not be null");
        this.handleAccountRepository = Objects.requireNonNull(handleAccountRepository, "handleAccountRepository must not be null");
        this.submissionRepository = Objects.requireNonNull(submissionRepository, "submissionRepository must not be null");
        this.sourceCodeRepository = Objects.requireNonNull(sourceCodeRepository, "sourceCodeRepository must not be null");
        this.crawlLogRepository = Objects.requireNonNull(crawlLogRepository, "crawlLogRepository must not be null");
        this.submissionUpsertService = new SubmissionUpsertService(submissionRepository, sourceCodeRepository);
        this.submissionUpsertService.addProgressListener(this::emitProgress);
        this.crawlersByPlatformCode = mapCrawlers(crawlers);
        this.playwrightCdpSourceFetcher = playwrightCdpSourceFetcher;
    }

    public Process initializeBotBrowser() {
        return requirePlaywrightCdpSourceFetcher().initializeBotBrowser();
    }

    public boolean initializeVisibleBotBrowser(Duration timeout) {
        return requirePlaywrightCdpSourceFetcher().initializeVisibleBotBrowser(timeout);
    }

    public Process initializeBotBrowserHeadless() {
        return requirePlaywrightCdpSourceFetcher().initializeBotBrowserHeadless();
    }

    public String botBrowserCommandText() {
        return requirePlaywrightCdpSourceFetcher().chromeCommandText();
    }

    public String botBrowserHeadlessCommandText() {
        return requirePlaywrightCdpSourceFetcher().chromeHeadlessCommandText();
    }

    public boolean isBotBrowserReady() {
        return requirePlaywrightCdpSourceFetcher().isBotBrowserReady();
    }

    public boolean isVisibleBotBrowserReady() {
        return requirePlaywrightCdpSourceFetcher().isVisibleBotBrowserReady();
    }

    public boolean ensureHeadlessBotBrowserReady(Duration timeout) {
        return requirePlaywrightCdpSourceFetcher().ensureHeadlessBotBrowserReady(timeout);
    }

    public boolean ensureVisibleBotBrowserReady(Duration timeout) {
        return requirePlaywrightCdpSourceFetcher().ensureVisibleBotBrowserReady(timeout);
    }

    public void addProgressListener(Consumer<String> listener) {
        if (listener != null) {
            progressListeners.add(listener);
        }
    }

    public void removeProgressListener(Consumer<String> listener) {
        progressListeners.remove(listener);
    }

    public CrawlLog crawlHandle(long handleId, String jobType) {
        HandleAccount handleAccount = handleAccountRepository.findById(handleId)
                .orElseThrow(() -> new DatabaseException("Cannot find handle id " + handleId + "."));
        return crawlHandle(handleAccount, jobType, DEFAULT_MAX_SUBMISSIONS);
    }

    public CrawlResult crawlHandleResult(long handleId, String jobType, int maxSubmissions) {
        HandleAccount handleAccount = handleAccountRepository.findById(handleId)
                .orElseThrow(() -> new DatabaseException("Cannot find handle id " + handleId + "."));
        Platform platform = platformRepository.findById(handleAccount.getPlatformId())
                .orElseThrow(() -> new DatabaseException("Cannot find platform id " + handleAccount.getPlatformId() + "."));
        OnlineJudgeCrawler crawler = crawlerFor(platform.getCode());

        LocalDateTime startedAt = LocalDateTime.now();
        Set<String> knownSubmissionIds = knownSubmissionIds(platform.getCode(), handleAccount.getHandle(), maxSubmissions);
        emitProgress("Crawling handle " + platform.getCode() + "/" + handleAccount.getHandle()
                + " with maxSubmissions=" + maxSubmissions
                + ", consentConfirmed=" + hasConfirmedConsent(handleAccount)
                + ", knownSubmissionIds=" + knownSubmissionIds.size() + ".");
        try {
            CrawlRequest request = new CrawlRequest(
                    handleAccount.getHandle(),
                    maxSubmissions,
                    true,
                    hasConfirmedConsent(handleAccount),
                    null,
                    knownSubmissionIds
            );
            CrawlResult result = crawler.crawl(request);
            emitFetchedSubmissionDetails(platform.getCode(), result.submissions());
            CrawlResult persistedResult = submissionUpsertService.upsertCrawlResult(handleAccount, result);
            emitSavedSourceDetails(platform.getCode(), persistedResult.submissions());
            emitProgress("Finished handle " + platform.getCode() + "/" + handleAccount.getHandle()
                    + ". fetched=" + persistedResult.submissions().size()
                    + ", new=" + persistedResult.newCount()
                    + ", updated=" + persistedResult.updatedCount()
                    + ", skipped=" + persistedResult.skippedCount()
                    + ", failed=" + persistedResult.failedCount() + ".");
            saveCrawlLog(jobType, logStatus(persistedResult), startedAt, 1,
                    persistedResult.newCount(), persistedResult.failedCount(),
                    buildSuccessMessage(persistedResult, persistedResult.unavailableSourceCount()));
            updateHandleLastCrawledAt(handleAccount.getHandleId(), startedAt);
            return persistedResult;
        } catch (RuntimeException ex) {
            String message = "Crawler failed for " + platform.getCode() + "/" + handleAccount.getHandle()
                    + ". Reason: " + sanitizeMessage(ex.getMessage());
            emitProgress(message);
            saveCrawlLog(jobType, "FAILED", startedAt, 1, 0, 1, message);
            updateHandleLastCrawledAt(handleAccount.getHandleId(), startedAt);
            return new CrawlResult(
                    platform.getCode(),
                    handleAccount.getHandle(),
                    List.of(),
                    List.of(message),
                    LocalDateTime.now(),
                    0,
                    0,
                    0,
                    1
            );
        }
    }

    public CrawlLog crawlHandle(HandleAccount handleAccount, String jobType, int maxSubmissions) {
        Objects.requireNonNull(handleAccount, "handleAccount must not be null");
        Platform platform = platformRepository.findById(handleAccount.getPlatformId())
                .orElseThrow(() -> new DatabaseException("Cannot find platform id " + handleAccount.getPlatformId() + "."));
        OnlineJudgeCrawler crawler = crawlerFor(platform.getCode());

        LocalDateTime startedAt = LocalDateTime.now();
        Set<String> knownSubmissionIds = knownSubmissionIds(platform.getCode(), handleAccount.getHandle(), maxSubmissions);
        emitProgress("Crawling handle " + platform.getCode() + "/" + handleAccount.getHandle()
                + " with maxSubmissions=" + maxSubmissions
                + ", consentConfirmed=" + hasConfirmedConsent(handleAccount)
                + ", knownSubmissionIds=" + knownSubmissionIds.size() + ".");
        try {
            CrawlRequest request = new CrawlRequest(
                    handleAccount.getHandle(),
                    maxSubmissions,
                    true,
                    hasConfirmedConsent(handleAccount),
                    null,
                    knownSubmissionIds
            );
            CrawlResult result = crawler.crawl(request);
            emitFetchedSubmissionDetails(platform.getCode(), result.submissions());
            CrawlResult persistedResult = submissionUpsertService.upsertCrawlResult(handleAccount, result);
            emitSavedSourceDetails(platform.getCode(), persistedResult.submissions());
            int sourceUnavailableCount = result.unavailableSourceCount();
            String message = buildSuccessMessage(persistedResult, sourceUnavailableCount);
            emitProgress("Finished handle " + platform.getCode() + "/" + handleAccount.getHandle()
                    + ". fetched=" + persistedResult.submissions().size()
                    + ", new=" + persistedResult.newCount()
                    + ", updated=" + persistedResult.updatedCount()
                    + ", skipped=" + persistedResult.skippedCount()
                    + ", failed=" + persistedResult.failedCount()
                    + ", sourceUnavailable=" + sourceUnavailableCount + ".");
            CrawlLog log = saveCrawlLog(jobType, logStatus(persistedResult),
                    startedAt, 1, persistedResult.newCount(), persistedResult.failedCount(), message);
            updateHandleLastCrawledAt(handleAccount.getHandleId(), startedAt);
            return log;
        } catch (RuntimeException ex) {
            emitProgress("Crawler failed for " + platform.getCode() + "/" + handleAccount.getHandle()
                    + ". Reason: " + sanitizeMessage(ex.getMessage()));
            CrawlLog log = saveCrawlLog(jobType, "FAILED", startedAt, 1, 0, 1,
                    "Crawler failed for " + platform.getCode() + "/" + handleAccount.getHandle()
                            + ". Reason: " + sanitizeMessage(ex.getMessage()));
            updateHandleLastCrawledAt(handleAccount.getHandleId(), startedAt);
            return log;
        }
    }

    public CrawlLog crawlAllActiveHandles(String jobType, int maxSubmissions) {
        LocalDateTime startedAt = LocalDateTime.now();
        int totalHandles = 0;
        int totalSaved = 0;
        int totalErrors = 0;

        emitProgress("Loading active handles for " + normalizeJobType(jobType) + " crawl.");
        for (HandleAccount handleAccount : handleAccountRepository.findAll()) {
            if (!handleAccount.isActive()) {
                emitProgress("Skipping inactive handle id=" + handleAccount.getHandleId()
                        + " handle=" + handleAccount.getHandle() + ".");
                continue;
            }
            totalHandles++;
            try {
                emitProgress("Starting handle " + totalHandles + ": " + handleAccount.getHandle() + ".");
                CrawlLog handleLog = crawlHandle(handleAccount, jobType, maxSubmissions);
                totalSaved += handleLog.getTotalNewSubmissions();
                totalErrors += handleLog.getTotalErrors();
            } catch (RuntimeException ex) {
                totalErrors++;
                saveCrawlLog(jobType, "FAILED", LocalDateTime.now(), 1, 0, 1,
                        "Crawler failed for handle " + handleAccount.getHandle()
                                + ". Reason: " + sanitizeMessage(ex.getMessage()));
            }
        }

        String status = totalErrors == 0 ? "SUCCESS" : totalSaved == 0 ? "FAILED" : "PARTIAL_FAILED";
        emitProgress("Crawl cycle summary. status=" + status
                + ", handles=" + totalHandles
                + ", saved=" + totalSaved
                + ", errors=" + totalErrors + ".");
        return saveCrawlLog(jobType, status, startedAt, totalHandles, totalSaved, totalErrors,
                "Crawl cycle finished. Handles=" + totalHandles
                        + ", saved=" + totalSaved
                        + ", errors=" + totalErrors + ".");
    }

    private OnlineJudgeCrawler crawlerFor(String platformCode) {
        OnlineJudgeCrawler crawler = crawlersByPlatformCode.get(normalizeCode(platformCode));
        if (crawler == null) {
            throw new DatabaseException("No crawler registered for platform " + platformCode + ".");
        }
        return crawler;
    }

    private Set<String> knownSubmissionIds(String platformCode, String handle, int maxSubmissions) {
        if (maxSubmissions <= 0 || maxSubmissions == CrawlRequest.UNLIMITED_SUBMISSIONS) {
            Set<String> sourceCrawledIds = submissionRepository
                    .findSubmissionIdsByPlatformAndHandleWithCrawledSource(platformCode, handle);
            Set<String> allKnownIds = submissionRepository.findSubmissionIdsByPlatformAndHandle(platformCode, handle);
            int retryableKnown = Math.max(0, allKnownIds.size() - sourceCrawledIds.size());
            if (retryableKnown > 0) {
                emitProgress("Will retry source crawl for " + retryableKnown
                        + " known " + normalizeCode(platformCode) + " submission(s) whose source is not CRAWLED yet.");
            }
            return sourceCrawledIds;
        }
        int limit = Math.max(maxSubmissions, 1);
        return submissionRepository.findSubmissionIdsByPlatformAndHandleWithCrawledSource(platformCode, handle).stream()
                .limit(limit)
                .collect(Collectors.toSet());
    }

    private Map<String, OnlineJudgeCrawler> mapCrawlers(List<OnlineJudgeCrawler> crawlers) {
        Map<String, OnlineJudgeCrawler> mappedCrawlers = new LinkedHashMap<>();
        if (crawlers == null) {
            return mappedCrawlers;
        }
        for (OnlineJudgeCrawler crawler : crawlers) {
            mappedCrawlers.put(normalizeCode(crawler.platformCode()), crawler);
        }
        return mappedCrawlers;
    }

    private PlaywrightCdpSourceFetcher requirePlaywrightCdpSourceFetcher() {
        if (playwrightCdpSourceFetcher == null) {
            throw new IllegalStateException("Playwright CDP source fetcher is not configured for this CrawlService.");
        }
        return playwrightCdpSourceFetcher;
    }

    private static List<OnlineJudgeCrawler> defaultCrawlers(PlaywrightCdpSourceFetcher sourceFetcher) {
        HttpClient httpClient = HttpClient.newHttpClient();
        return List.of(
                new CodeforcesCrawler(
                        httpClient,
                        CrawlerRateLimiter.politeDefault(),
                        new ObjectMapper(),
                        3,
                        sourceFetcher
                ),
                new VJudgeCrawler(
                        httpClient,
                        CrawlerRateLimiter.politeDefault(),
                        new ObjectMapper(),
                        3,
                        sourceFetcher
                )
        );
    }

    private CrawlLog saveCrawlLog(
            String jobType,
            String status,
            LocalDateTime startedAt,
            int totalHandles,
            int totalNewSubmissions,
            int totalErrors,
            String message
    ) {
        return crawlLogRepository.save(new CrawlLog(
                null,
                normalizeJobType(jobType),
                status,
                startedAt,
                LocalDateTime.now(),
                totalHandles,
                totalNewSubmissions,
                totalErrors,
                message,
                null,
                null
        ));
    }

    private String buildSuccessMessage(CrawlResult result, int sourceUnavailableCount) {
        String warnings = result.warnings().isEmpty()
                ? ""
                : " Warnings: " + result.warnings().stream().collect(Collectors.joining(" | "));
        return "Crawler finished for " + result.platformCode() + "/" + result.handle()
                + ". fetched=" + result.submissions().size()
                + ", new=" + result.newCount()
                + ", updated=" + result.updatedCount()
                + ", skipped=" + result.skippedCount()
                + ", failed=" + result.failedCount()
                + ", " + SourceAvailability.SOURCE_NOT_AVAILABLE.name() + "=" + sourceUnavailableCount
                + "." + warnings;
    }

    private String logStatus(CrawlResult result) {
        if (result.failedCount() > 0) {
            return result.newCount() == 0 && result.updatedCount() == 0 ? "FAILED" : "PARTIAL_FAILED";
        }
        return result.warnings().isEmpty() ? "SUCCESS" : "PARTIAL_FAILED";
    }

    private boolean hasConfirmedConsent(HandleAccount handleAccount) {
        return "CONFIRMED".equalsIgnoreCase(handleAccount.getConsentStatus());
    }

    private String normalizeCode(String platformCode) {
        return platformCode == null ? "" : platformCode.trim().toUpperCase();
    }

    private String normalizeJobType(String jobType) {
        if ("SCHEDULED".equalsIgnoreCase(jobType)) {
            return "SCHEDULED";
        }
        if ("DIRECT".equalsIgnoreCase(jobType)) {
            return "DIRECT";
        }
        return "MANUAL";
    }

    private String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown crawler error.";
        }
        return message.replaceAll("(?i)(password=|token=|api[_-]?key=)[^;\\s]+", "$1****");
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

    private void updateHandleLastCrawledAt(long handleId, LocalDateTime lastCrawledAt) {
        try {
            handleAccountRepository.updateLastCrawledAt(handleId, lastCrawledAt);
        } catch (RuntimeException ex) {
            emitProgress("Could not update last crawled time for handle_id=" + handleId
                    + ". Reason: " + sanitizeMessage(ex.getMessage()));
        }
    }

    private void emitSavedSourceDetails(String platformCode, List<CrawledSubmission> submissions) {
        if (submissions == null || submissions.isEmpty()) {
            return;
        }
        for (CrawledSubmission crawledSubmission : submissions) {
            if (crawledSubmission == null || !hasText(crawledSubmission.sourceCode())) {
                continue;
            }
            try {
                submissionRepository.findByPlatformAndRemoteSubmissionId(
                                normalizeCode(platformCode),
                                crawledSubmission.platformSubmissionId()
                        )
                        .flatMap(submission -> sourceCodeRepository.findBySubmissionId(submission.getSubmissionId()))
                        .ifPresent(source -> emitProgress("Crawled source_code_id=" + source.getSourceCodeId()
                                + ", submission_id=" + source.getSubmissionId()
                                + ", remote_id=" + crawledSubmission.platformSubmissionId() + "."));
            } catch (RuntimeException ex) {
                emitProgress("Could not resolve saved source id for remote_id="
                        + crawledSubmission.platformSubmissionId()
                        + ". Reason: " + sanitizeMessage(ex.getMessage()));
            }
        }
    }

    private void emitFetchedSubmissionDetails(String platformCode, List<CrawledSubmission> submissions) {
        if (submissions == null || submissions.isEmpty()) {
            emitProgress("Crawler returned no candidate submissions to persist.");
            return;
        }
        for (CrawledSubmission submission : submissions) {
            if (submission == null) {
                continue;
            }
            emitProgress("Fetched submission metadata platform=" + normalizeCode(platformCode)
                    + ", remote_id=" + blankToDash(submission.platformSubmissionId())
                    + ", problem=" + blankToDash(submission.problemCode())
                    + ", name=" + blankToDash(submission.problemName())
                    + ", language=" + blankToDash(submission.language())
                    + ", verdict=" + blankToDash(submission.verdict())
                    + ", submittedAt=" + (submission.submittedAt() == null ? "-" : submission.submittedAt())
                    + ", sourceAvailability=" + submission.sourceAvailability()
                    + ", sourceOrigin=" + submission.sourceOrigin()
                    + ".");
            if (!hasText(submission.sourceCode()) && hasText(submission.sourceUnavailableReason())) {
                emitProgress("Source unavailable for remote_id=" + blankToDash(submission.platformSubmissionId())
                        + ". Reason: " + sanitizeMessage(submission.sourceUnavailableReason()));
            }
        }
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
