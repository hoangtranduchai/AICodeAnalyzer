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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Fetches Codeforces submission metadata through official endpoints and reads source code from web pages.
 */
public class CodeforcesCrawler extends AbstractHttpCrawler {
    private static final String PLATFORM_CODE = "CODEFORCES";
    private static final String API_URL = "https://codeforces.com/api/user.status";
        private static final String SOURCE_NOT_AVAILABLE_REASON =
            "Codeforces source page was unavailable via the authorized browser session.";

    private final ObjectMapper objectMapper;
    private final SourceFetcher sourceFetcher;
    private final Optional<CodeforcesApiAuth> apiAuth;

    public CodeforcesCrawler() {
        this(
                HttpClient.newHttpClient(),
                CrawlerRateLimiter.politeDefault(),
                new ObjectMapper(),
                3,
                DefaultSourceFetchers.authorizedBrowserThenHttp()
        );
    }

    public CodeforcesCrawler(
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

    public CodeforcesCrawler(
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
        this.apiAuth = CodeforcesApiAuth.load();
    }

    @Override
    public String platformCode() {
        return PLATFORM_CODE;
    }

    @Override
    public CrawlResult crawl(CrawlRequest request) {
        JsonNode root = fetchUserStatus(request);
        String status = root.path("status").asText();
        if (!"OK".equalsIgnoreCase(status)) {
            String comment = root.path("comment").asText("Unknown Codeforces API error.");
            throw new CrawlException("Codeforces API rejected request: " + comment);
        }

        JsonNode result = root.path("result");
        int returnedCount = result.isArray() ? result.size() : 0;
        List<CrawledSubmission> submissions = new ArrayList<>();
        for (JsonNode node : result) {
            CrawledSubmission submission = mapSubmission(node, request);
            if (submission != null) {
                submissions.add(submission);
            }
        }

        List<String> warnings = submissions.isEmpty()
                ? List.of(returnedCount == 0
                        ? "No public submissions returned for handle " + request.handle() + "."
                        : "No new Codeforces submissions returned for handle " + request.handle() + ".")
                : sourceWarnings(submissions);

        return new CrawlResult(PLATFORM_CODE, request.handle(), submissions, warnings, LocalDateTime.now());
    }

    private URI buildUserStatusUri(CrawlRequest request) {
        String encodedHandle = URLEncoder.encode(request.handle(), StandardCharsets.UTF_8);
        String uri = API_URL + "?handle=" + encodedHandle
                + (request.isUnlimited() ? "" : "&from=1&count=" + request.maxSubmissions());
        return URI.create(uri);
    }

    private JsonNode fetchUserStatus(CrawlRequest request) {
        if (apiAuth.isPresent()) {
            Map<String, String> parameters = request.isUnlimited()
                    ? Map.of(
                            "handle", request.handle(),
                            "includeSources", "true"
                    )
                    : Map.of(
                            "handle", request.handle(),
                            "from", "1",
                            "count", String.valueOf(request.maxSubmissions()),
                            "includeSources", "true"
                    );
            URI signedUri = apiAuth.get().signedUri("user.status", parameters);
            JsonNode signedRoot = parseJson(getText(signedUri, request.requestTimeout()));
            if ("OK".equalsIgnoreCase(signedRoot.path("status").asText())) {
                return signedRoot;
            }
        }
        return parseJson(getText(buildUserStatusUri(request), request.requestTimeout()));
    }

    private CrawledSubmission mapSubmission(JsonNode node, CrawlRequest request) {
        JsonNode problem = node.path("problem");
        String contestId = textOrNull(problem.path("contestId"));
        String problemIndex = textOrNull(problem.path("index"));
        String platformSubmissionId = textOrNull(node.path("id"));
        if (request.isKnownSubmission(platformSubmissionId)) {
            return null;
        }
        List<String> sourceUrls = sourceUrls(contestId, platformSubmissionId, problem);
        String sourceUrl = sourceUrls.isEmpty() ? null : sourceUrls.getFirst();
        SourceFetchResult sourceFetchResult = fetchSource(node, sourceUrls, request);

        return new CrawledSubmission(
                platformSubmissionId,
                problemCode(contestId, problemIndex),
                textOrNull(problem.path("name")),
                contestId,
                textOrNull(node.path("programmingLanguage")),
                textOrNull(node.path("verdict")),
                submittedAt(node.path("creationTimeSeconds").asLong(0)),
                integerOrNull(node.path("timeConsumedMillis")),
                longOrNull(node.path("memoryConsumedBytes")),
                integerOrNull(problem.path("rating")),
                tags(problem.path("tags")),
                sourceUrl,
                sourceFetchResult.availability(),
                sourceFetchResult.origin(),
                sourceFetchResult.sourceCode(),
                sourceFetchResult.unavailableReason()
        );
    }

    private SourceFetchResult fetchSource(JsonNode node, List<String> sourceUrls, CrawlRequest request) {
        if (sourceUrls == null || sourceUrls.isEmpty()) {
            return SourceFetchResult.unavailable("Codeforces source URL is not available for this submission.");
        }
        SourceFetchResult lastResult = SourceFetchResult.unavailable(SOURCE_NOT_AVAILABLE_REASON);
        for (String url : sourceUrls) {
            if (url == null || url.isBlank()) {
                continue;
            }
            SourceFetchResult result = sourceFetcher.fetchSource(URI.create(url), request.requestTimeout());
            if (result.hasSourceCode()) {
                return result;
            }
            lastResult = result;
        }
        return lastResult;
    }

    private List<String> sourceWarnings(List<CrawledSubmission> submissions) {
        long available = submissions.stream()
                .filter(submission -> submission.sourceAvailability() == SourceAvailability.AVAILABLE)
                .count();
        long unavailable = submissions.size() - available;
        if (unavailable == 0) {
            return List.of("Fetched source code for " + available + " Codeforces submissions via direct web pages.");
        }
        return List.of("Fetched source code for " + available + " Codeforces submissions; "
                + unavailable + " submissions remain unavailable due to permissions, page format, or Chrome session state.");
    }

    private JsonNode parseJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException ex) {
            throw new CrawlException("Cannot parse Codeforces API response.", ex);
        }
    }

    private String problemCode(String contestId, String problemIndex) {
        if (contestId == null && problemIndex == null) {
            return null;
        }
        return (contestId == null ? "" : contestId) + (problemIndex == null ? "" : problemIndex);
    }

    private String tags(JsonNode tags) {
        if (!tags.isArray()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (JsonNode tag : tags) {
            values.add(tag.asText());
        }
        return values.stream().collect(Collectors.joining(","));
    }

    private List<String> sourceUrls(String contestId, String platformSubmissionId, JsonNode problem) {
        if (platformSubmissionId == null || platformSubmissionId.isBlank()) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        boolean likelyGym = isGymProblem(problem);
        if (contestId != null && !contestId.isBlank()) {
            if (likelyGym) {
                urls.add(gymUrl(contestId, platformSubmissionId));
                urls.add(contestUrl(contestId, platformSubmissionId));
            } else {
                urls.add(contestUrl(contestId, platformSubmissionId));
                urls.add(gymUrl(contestId, platformSubmissionId));
            }
        }
        urls.add(submissionUrl(platformSubmissionId));
        return urls.stream()
                .filter(url -> url != null && !url.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }

    private boolean isGymProblem(JsonNode problem) {
        String problemsetName = textOrNull(problem.path("problemsetName"));
        return problemsetName != null && problemsetName.toLowerCase().contains("gym");
    }

    private String contestUrl(String contestId, String platformSubmissionId) {
        return "https://codeforces.com/contest/" + contestId + "/submission/" + platformSubmissionId;
    }

    private String gymUrl(String contestId, String platformSubmissionId) {
        return "https://codeforces.com/gym/" + contestId + "/submission/" + platformSubmissionId;
    }

    private String submissionUrl(String platformSubmissionId) {
        return "https://codeforces.com/submission/" + platformSubmissionId;
    }

    private LocalDateTime submittedAt(long epochSeconds) {
        if (epochSeconds <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
    }

    private Integer integerOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asInt();
    }

    private Long longOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asLong();
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }
}
