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
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VJudgeCrawlerTest {

    @Test
    void crawlUnlimitedUsesPositivePageSizeBecauseVjudgeReturnsEmptyDataForNegativeLength() {
        MockHttpClient httpClient = new MockHttpClient();
        httpClient.enqueue(200, """
                {
                  "data": [
                    {
                      "runId": 68653699,
                      "probNum": "596A",
                      "language": "GNU G++23 14.2",
                      "status": "Accepted",
                      "runtime": 46,
                      "memory": 100,
                      "time": 1774158334000,
                      "oj": "CodeForces"
                    }
                  ]
                }
                """);

        VJudgeCrawler crawler = new VJudgeCrawler(
                httpClient,
                new CrawlerRateLimiter(Duration.ZERO),
                new ObjectMapper(),
                1
        );

        CrawlResult result = crawler.crawl(CrawlRequest.publicOnly("hoangtranduchai", CrawlRequest.UNLIMITED_SUBMISSIONS));

        assertEquals(1, result.submissions().size());
        assertEquals("https://vjudge.net/status/data?draw=1&start=0&length=100&un=hoangtranduchai",
                httpClient.requests.peek().uri().toString());

        CrawledSubmission submission = result.submissions().getFirst();
        assertEquals("68653699", submission.platformSubmissionId());
        assertEquals("596A", submission.problemCode());
        assertEquals("GNU G++23 14.2", submission.language());
        assertEquals("Accepted", submission.verdict());
        assertEquals(46, submission.executionTimeMs());
        assertEquals(100L, submission.memoryBytes());
        assertEquals(LocalDateTime.of(2026, 3, 22, 5, 45, 34), submission.submittedAt());
        assertEquals("CodeForces", submission.problemTags());
        assertTrue(result.warnings().getFirst().contains("Fetched source code for 0 VJudge submissions"));
    }

    @Test
    void crawlBoundedUsesRequestedLength() {
        MockHttpClient httpClient = new MockHttpClient();
        httpClient.enqueue(200, """
                {
                  "data": []
                }
                """);

        VJudgeCrawler crawler = new VJudgeCrawler(
                httpClient,
                new CrawlerRateLimiter(Duration.ZERO),
                new ObjectMapper(),
                1
        );

        crawler.crawl(CrawlRequest.publicOnly("student", 20));

        assertEquals("https://vjudge.net/status/data?draw=1&start=0&length=20&un=student",
                httpClient.requests.peek().uri().toString());
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
