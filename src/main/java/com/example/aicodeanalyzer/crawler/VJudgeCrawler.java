package com.example.aicodeanalyzer.crawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches permitted VJudge data while respecting access rights, throttling, and platform rules.
 */
public class VJudgeCrawler extends AbstractHttpCrawler {
    private static final String PLATFORM_CODE = "VJUDGE";
    private static final String BASE_URL = "https://vjudge.net";
    private static final int UNLIMITED_PAGE_SIZE = 100;
    private static final String SOURCE_NOT_AVAILABLE_REASON =
            "VJudge source could not be fetched from public/authorized web pages. Check Chrome session or OCR settings.";

    private final ObjectMapper objectMapper;
    private final SourceFetcher sourceFetcher;

    public VJudgeCrawler() {
        this(
                HttpClient.newHttpClient(),
                CrawlerRateLimiter.politeDefault(),
                new ObjectMapper(),
                3,
                DefaultSourceFetchers.authorizedBrowserThenHttp()
        );
    }

    public VJudgeCrawler(
            HttpClient httpClient,
            CrawlerRateLimiter rateLimiter,
            ObjectMapper objectMapper,
            int maxAttempts
    ) {
        this(
                httpClient,
                rateLimiter,
                objectMapper,
                maxAttempts,
                new NoopSourceFetcher("Source fetching is disabled for this crawler instance.")
        );
    }

    public VJudgeCrawler(
            HttpClient httpClient,
            CrawlerRateLimiter rateLimiter,
            ObjectMapper objectMapper,
            int maxAttempts,
            SourceFetcher sourceFetcher
    ) {
        super(httpClient, rateLimiter, maxAttempts);
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.sourceFetcher = sourceFetcher == null
                ? new NoopSourceFetcher("Source fetching is disabled for this crawler instance.")
                : sourceFetcher;
    }

    @Override
    public String platformCode() {
        return PLATFORM_CODE;
    }

    @Override
    public CrawlResult crawl(CrawlRequest request) {
        if (!request.publicDataOnly() && !request.hasUserConsent()) {
            throw new CrawlException("VJudge private data crawl requires explicit valid user consent.");
        }

        List<CrawledSubmission> submissions = new ArrayList<>();
        int returnedCount = 0;
        int start = 0;
        int length = request.isUnlimited() ? UNLIMITED_PAGE_SIZE : Math.max(1, request.maxSubmissions());
        while (true) {
            String body = getText(buildStatusUri(request, start, length), request.requestTimeout());
            JsonNode root = parseJson(body);
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return new CrawlResult(
                        PLATFORM_CODE,
                        request.handle(),
                        List.of(),
                        List.of("VJudge did not return public status JSON. No scraping/login bypass was attempted."),
                        LocalDateTime.now()
                );
            }

            int pageRows = data.size();
            for (JsonNode row : data) {
                returnedCount++;
                mapStatusRow(row, request).ifPresent(submissions::add);
            }

            if (!request.isUnlimited() || pageRows < length || pageRows == 0) {
                break;
            }
            start += pageRows;
        }

        List<String> warnings = new ArrayList<>();
        if (submissions.isEmpty()) {
            warnings.add(returnedCount == 0
                    ? "No public VJudge submissions found for handle " + request.handle() + "."
                    : "No new VJudge submissions found for handle " + request.handle() + ".");
        }
        warnings.add(sourceWarning(submissions));

