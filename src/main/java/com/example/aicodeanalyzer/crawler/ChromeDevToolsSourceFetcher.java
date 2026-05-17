package com.example.aicodeanalyzer.crawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class ChromeDevToolsSourceFetcher implements SourceFetcher {
    private static final URI DEFAULT_DEVTOOLS_BASE = URI.create("http://localhost:9222");
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);

    private final URI devToolsBaseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final JsoupSourceCodeExtractor extractor;
    private final GeminiImageOcrClient imageOcrClient;

    ChromeDevToolsSourceFetcher() {
        this(devToolsBaseUri(), HttpClient.newHttpClient(), new ObjectMapper(),
                new JsoupSourceCodeExtractor(), new GeminiImageOcrClient());
    }

    ChromeDevToolsSourceFetcher(
            URI devToolsBaseUri,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            JsoupSourceCodeExtractor extractor,
            GeminiImageOcrClient imageOcrClient
    ) {
        this.devToolsBaseUri = devToolsBaseUri == null ? DEFAULT_DEVTOOLS_BASE : devToolsBaseUri;
        this.httpClient = httpClient == null ? HttpClient.newHttpClient() : httpClient;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.extractor = extractor == null ? new JsoupSourceCodeExtractor() : extractor;
        this.imageOcrClient = imageOcrClient == null ? new GeminiImageOcrClient() : imageOcrClient;
    }

    @Override
    public SourceFetchResult fetchSource(URI sourceUri, Duration timeout) {
        Duration effectiveTimeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        ChromeTab tab = null;
        Optional<SourceFetchResult> browserContextResult = Optional.empty();
        try {
            if (!isDevToolsAvailable(effectiveTimeout)) {
                return SourceFetchResult.unavailable("Chrome DevTools is not available at " + devToolsBaseUri + ".");
            }
            browserContextResult = fetchThroughExistingBrowserContext(sourceUri, effectiveTimeout);
            if (browserContextResult.isPresent()) {
                SourceFetchResult result = browserContextResult.get();
                if (result.hasSourceCode()) {
                    return result;
                }
            }
            tab = openTab(sourceUri, effectiveTimeout);
            try (DevToolsSession session = connect(tab.webSocketDebuggerUrl(), effectiveTimeout)) {
                waitForReadyState(session, effectiveTimeout);
                String html = session.evaluateString("document.documentElement.outerHTML", effectiveTimeout);
                Optional<String> source = extractor.extractTextSource(html, sourceUri);
                if (source.isPresent()) {
                    return SourceFetchResult.available(originFor(sourceUri), source.get());
                }

                Optional<URI> snapshotUri = extractor.extractVJudgeSnapshotUri(html, sourceUri);
                if (snapshotUri.isPresent()) {
                    String dataUrl = session.evaluateString(snapshotFetchScript(snapshotUri.get()), effectiveTimeout);
                    return imageOcrClient.extractSourceFromDataUrl(dataUrl, effectiveTimeout);
                }

                SourceFetchResult tabResult = classifyUnavailable(html);
                return browserContextResult
                        .filter(result -> result.availability() != SourceAvailability.SOURCE_NOT_AVAILABLE)
                        .orElse(tabResult);
            }
        } catch (RuntimeException ex) {
            return SourceFetchResult.unavailable("Chrome DevTools source fetch failed: " + ex.getMessage());
        } finally {
            if (tab != null) {
                closeTab(tab.id(), effectiveTimeout);
            }
        }
    }

    private Optional<SourceFetchResult> fetchThroughExistingBrowserContext(URI sourceUri, Duration timeout) {
        if (sourceUri == null || sourceUri.getHost() == null) {
            return Optional.empty();
        }
        ChromeTab createdOriginTab = null;
        try {
            Optional<ChromeTab> sameOriginTab = listTabs(timeout).stream()
                    .filter(tab -> sameHost(sourceUri, tab.url()))
                    .sorted((left, right) -> Boolean.compare(
                            isSameUri(sourceUri, left.url()),
                            isSameUri(sourceUri, right.url())
                    ))
                    .findFirst();
            ChromeTab contextTab = sameOriginTab.orElse(null);
            if (contextTab == null) {
                createdOriginTab = openTab(originUri(sourceUri), timeout);
                contextTab = createdOriginTab;
            }
            try (DevToolsSession session = connect(contextTab.webSocketDebuggerUrl(), timeout)) {
                waitForReadyState(session, timeout);
                String fetchResultJson = session.evaluateString(browserFetchScript(sourceUri), timeout);
                if (fetchResultJson == null || fetchResultJson.isBlank()) {
                    return Optional.empty();
                }
                JsonNode fetchResult = objectMapper.readTree(fetchResultJson);
                int status = fetchResult.path("status").asInt(0);
                String html = fetchResult.path("body").asText("");
                if (status >= 200 && status < 300) {
                    Optional<String> source = extractor.extractTextSource(html, sourceUri);
                    if (source.isPresent()) {
                        return Optional.of(SourceFetchResult.available(originFor(sourceUri), source.get()));
                    }
                    return Optional.of(classifyUnavailable(html));
                }
                if (status == 401 || status == 403) {
                    return Optional.of(SourceFetchResult.unavailable(
                            SourceAvailability.PERMISSION_DENIED,
                            originFor(sourceUri),
                            "Chrome session could not access source page. Open this submission manually in Chrome debug, log in, and solve any challenge if shown."
                    ));
                }
                if (status == 429) {
                    return Optional.of(SourceFetchResult.unavailable(
                            SourceAvailability.RATE_LIMITED,
                            originFor(sourceUri),
                            "Source page was rate limited by the platform."
                    ));
                }
                return Optional.of(SourceFetchResult.unavailable(
                        SourceAvailability.SOURCE_NOT_AVAILABLE,
                        originFor(sourceUri),
                        "Source page returned HTTP " + status + " in Chrome session."
                ));
            }
        } catch (RuntimeException | IOException ex) {
            return Optional.empty();
        } finally {
            if (createdOriginTab != null) {
                closeTab(createdOriginTab.id(), timeout);
            }
        }
    }

    private List<ChromeTab> listTabs(Duration timeout) {
        try {
            HttpRequest request = HttpRequest.newBuilder(resolve("/json/list"))
                    .timeout(shortProbeTimeout(timeout))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (!root.isArray()) {
                return List.of();
            }
            java.util.ArrayList<ChromeTab> tabs = new java.util.ArrayList<>();
            for (JsonNode node : root) {
                if (!"page".equalsIgnoreCase(node.path("type").asText())) {
                    continue;
                }
                String id = node.path("id").asText();
                String webSocketUrl = node.path("webSocketDebuggerUrl").asText();
                String url = node.path("url").asText();
                if (!id.isBlank() && !webSocketUrl.isBlank() && !url.isBlank()) {
                    tabs.add(new ChromeTab(id, URI.create(webSocketUrl), URI.create(url)));
                }
            }
            return tabs;
        } catch (IOException | InterruptedException | IllegalArgumentException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return List.of();
        }
    }

    private SourceOrigin originFor(URI sourceUri) {
        String host = sourceUri == null || sourceUri.getHost() == null ? "" : sourceUri.getHost().toLowerCase();
        if (host.contains("codeforces")) {
            return SourceOrigin.CODEFORCES_AUTHORIZED_HTML;
        }
        if (host.contains("vjudge")) {
            return SourceOrigin.VJUDGE_AUTHORIZED_HTML;
        }
        return SourceOrigin.UNKNOWN;
    }

    private SourceFetchResult classifyUnavailable(String html) {
        String lower = html == null ? "" : html.toLowerCase();
        if (lower.contains("captcha") || lower.contains("cloudflare")) {
            return SourceFetchResult.unavailable(
                    SourceAvailability.CAPTCHA_REQUIRED,
                    SourceOrigin.UNKNOWN,
                    "Source page requires CAPTCHA or browser challenge."
            );
        }
        if (lower.contains("login") || lower.contains("sign in")) {
            return SourceFetchResult.unavailable(
                    SourceAvailability.LOGIN_REQUIRED,
                    SourceOrigin.UNKNOWN,
                    "Source page requires login in the Chrome debug session."
            );
        }
        if (lower.contains("access denied") || lower.contains("permission") || lower.contains("not allowed")) {
            return SourceFetchResult.unavailable(
                    SourceAvailability.PERMISSION_DENIED,
                    SourceOrigin.UNKNOWN,
                    "Source page did not expose source for this authorized session."
            );
        }
        if (lower.contains("contest") && (lower.contains("hidden") || lower.contains("private"))) {
            return SourceFetchResult.unavailable(
                    SourceAvailability.CONTEST_HIDDEN,
                    SourceOrigin.UNKNOWN,
                    "Contest settings hide source code for this session."
            );
        }
        return SourceFetchResult.unavailable("Chrome opened the page, but no readable source block was found.");
    }

    private boolean isDevToolsAvailable(Duration timeout) {
        try {
            HttpRequest request = HttpRequest.newBuilder(resolve("/json/version"))
                    .timeout(shortProbeTimeout(timeout))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private ChromeTab openTab(URI sourceUri, Duration timeout) {
        try {
            String encodedUrl = URLEncoder.encode(sourceUri.toString(), StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(resolve("/json/new?" + encodedUrl))
                    .timeout(shortProbeTimeout(timeout))
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new CrawlException("Cannot open Chrome tab. HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            String id = root.path("id").asText();
            String webSocketUrl = root.path("webSocketDebuggerUrl").asText();
            if (id.isBlank() || webSocketUrl.isBlank()) {
                throw new CrawlException("Chrome DevTools did not return a debuggable tab.");
            }
            return new ChromeTab(id, URI.create(webSocketUrl));
        } catch (IOException ex) {
            throw new CrawlException("Cannot open Chrome tab: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CrawlException("Interrupted while opening Chrome tab.", ex);
        }
    }

    private DevToolsSession connect(URI webSocketUri, Duration timeout) {
        DevToolsSession listener = new DevToolsSession(objectMapper);
        try {
            WebSocket webSocket = httpClient.newWebSocketBuilder()
                    .connectTimeout(timeout)
                    .buildAsync(webSocketUri, listener)
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            listener.attach(webSocket);
            return listener;
        } catch (Exception ex) {
            throw new CrawlException("Cannot connect to Chrome tab: " + ex.getMessage(), ex);
        }
    }

    private void waitForReadyState(DevToolsSession session, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            String readyState = session.evaluateString("document.readyState", Duration.ofSeconds(3));
            if ("complete".equalsIgnoreCase(readyState) || "interactive".equalsIgnoreCase(readyState)) {
                return;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new CrawlException("Interrupted while waiting for source page.");
            }
        }
    }

    private void closeTab(String tabId, Duration timeout) {
        try {
            HttpRequest request = HttpRequest.newBuilder(resolve("/json/close/" + tabId))
                    .timeout(shortProbeTimeout(timeout))
                    .GET()
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (IOException | InterruptedException ignored) {
            if (ignored instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private URI resolve(String path) {
        String base = devToolsBaseUri.toString();
        if (base.endsWith("/") && path.startsWith("/")) {
            return URI.create(base.substring(0, base.length() - 1) + path);
        }
        if (!base.endsWith("/") && !path.startsWith("/")) {
            return URI.create(base + "/" + path);
        }
        return URI.create(base + path);
    }

    private Duration shortProbeTimeout(Duration timeout) {
        Duration fallback = Duration.ofSeconds(3);
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return fallback;
        }
        return timeout.compareTo(fallback) > 0 ? fallback : timeout;
    }

    private String snapshotFetchScript(URI snapshotUri) {
        String escapedUri = snapshotUri.toString().replace("\\", "\\\\").replace("'", "\\'");
        return """
                (async () => {
                  const response = await fetch('%s', { credentials: 'include' });
                  if (!response.ok) return '';
                  const blob = await response.blob();
                  return await new Promise((resolve) => {
                    const reader = new FileReader();
                    reader.onloadend = () => resolve(reader.result || '');
                    reader.readAsDataURL(blob);
                  });
                })()
                """.formatted(escapedUri);
    }

    private String browserFetchScript(URI sourceUri) {
        String escapedUri = sourceUri.toString().replace("\\", "\\\\").replace("'", "\\'");
        return """
                (async () => {
                  try {
                    const response = await fetch('%s', {
                      credentials: 'include',
                      cache: 'no-store',
                      headers: {
                        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'
                      }
                    });
                    const body = await response.text();
                    return JSON.stringify({ status: response.status, url: response.url, body });
                  } catch (error) {
                    return JSON.stringify({ status: 0, url: '%s', body: String(error || '') });
                  }
                })()
                """.formatted(escapedUri, escapedUri);
    }

    private boolean sameHost(URI sourceUri, URI tabUri) {
        if (sourceUri == null || tabUri == null || sourceUri.getHost() == null || tabUri.getHost() == null) {
            return false;
        }
        return sourceUri.getHost().equalsIgnoreCase(tabUri.getHost());
    }

    private boolean isSameUri(URI sourceUri, URI tabUri) {
        return sourceUri != null && tabUri != null && sourceUri.normalize().equals(tabUri.normalize());
    }

    private URI originUri(URI sourceUri) {
        String scheme = sourceUri.getScheme() == null ? "https" : sourceUri.getScheme();
        return URI.create(scheme + "://" + sourceUri.getHost() + "/");
    }

    private static URI devToolsBaseUri() {
        String configured = System.getProperty("crawler.chrome-devtools-url");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("CHROME_DEVTOOLS_URL");
        }
        if (configured == null || configured.isBlank()) {
            configured = localApplicationProperty("crawler.chrome-devtools-url");
        }
        return configured == null || configured.isBlank()
                ? DEFAULT_DEVTOOLS_BASE
                : URI.create(configured.trim());
    }

    private static String localApplicationProperty(String key) {
        Path path = Path.of("application.properties");
        if (!Files.isRegularFile(path)) {
            path = Path.of("src", "main", "resources", "application.properties");
        }
        if (!Files.isRegularFile(path)) {
            return "";
        }
        Properties properties = new Properties();
        try (var input = Files.newInputStream(path)) {
            properties.load(input);
            return properties.getProperty(key, "");
        } catch (IOException ex) {
            return "";
        }
    }

    private record ChromeTab(String id, URI webSocketDebuggerUrl, URI url) {
        private ChromeTab(String id, URI webSocketDebuggerUrl) {
            this(id, webSocketDebuggerUrl, null);
        }
    }

    private static final class DevToolsSession implements WebSocket.Listener, AutoCloseable {
        private final ObjectMapper objectMapper;
        private final AtomicInteger nextId = new AtomicInteger(1);
        private final Map<Integer, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
        private final StringBuilder messageBuffer = new StringBuilder();
        private final CountDownLatch closed = new CountDownLatch(1);
        private WebSocket webSocket;

        private DevToolsSession(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        void attach(WebSocket webSocket) {
            this.webSocket = webSocket;
        }

        String evaluateString(String expression, Duration timeout) {
            JsonNode result = sendCommand("Runtime.evaluate", Map.of(
                    "expression", expression,
                    "awaitPromise", true,
                    "returnByValue", true
            ), timeout);
            JsonNode value = result.path("result").path("result").path("value");
            return value.isMissingNode() || value.isNull() ? "" : value.asText();
        }

        JsonNode sendCommand(String method, Map<String, Object> params, Duration timeout) {
            int id = nextId.getAndIncrement();
            ObjectNode command = objectMapper.createObjectNode();
            command.put("id", id);
            command.put("method", method);
            ObjectNode paramsNode = command.putObject("params");
            if (params != null) {
                params.forEach((key, value) -> paramsNode.set(key, objectMapper.valueToTree(value)));
            }

            CompletableFuture<JsonNode> future = new CompletableFuture<>();
            pending.put(id, future);
            webSocket.sendText(command.toString(), true);
            try {
                JsonNode response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (response.has("error")) {
                    throw new CrawlException(response.path("error").path("message").asText("Chrome command failed."));
                }
                return response;
            } catch (Exception ex) {
                pending.remove(id);
                throw new CrawlException("Chrome command timed out or failed: " + method, ex);
            }
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            messageBuffer.append(data);
            if (last) {
                String message = messageBuffer.toString();
                messageBuffer.setLength(0);
                completeMessage(message);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            WebSocket.Listener.super.onOpen(webSocket);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closed.countDown();
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            pending.values().forEach(future -> future.completeExceptionally(error));
            closed.countDown();
        }

        private void completeMessage(String message) {
            try {
                JsonNode root = objectMapper.readTree(message);
                if (!root.has("id")) {
                    return;
                }
                CompletableFuture<JsonNode> future = pending.remove(root.path("id").asInt());
                if (future != null) {
                    future.complete(root);
                }
            } catch (IOException ex) {
                pending.values().forEach(future -> future.completeExceptionally(ex));
            }
        }

        @Override
        public void close() {
            if (webSocket != null) {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
                try {
                    closed.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
