package com.example.aicodeanalyzer.crawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches permitted VJudge data while respecting access rights, throttling, and platform rules.
 */
public class VJudgeCrawler extends AbstractHttpCrawler {
    private static final String PLATFORM_CODE = "VJUDGE";
    private static final String BASE_URL = "https://vjudge.net";
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

        String body = getText(buildStatusUri(request), request.requestTimeout());
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

        List<CrawledSubmission> submissions = new ArrayList<>();
        int returnedCount = 0;
        for (JsonNode row : data) {
            returnedCount++;
            mapStatusRow(row, request).ifPresent(submissions::add);
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

    private URI buildStatusUri(CrawlRequest request) {
        String encodedHandle = URLEncoder.encode(request.handle(), StandardCharsets.UTF_8);
        String length = request.isUnlimited() ? "-1" : String.valueOf(request.maxSubmissions());
        String uri = BASE_URL + "/status/data?draw=1&start=0&length=" + length
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
                firstText(row, "contestId"),
                firstText(row, "language", "lang"),
                normalizeVerdict(firstText(row, "status", "result")),
                null,
                integerFromText(firstText(row, "time", "runtime")),
                null,
                null,
                firstText(row, "oj", "remoteOj"),
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
