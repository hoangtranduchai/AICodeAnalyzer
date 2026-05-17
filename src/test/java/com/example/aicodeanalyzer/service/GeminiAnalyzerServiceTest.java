package com.example.aicodeanalyzer.service;

import com.example.aicodeanalyzer.exception.AnalyzerException;
import com.example.aicodeanalyzer.exception.AiRateLimitException;
import com.example.aicodeanalyzer.model.AiAnalysisResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiAnalyzerServiceTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SOURCE = """
            #include <bits/stdc++.h>
            using namespace std;

            int main() {
                vector<int> a = {3, 1, 2};
                sort(a.begin(), a.end());
                cout << a[0] << '\\n';
            }
            """;

    @Test
    void analyzeCallsGeminiRestAndParsesThreeKeyJson() throws Exception {
        MockHttpClient httpClient = new MockHttpClient();
        httpClient.enqueueResponse(200, geminiResponse("""
                {
                  "data_structures": ["vector", "array"],
                  "algorithms": ["sorting"],
                  "ai_generated_probability": 42
                }
                """));
        GeminiAnalyzerService service = service(httpClient);

        GeminiAnalyzerService.GeminiAnalysisResult result = service.analyze(SOURCE);

        assertEquals(List.of("vector", "array"), result.dataStructures());
        assertEquals(List.of("sorting"), result.algorithms());
        assertEquals(42, result.aiGeneratedProbability());

        HttpRequest request = httpClient.requests.peek();
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-test:generateContent",
                request.uri().toString());
        assertEquals("gemini-test-key", request.headers().firstValue("x-goog-api-key").orElse(""));
        assertFalse(request.headers().firstValue("Authorization").isPresent());
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElse(""));

        String requestBody = readBody(request);
        assertTrue(requestBody.contains("\"responseMimeType\":\"application/json\""));
        assertTrue(requestBody.contains("\"responseSchema\""));
        assertTrue(requestBody.contains("\"data_structures\""));
        assertTrue(requestBody.contains("\"algorithms\""));
        assertTrue(requestBody.contains("\"ai_generated_probability\""));
        assertTrue(requestBody.contains("sort(a.begin(), a.end())"));
        assertFalse(requestBody.contains("gemini-test-key"));
    }

    @Test
    void analyzeForSubmissionMapsGeminiResultToDatabaseModel() throws Exception {
        MockHttpClient httpClient = new MockHttpClient();
        httpClient.enqueueResponse(200, geminiResponse("""
                {
                  "data_structures": ["vector"],
                  "algorithms": ["sorting"],
                  "ai_generated_probability": 72
                }
                """));
        GeminiAnalyzerService service = service(httpClient);

        AiAnalysisResult result = service.analyzeForSubmission(99L, SOURCE);

        assertEquals(99L, result.getSubmissionId());
        assertEquals("GEMINI", result.getAnalyzerType());
        assertEquals("gemini-test", result.getModelName());
        assertEquals("vector", result.getDataStructures());
        assertEquals("sorting", result.getAlgorithms());
        assertEquals("72", result.getAiRiskScore().toPlainString());
        assertEquals("HIGH", result.getAiRiskLevel());
        assertTrue(result.getRawResponse().contains("candidates"));
        assertTrue(result.getPromptHash().matches("[0-9a-f]{64}"));
    }

    @Test
    void analyzeRejectsInvalidProviderJson() throws Exception {
        MockHttpClient httpClient = new MockHttpClient();
        httpClient.enqueueResponse(200, geminiResponse("""
                {
                  "data_structures": ["vector"],
                  "ai_generated_probability": 20
                }
                """));
        GeminiAnalyzerService service = service(httpClient);

        AnalyzerException exception = assertThrows(AnalyzerException.class, () -> service.analyze(SOURCE));

        assertTrue(exception.getMessage().contains("algorithms"));
    }

    @Test
    void analyzeThrowsOnGeminiHttpErrorWithoutLeakingApiKey() {
        MockHttpClient httpClient = new MockHttpClient();
        httpClient.enqueueResponse(403, """
                {
                  "error": {
                    "message": "API key not valid"
                  }
                }
                """);
        GeminiAnalyzerService service = service(httpClient);

        AnalyzerException exception = assertThrows(AnalyzerException.class, () -> service.analyze(SOURCE));

        assertTrue(exception.getMessage().contains("HTTP 403"));
        assertFalse(exception.getMessage().contains("gemini-test-key"));
    }

    @Test
    void analyzeThrowsTypedRateLimitExceptionForGeminiQuotaErrors() {
        MockHttpClient httpClient = new MockHttpClient();
        httpClient.enqueueResponse(429, """
                {
                  "error": {
                    "code": 429,
                    "message": "Quota exceeded. Please retry in 25.5s.",
                    "status": "RESOURCE_EXHAUSTED"
                  }
                }
                """);
        GeminiAnalyzerService service = service(httpClient);

        AiRateLimitException exception = assertThrows(AiRateLimitException.class, () -> service.analyze(SOURCE));

        assertEquals(429, exception.statusCode());
        assertEquals(Duration.ofMillis(25_500), exception.retryAfter());
        assertTrue(exception.getMessage().contains("quota/rate limit"));
        assertFalse(exception.getMessage().contains("gemini-test-key"));
    }

    private static GeminiAnalyzerService service(MockHttpClient httpClient) {
        return new GeminiAnalyzerService(
                "gemini-test-key",
                "gemini-test",
                URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-test:generateContent"),
                Duration.ofSeconds(2),
                httpClient,
                OBJECT_MAPPER
        );
    }

    private static String geminiResponse(String analysisJson) throws Exception {
        return """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": %s
                          }
                        ],
                        "role": "model"
                      },
                      "finishReason": "STOP",
                      "index": 0
                    }
                  ]
                }
                """.formatted(OBJECT_MAPPER.writeValueAsString(analysisJson));
    }

    private static String readBody(HttpRequest request) throws Exception {
        HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
        CapturingSubscriber subscriber = new CapturingSubscriber();
        publisher.subscribe(subscriber);
        return subscriber.body();
    }

    private static final class CapturingSubscriber implements Flow.Subscriber<ByteBuffer> {
        private final CountDownLatch finished = new CountDownLatch(1);
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        private Throwable failure;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer item) {
            byte[] bytes = new byte[item.remaining()];
            item.get(bytes);
            outputStream.writeBytes(bytes);
        }

        @Override
        public void onError(Throwable throwable) {
            failure = throwable;
            finished.countDown();
        }

        @Override
        public void onComplete() {
            finished.countDown();
        }

        String body() throws Exception {
            if (!finished.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out while reading request body.");
            }
            if (failure != null) {
                throw new AssertionError("Cannot read request body.", failure);
            }
            return outputStream.toString(StandardCharsets.UTF_8);
        }
    }

    private static final class MockHttpClient extends HttpClient {
        private final Queue<HttpResponse<String>> responses = new ArrayDeque<>();
        private final Queue<HttpRequest> requests = new ArrayDeque<>();

        void enqueueResponse(int statusCode, String body) {
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
            HttpResponse<String> response = responses.poll();
            if (response == null) {
                throw new IOException("No mock response queued.");
            }
            return (HttpResponse<T>) ((MockHttpResponse) response).withRequest(request);
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
            return HttpHeaders.of(Map.of(), (name, value) -> true);
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