        return new CrawlResult(PLATFORM_CODE, request.handle(), submissions, warnings, LocalDateTime.now());
    }

    private URI buildStatusUri(CrawlRequest request, int start, int length) {
        String encodedHandle = URLEncoder.encode(request.handle(), StandardCharsets.UTF_8);
        String uri = BASE_URL + "/status/data?draw=1&start=" + Math.max(0, start)
                + "&length=" + Math.max(1, length)
                + "&un=" + encodedHandle;
        return URI.create(uri);
    }

    private java.util.Optional<CrawledSubmission> mapStatusRow(JsonNode row, CrawlRequest request) {
        if (row.isArray()) {
            return mapArrayRow(row, request);
        }
        if (row.isObject()) {
            return mapObjectRow(row, request);
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<CrawledSubmission> mapArrayRow(JsonNode row, CrawlRequest request) {
        String runId = textAt(row, 0);
        if (runId == null) {
            return java.util.Optional.empty();
        }
        if (request.isKnownSubmission(runId)) {
            return java.util.Optional.empty();
        }
        SourceFetchResult sourceFetchResult = fetchSource(runId, request);

        return java.util.Optional.of(new CrawledSubmission(
                runId,
                textAt(row, 3),
                textAt(row, 3),
                null,
                textAt(row, 7),
                normalizeVerdict(textAt(row, 4)),
                null,
                integerFromText(textAt(row, 5)),
                null,
                null,
                textAt(row, 2),
                solutionUrl(runId),
                sourceFetchResult.availability(),
                sourceFetchResult.origin(),
                sourceFetchResult.sourceCode(),
                sourceFetchResult.unavailableReason()
        ));
    }

    private java.util.Optional<CrawledSubmission> mapObjectRow(JsonNode row, CrawlRequest request) {
        String runId = firstText(row, "runId", "id", "submissionId");
        if (runId == null) {
            return java.util.Optional.empty();
        }
        if (request.isKnownSubmission(runId)) {
            return java.util.Optional.empty();
        }
        SourceFetchResult sourceFetchResult = fetchSource(runId, request);

        return java.util.Optional.of(new CrawledSubmission(
                runId,
                firstText(row, "probNum", "problem", "problemId"),
                firstText(row, "title", "problemTitle", "probNum"),
                firstText(row, "contestId", "contestNum"),
                firstText(row, "language", "lang", "languageCanonical"),
                normalizeVerdict(firstText(row, "status", "result")),
                localDateTimeFromEpoch(firstText(row, "time", "submittedAt", "submitTime")),
                integerFromText(firstText(row, "runtime", "timeConsumedMillis", "executionTimeMs")),
                longFromText(firstText(row, "memory", "memoryBytes", "memoryKb")),
                null,
                firstText(row, "oj", "remoteOj", "sourceOj"),
                solutionUrl(runId),
                sourceFetchResult.availability(),
                sourceFetchResult.origin(),
                sourceFetchResult.sourceCode(),
                sourceFetchResult.unavailableReason()
        ));
    }

    private SourceFetchResult fetchSource(String runId, CrawlRequest request) {
        if (runId == null || runId.isBlank()) {
            return SourceFetchResult.unavailable("VJudge run id is missing.");
        }
        return sourceFetcher.fetchSource(URI.create(solutionUrl(runId)), request.requestTimeout());
    }

    private String solutionUrl(String runId) {
        return BASE_URL + "/solution/" + runId;
    }

    private String sourceWarning(List<CrawledSubmission> submissions) {
        if (submissions.isEmpty()) {
            return SOURCE_NOT_AVAILABLE_REASON;
        }
        long available = submissions.stream()
                .filter(submission -> submission.sourceAvailability() == SourceAvailability.AVAILABLE)
                .count();
        long unavailable = submissions.size() - available;
        return "Fetched source code for " + available + " VJudge submissions; "
                + unavailable + " submissions remain unavailable due to permissions, page format, OCR config, or Chrome session state.";
    }

    private JsonNode parseJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException ex) {
            throw new CrawlException("Cannot parse VJudge status response. No scraping/login bypass was attempted.", ex);
        }
    }

    private String textAt(JsonNode row, int index) {
        JsonNode value = row.path(index);
        return textOrNull(value);
    }

    private String firstText(JsonNode row, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = textOrNull(row.path(fieldName));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Integer integerFromText(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return null;
        }
        return Integer.parseInt(digits);
    }

    private Long longFromText(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return null;
        }
        return Long.parseLong(digits);
    }

    private LocalDateTime localDateTimeFromEpoch(String value) {
        Long epochValue = longFromText(value);
        if (epochValue == null || epochValue <= 0) {
            return null;
        }
        long seconds = epochValue > 10_000_000_000L ? epochValue / 1000L : epochValue;
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneOffset.UTC);
    }

    private String normalizeVerdict(String verdict) {
        if (verdict == null) {
            return null;
        }
        return verdict.replaceAll("<[^>]+>", "").trim();
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value.trim();
    }
}
