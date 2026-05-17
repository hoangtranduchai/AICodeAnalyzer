package com.example.aicodeanalyzer.service;

import com.example.aicodeanalyzer.analyzer.AnalysisPromptBuilder;
import com.example.aicodeanalyzer.config.AiConfig;
import com.example.aicodeanalyzer.exception.AnalyzerException;
import com.example.aicodeanalyzer.model.AiAnalysisResult;
import com.example.aicodeanalyzer.model.SourceCodeDetail;
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
import java.time.LocalDateTime;
import java.util.ArrayDeque;
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

class OpenAIAnalyzerServiceTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void analyzeUsesMockModeWhenApiKeyIsMissing() {
        MockHttpClient httpClient = new MockHttpClient();
        OpenAIAnalyzerService service = new OpenAIAnalyzerService(
                config("", false, 3),
                httpClient,
                OBJECT_MAPPER,
                new AnalysisPromptBuilder(),
                Duration.ZERO
        );

        AiAnalysisResult result = service.analyze(sourceDetail());

        assertEquals("OPENAI", result.getAnalyzerType());
        assertEquals("mock-gpt-test", result.getModelName());
        assertTrue(result.getRawResponse().contains("\"mock\":true"));
        assertTrue(result.getSummary().contains("mock mode"));
        assertEquals(0, httpClient.requests.size());
    }

    @Test
    void analyzeSendsStructuredOutputRequestAndParsesResponse() throws Exception {
        MockHttpClient httpClient = new MockHttpClient();
        httpClient.enqueueResponse(200, openAiResponse(analysisJson()));

        OpenAIAnalyzerService service = new OpenAIAnalyzerService(
                config("sk-test-key", false, 3),
                httpClient,
                OBJECT_MAPPER,
                new AnalysisPromptBuilder(),
                Duration.ZERO
        );

        AiAnalysisResult result = service.analyze(sourceDetail());

        assertEquals("OPENAI", result.getAnalyzerType());
        assertEquals("gpt-test", result.getModelName());
        assertEquals("vector", result.getDataStructures());
        assertEquals("sorting, binary_search", result.getAlgorithms());
        assertEquals("time=O(n log n); space=O(n); level=intermediate; algorithm_score=78; ds_score=72; confidence=84",
                result.getComplexityEstimate());
        assertEquals("82", result.getCodeQualityScore().toPlainString());
        assertEquals("35", result.getAiRiskScore().toPlainString());
        assertEquals("LOW", result.getAiRiskLevel());
        assertTrue(result.getSummary().contains("Mã nguồn sử dụng"));

        HttpRequest request = httpClient.requests.peek();
        assertEquals("https://api.openai.com/v1/responses", request.uri().toString());
        assertEquals("Bearer sk-test-key", request.headers().firstValue("Authorization").orElse(""));
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElse(""));

        String requestBody = readBody(request);
        assertTrue(requestBody.contains("\"type\":\"json_schema\""));
        assertTrue(requestBody.contains("\"name\":\"ai_code_analysis_result\""));
        assertTrue(requestBody.contains("sort(a.begin(), a.end())"));
        assertFalse(requestBody.contains("sk-test-key"));
    }

    @Test
    void analyzeSupportsGeminiGenerateContentRequestAndResponse() throws Exception {
        MockHttpClient httpClient = new MockHttpClient();
        httpClient.enqueueResponse(200, geminiResponse(analysisJson()));

        OpenAIAnalyzerService service = new OpenAIAnalyzerService(
                geminiConfig("gemini-test-key", false, 3),
                httpClient,
                OBJECT_MAPPER,
                new AnalysisPromptBuilder(),
                Duration.ZERO
        );

        AiAnalysisResult result = service.analyze(sourceDetail());

        assertEquals("GEMINI", result.getAnalyzerType());
        assertEquals("gemini-test", result.getModelName());
        assertEquals("vector", result.getDataStructures());
        assertEquals("sorting, binary_search", result.getAlgorithms());

        HttpRequest request = httpClient.requests.peek();
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-test:generateContent",
                request.uri().toString());
        assertEquals("gemini-test-key", request.headers().firstValue("x-goog-api-key").orElse(""));
        assertFalse(request.headers().firstValue("Authorization").isPresent());
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElse(""));

        String requestBody = readBody(request);
        assertTrue(requestBody.contains("\"systemInstruction\""));
        assertTrue(requestBody.contains("\"contents\""));
        assertTrue(requestBody.contains("\"responseMimeType\":\"application/json\""));
        assertTrue(requestBody.contains("sort(a.begin(), a.end())"));
        assertFalse(requestBody.contains("gemini-test-key"));
    }

    @Test
    void analyzeRetriesNetworkFailureThenSucceeds() throws Exception {
        MockHttpClient httpClient = new MockHttpClient();
        httpClient.enqueueFailure(new IOException("temporary network issue"));
        httpClient.enqueueResponse(200, openAiResponse(analysisJson()));

        OpenAIAnalyzerService service = new OpenAIAnalyzerService(
                config("sk-test-key", false, 3),
                httpClient,
                OBJECT_MAPPER,
                new AnalysisPromptBuilder(),
                Duration.ZERO
        );

        AiAnalysisResult result = service.analyze(sourceDetail());

        assertEquals("OPENAI", result.getAnalyzerType());
        assertEquals(2, httpClient.requests.size());
    }

    @Test
    void analyzeThrowsOnNonRetryableApiErrorWithoutLeakingApiKey() {
        MockHttpClient httpClient = new MockHttpClient();
        httpClient.enqueueResponse(401, """
                {
                  "error": {
                    "message": "invalid api key"
                  }
                }
                """);

        OpenAIAnalyzerService service = new OpenAIAnalyzerService(
                config("sk-secret-should-not-appear", false, 3),
                httpClient,
                OBJECT_MAPPER,
                new AnalysisPromptBuilder(),
                Duration.ZERO
        );

        AnalyzerException exception = assertThrows(AnalyzerException.class, () -> service.analyze(sourceDetail()));

        assertEquals(1, httpClient.requests.size());
        assertTrue(exception.getMessage().contains("HTTP 401"));
        assertFalse(exception.getMessage().contains("sk-secret-should-not-appear"));
    }

    @Test
    void analyzeThrowsOnGeminiApiErrorWithoutLeakingApiKey() {
        MockHttpClient httpClient = new MockHttpClient();
        httpClient.enqueueResponse(403, """
                {
                  "error": {
                    "code": 403,
                    "message": "API key not valid"
                  }
                }
                """);

        OpenAIAnalyzerService service = new OpenAIAnalyzerService(
                geminiConfig("AIzaSy_SECRET_SHOULD_NOT_LEAK", false, 3),
                httpClient,
                OBJECT_MAPPER,
                new AnalysisPromptBuilder(),
                Duration.ZERO
        );

        AnalyzerException exception = assertThrows(AnalyzerException.class, () -> service.analyze(sourceDetail()));

        assertEquals(1, httpClient.requests.size());
        assertTrue(exception.getMessage().contains("HTTP 403"));
        assertFalse(exception.getMessage().contains("AIzaSy_SECRET_SHOULD_NOT_LEAK"));
    }

    @Test
    void analyzeThrowsWhenStructuredResponseMissesRequiredFields() throws Exception {
        MockHttpClient httpClient = new MockHttpClient();
        httpClient.enqueueResponse(200, openAiResponse("""
                {
                  "language": "cpp",
                  "algorithms": [],
                  "data_structures": [],
                  "complexity_time": "unknown",
                  "complexity_space": "unknown",
                  "problem_solving_level": "beginner",
                  "code_quality_score": 30,
                  "algorithm_score": 20,
                  "ds_score": 10,
                  "ai_generated_probability": 5,
                  "ai_usage_evidence": [],
                  "warnings": [],
                  "confidence": 20
                }
                """));

        OpenAIAnalyzerService service = new OpenAIAnalyzerService(
                config("sk-test-key", false, 3),
                httpClient,
                OBJECT_MAPPER,
                new AnalysisPromptBuilder(),
                Duration.ZERO
        );

        AnalyzerException exception = assertThrows(AnalyzerException.class, () -> service.analyze(sourceDetail()));

        assertEquals(1, httpClient.requests.size());
        assertTrue(exception.getMessage().contains("explanation_vi"));
    }

    private static AiConfig config(String apiKey, boolean mockMode, int maxRetries) {
        return new AiConfig(
                "openai-rest",
                "OPENAI_API_KEY",
                apiKey,
                "gpt-test",
                URI.create("https://api.openai.com/v1/responses"),
                Duration.ofSeconds(2),
                maxRetries,
                mockMode
        );
    }

    private static AiConfig geminiConfig(String apiKey, boolean mockMode, int maxRetries) {
        return new AiConfig(
                "gemini-rest",
                "GEMINI_API_KEY",
                apiKey,
                "gemini-test",
                URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-test:generateContent"),
                Duration.ofSeconds(2),
                maxRetries,
                mockMode
        );
    }

    private static SourceCodeDetail sourceDetail() {
        return new SourceCodeDetail(
                1L,
                10L,
                "CODEFORCES",
                "Codeforces",
                "tourist",
                "123456",
                "1703A",
                "YES or YES?",
                "GNU C++17",
                "OK",
                LocalDateTime.of(2026, 5, 13, 10, 30),
                "CRAWLED",
                null,
                """
                        #include <bits/stdc++.h>
                        using namespace std;

                        int main() {
                            int n;
                            cin >> n;
                            vector<int> a(n);
                            for (int &x : a) cin >> x;
                            sort(a.begin(), a.end());
                            cout << a[n / 2] << '\\n';
                            return 0;
                        }
                        """,
                "hash",
                12,
                252,
                LocalDateTime.of(2026, 5, 13, 10, 31),
                null,
                null
        );
    }

    private static String analysisJson() {
        return """
                {
                  "language": "cpp",
                  "algorithms": ["sorting", "binary_search"],
                  "data_structures": ["vector"],
                  "complexity_time": "O(n log n)",
                  "complexity_space": "O(n)",
                  "problem_solving_level": "intermediate",
                  "code_quality_score": 82,
                  "algorithm_score": 78,
                  "ds_score": 72,
                  "ai_generated_probability": 35,
                  "ai_usage_evidence": ["template_like_code", "no_clear_evidence"],
                  "explanation_vi": "Mã nguồn sử dụng sắp xếp và tìm kiếm nhị phân đúng cách.",
                  "warnings": ["Đây chỉ là xác suất tham khảo."],
                  "confidence": 84
                }
                """;
    }

    private static String openAiResponse(String analysisJson) throws Exception {
        return """
                {
                  "id": "resp_test",
                  "output": [
                    {
                      "type": "message",
                      "role": "assistant",
                      "content": [
                        {
                          "type": "output_text",
                          "text": %s
                        }
                      ]
                    }
                  ]
                }
                """.formatted(OBJECT_MAPPER.writeValueAsString(analysisJson));
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
        private final Queue<Object> responses = new ArrayDeque<>();
        private final Queue<HttpRequest> requests = new ArrayDeque<>();

        void enqueueResponse(int statusCode, String body) {
            responses.add(new MockHttpResponse(statusCode, body));
        }

        void enqueueFailure(IOException exception) {
            responses.add(exception);
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
            Object response = responses.poll();
            if (response == null) {
                throw new IOException("No mock response queued.");
            }
            if (response instanceof IOException exception) {
                throw exception;
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
