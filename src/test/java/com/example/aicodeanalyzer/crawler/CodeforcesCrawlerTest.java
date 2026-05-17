package com.example.aicodeanalyzer.crawler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeforcesCrawlerTest {

    @Test
    void crawlParsesCodeforcesUserStatusAndMarksSourceUnavailable() {
        MockHttpClient httpClient = new MockHttpClient();
        httpClient.enqueue(200, """
                {
                  "status": "OK",
                  "result": [
                    {
                      "id": 245123456,
                      "contestId": 1703,
                      "creationTimeSeconds": 1714521600,
                      "programmingLanguage": "GNU C++17",
                      "verdict": "OK",
                      "timeConsumedMillis": 46,
                      "memoryConsumedBytes": 102400,
                      "problem": {
                        "contestId": 1703,
                        "index": "A",
                        "name": "YES or YES?",
                        "rating": 800,
                        "tags": ["implementation", "strings"]
                      }
                    }
                  ]
                }
                """);

        CodeforcesCrawler crawler = new CodeforcesCrawler(
                httpClient,
                new CrawlerRateLimiter(Duration.ZERO),
                new ObjectMapper(),
                3
        );

        CrawlResult result = crawler.crawl(CrawlRequest.publicOnly("tourist", 10));

        assertEquals("CODEFORCES", result.platformCode());
        assertEquals("tourist", result.handle());
        assertEquals(1, result.submissions().size());
        assertEquals(1, result.unavailableSourceCount());
        assertTrue(result.warnings().getFirst().contains("0 Codeforces submissions"));

        CrawledSubmission submission = result.submissions().getFirst();
        assertEquals("245123456", submission.platformSubmissionId());
        assertEquals("1703A", submission.problemCode());
        assertEquals("YES or YES?", submission.problemName());
        assertEquals("1703", submission.contestId());
        assertEquals("GNU C++17", submission.language());
        assertEquals("OK", submission.verdict());
        assertEquals(LocalDateTime.of(2024, 5, 1, 0, 0), submission.submittedAt());
        assertEquals(46, submission.executionTimeMs());
        assertEquals(102400L, submission.memoryBytes());
        assertEquals(800, submission.problemRating());
        assertEquals("implementation,strings", submission.problemTags());
        assertEquals("https://codeforces.com/contest/1703/submission/245123456", submission.sourceUrl());
        assertEquals(SourceAvailability.SOURCE_NOT_AVAILABLE, submission.sourceAvailability());
        assertNull(submission.sourceCode());

        assertEquals(1, httpClient.requests.size());
        URI requestedUri = httpClient.requests.peek().uri();
        assertEquals("https://codeforces.com/api/user.status?handle=tourist&from=1&count=10", requestedUri.toString());
    }

    @Test
    void crawlUnlimitedCodeforcesUserStatusOmitsCountParameter() {
        MockHttpClient httpClient = new MockHttpClient();
        httpClient.enqueue(200, """
                {
                  "status": "OK",
                  "result": []
                }
                """);

        CodeforcesCrawler crawler = new CodeforcesCrawler(
                httpClient,
                new CrawlerRateLimiter(Duration.ZERO),
                new ObjectMapper(),
                3
        );

        CrawlResult result = crawler.crawl(CrawlRequest.publicOnly("tourist", 0));

        assertTrue(result.submissions().isEmpty());
        assertEquals(1, httpClient.requests.size());
        URI requestedUri = httpClient.requests.peek().uri();
        assertEquals("https://codeforces.com/api/user.status?handle=tourist", requestedUri.toString());
    }

    @Test
    void crawlStoresFetchedSourceWhenSourceFetcherReturnsCode() {
        MockHttpClient httpClient = new MockHttpClient();
        httpClient.enqueue(200, """
                {
                  "status": "OK",
                  "result": [
                    {
                      "id": 245123456,
                      "creationTimeSeconds": 1714521600,
                      "programmingLanguage": "GNU C++17",
                      "verdict": "OK",
                      "problem": {
                        "contestId": 1703,
                        "index": "A",
                        "name": "YES or YES?"
                      }
                    }
                  ]
                }
                """);

        CodeforcesCrawler crawler = new CodeforcesCrawler(
                httpClient,
                new CrawlerRateLimiter(Duration.ZERO),
                new ObjectMapper(),
                3,
                (sourceUri, timeout) -> SourceFetchResult.available("#include <bits/stdc++.h>\nint main(){return 0;}")
        );

        CrawlResult result = crawler.crawl(CrawlRequest.publicOnly("tourist", 10));

        assertEquals(0, result.unavailableSourceCount());
        assertTrue(result.warnings().getFirst().contains("Fetched source code for 1"));
        CrawledSubmission submission = result.submissions().getFirst();
        assertEquals(SourceAvailability.AVAILABLE, submission.sourceAvailability());
        assertTrue(submission.sourceCode().contains("int main"));
    }

    @Test
    void crawlRetriesTemporaryHttpFailuresUpToSuccess() {
        MockHttpClient httpClient = new MockHttpClient();
        httpClient.enqueue(500, "temporary");
        httpClient.enqueue(429, "rate limited");
        httpClient.enqueue(200, """
                {
                  "status": "OK",
                  "result": []
                }
                """);

        CodeforcesCrawler crawler = new CodeforcesCrawler(
                httpClient,
                new CrawlerRateLimiter(Duration.ZERO),
                new ObjectMapper(),
                3
        );

        CrawlResult result = crawler.crawl(CrawlRequest.publicOnly("retry_user", 5));

        assertEquals(3, httpClient.requests.size());
        assertEquals(0, result.submissions().size());
        assertTrue(result.warnings().getFirst().contains("No public submissions"));
    }

    @Test
    void crawlThrowsWhenCodeforcesApiRejectsHandle() {
        MockHttpClient httpClient = new MockHttpClient();
        httpClient.enqueue(200, """
                {
                  "status": "FAILED",
                  "comment": "handle: User with handle unknown_user not found"
                }
                """);

        CodeforcesCrawler crawler = new CodeforcesCrawler(
                httpClient,
                new CrawlerRateLimiter(Duration.ZERO),
                new ObjectMapper(),
                3
        );

        CrawlException exception = assertThrows(
                CrawlException.class,
                () -> crawler.crawl(CrawlRequest.publicOnly("unknown_user", 10))
        );

        assertEquals(1, httpClient.requests.size());
        assertTrue(exception.getMessage().contains("Codeforces API rejected request"));
        assertTrue(exception.getMessage().contains("unknown_user"));
    }

    @Test
    void crawlThrowsAfterRetryableHttpFailuresAreExhausted() {
        MockHttpClient httpClient = new MockHttpClient();
        httpClient.enqueue(500, "temporary");
        httpClient.enqueue(500, "temporary again");

        CodeforcesCrawler crawler = new CodeforcesCrawler(
                httpClient,
                new CrawlerRateLimiter(Duration.ZERO),
                new ObjectMapper(),
                2
        );

        CrawlException exception = assertThrows(
                CrawlException.class,
                () -> crawler.crawl(CrawlRequest.publicOnly("retry_exhausted_user", 5))
        );

        assertEquals(2, httpClient.requests.size());
        assertTrue(exception.getMessage().contains("HTTP 500"));
    }

    private static final class MockHttpClient extends HttpClient {
        private final Queue<MockHttpResponse> responses = new ArrayDeque<>();
        private final Queue<HttpRequest> requests = new ArrayDeque<>();

        void enqueue(int statusCode, String body) {
            responses.add(new MockHttpResponse(statusCode, body));
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NORMAL;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            try {
                return SSLContext.getDefault();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_2;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException {
            requests.add(request);
            MockHttpResponse response = responses.poll();
            if (response == null) {
                throw new IOException("No mock response queued.");
            }
            return (HttpResponse<T>) response.withRequest(request);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler
        ) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("sendAsync is not used in tests."));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException("sendAsync is not used in tests."));
        }
    }

    private static final class MockHttpResponse implements HttpResponse<String> {
        private final int statusCode;
        private final String body;
        private HttpRequest request;

        MockHttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        MockHttpResponse withRequest(HttpRequest request) {
            this.request = request;
            return this;
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Map.of(), (name, value) -> true);
        }

        @Override
        public String body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_2;
        }
    }
}
