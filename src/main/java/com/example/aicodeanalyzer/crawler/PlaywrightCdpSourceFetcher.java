package com.example.aicodeanalyzer.crawler;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Fetches Codeforces and VJudge source code through one real Chrome profile attached over Playwright CDP.
 */
public class PlaywrightCdpSourceFetcher implements SourceFetcher, AutoCloseable {
    private static final String DEFAULT_CHROME_EXECUTABLE = "chrome.exe";
    private static final String DEFAULT_CDP_ENDPOINT = "http://localhost:9222";
    private static final String DEFAULT_CODEFORCES_BASE_URL = "https://codeforces.com";
    private static final String DEFAULT_VJUDGE_BASE_URL = "https://vjudge.net";
    private static final Path DEFAULT_PROFILE_DIR = Path.of("C:\\CF_Bot_Profile");
    private static final String CODEFORCES_SELECTOR = "#program-source-text";
    private static final String VJUDGE_SELECTOR = "pre.prettyprint";
    private static final String VJUDGE_READY_SELECTOR = String.join(", ",
            "pre.prettyprint",
            "#code-content",
            "#code-panel pre code",
            "#code-panel pre",
            "#code-panel img[src*='solution/snapshot']",
            "img[src*='solution/snapshot']",
            "#code-panel .alert-danger"
    );

    private final String chromeExecutable;
    private final Path profileDir;
    private final String cdpEndpoint;
    private final String codeforcesBaseUrl;
    private final String vjudgeBaseUrl;
    private final Duration minDelay;
    private final Duration maxDelay;
    private final Duration connectTimeout;
    private final Duration sourceWaitTimeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final GeminiImageOcrClient imageOcrClient;

    private Playwright playwright;
    private Browser browser;
    private BrowserContext browserContext;

    public PlaywrightCdpSourceFetcher() {
        this(
                DEFAULT_CHROME_EXECUTABLE,
                DEFAULT_PROFILE_DIR,
                DEFAULT_CDP_ENDPOINT,
                DEFAULT_CODEFORCES_BASE_URL,
                DEFAULT_VJUDGE_BASE_URL,
                Duration.ofSeconds(5),
                Duration.ofSeconds(15),
                Duration.ofSeconds(30),
                Duration.ofSeconds(120),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build()
        );
    }

    public PlaywrightCdpSourceFetcher(
            String chromeExecutable,
            Path profileDir,
            String cdpEndpoint,
            String codeforcesBaseUrl,
            String vjudgeBaseUrl,
            Duration minDelay,
            Duration maxDelay,
            Duration connectTimeout,
            Duration sourceWaitTimeout,
            HttpClient httpClient
    ) {
        this.chromeExecutable = hasText(chromeExecutable) ? chromeExecutable.trim() : DEFAULT_CHROME_EXECUTABLE;
        this.profileDir = profileDir == null ? DEFAULT_PROFILE_DIR : profileDir;
        this.cdpEndpoint = hasText(cdpEndpoint) ? cdpEndpoint.trim() : DEFAULT_CDP_ENDPOINT;
        this.codeforcesBaseUrl = normalizeBaseUrl(codeforcesBaseUrl, DEFAULT_CODEFORCES_BASE_URL);
        this.vjudgeBaseUrl = normalizeBaseUrl(vjudgeBaseUrl, DEFAULT_VJUDGE_BASE_URL);
        this.minDelay = normalizeDuration(minDelay, Duration.ofSeconds(5));
        this.maxDelay = normalizeDuration(maxDelay, Duration.ofSeconds(15));
        if (this.maxDelay.compareTo(this.minDelay) < 0) {
            throw new IllegalArgumentException("maxDelay must be greater than or equal to minDelay.");
        }
        this.connectTimeout = normalizeDuration(connectTimeout, Duration.ofSeconds(30));
        this.sourceWaitTimeout = normalizeDuration(sourceWaitTimeout, Duration.ofSeconds(120));
        this.httpClient = httpClient == null
                ? HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build()
                : httpClient;
        this.objectMapper = new ObjectMapper();
        this.imageOcrClient = new GeminiImageOcrClient();
    }

    /**
     * Runs: cmd /c start chrome.exe --remote-debugging-port=9222 --user-data-dir="C:\CF_Bot_Profile"
     */
    public Process initializeBotBrowser() {
        return initializeBotBrowser(false);
    }

    public boolean initializeVisibleBotBrowser(Duration timeout) {
        if (isVisibleBotBrowserReady()) {
            return true;
        }
        stopBotBrowserProcesses();
        waitForBotBrowserStopped(Duration.ofSeconds(6));
        initializeBotBrowser(false);
        return waitForVisibleBotBrowserReady(timeout);
    }

    /**
     * Runs Chrome in headless mode with the same CDP port and bot profile used by visible sessions.
     */
    public Process initializeBotBrowserHeadless() {
        return initializeBotBrowser(true);
    }

    private Process initializeBotBrowser(boolean headless) {
        try {
            return new ProcessBuilder(chromeLaunchCommand(headless)).start();
        } catch (IOException ex) {
            throw new CrawlException("Cannot initialize bot Chrome profile. Check Chrome installation path.", ex);
        }
    }

    public String chromeCommandText() {
        return chromeLaunchCommand(false).stream()
                .map(this::quoteForCommandLine)
                .collect(Collectors.joining(" "));
    }

    public String chromeHeadlessCommandText() {
        return chromeLaunchCommand(true).stream()
                .map(this::quoteForCommandLine)
                .collect(Collectors.joining(" "));
    }

    public boolean isBotBrowserReady() {
        try {
            HttpRequest request = HttpRequest.newBuilder(resolveCdpPath("/json/version"))
                    .timeout(Duration.ofSeconds(6))
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

    public boolean isHeadlessBotBrowserReady() {
        try {
            HttpRequest request = HttpRequest.newBuilder(resolveCdpPath("/json/version"))
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return false;
            }
            JsonNode root = objectMapper.readTree(response.body());
            String userAgent = root.path("User-Agent").asText("");
            return userAgent.toLowerCase(Locale.ROOT).contains("headlesschrome");
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    public boolean isVisibleBotBrowserReady() {
        return isBotBrowserReady() && !isHeadlessBotBrowserReady();
    }

    public boolean waitForBotBrowserReady(Duration timeout) {
        Duration effectiveTimeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        long deadline = System.nanoTime() + effectiveTimeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (isBotBrowserReady()) {
                return true;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return isBotBrowserReady();
    }

    public boolean waitForVisibleBotBrowserReady(Duration timeout) {
        Duration effectiveTimeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        long deadline = System.nanoTime() + effectiveTimeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (isVisibleBotBrowserReady()) {
                return true;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return isVisibleBotBrowserReady();
    }

    private boolean waitForBotBrowserStopped(Duration timeout) {
        Duration effectiveTimeout = timeout == null ? Duration.ofSeconds(6) : timeout;
        long deadline = System.nanoTime() + effectiveTimeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (!isBotBrowserReady()) {
                return true;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return !isBotBrowserReady();
    }

    public boolean ensureHeadlessBotBrowserReady(Duration timeout) {
        if (isBotBrowserReady()) {
            return true;
        }
        initializeBotBrowserHeadless();
        return waitForBotBrowserReady(timeout);
    }

    public boolean ensureVisibleBotBrowserReady(Duration timeout) {
        if (isVisibleBotBrowserReady()) {
            return true;
        }
        return initializeVisibleBotBrowser(timeout);
    }

    public synchronized SourceFetchResult crawlCodeforces(String subId) {
        return crawlCodeforces(subId, sourceWaitTimeout);
    }

    public synchronized SourceFetchResult crawlCodeforces(String contestId, String subId) {
        return crawlWithSelector(
                codeforcesContestSubmissionUrl(contestId, subId),
                CODEFORCES_SELECTOR,
                SourceOrigin.CODEFORCES_AUTHORIZED_HTML,
                sourceWaitTimeout
        );
    }

    public synchronized SourceFetchResult crawlVjudge(String subId) {
        return crawlVjudge(subId, sourceWaitTimeout);
    }

    @Override
    public synchronized SourceFetchResult fetchSource(URI sourceUri, Duration timeout) {
        if (sourceUri == null || sourceUri.getHost() == null) {
            return SourceFetchResult.unavailable("Source URL is missing.");
        }
        String subId = extractSubmissionId(sourceUri);
        if (!hasText(subId)) {
            return SourceFetchResult.unavailable("Cannot determine submission id from " + sourceUri + ".");
        }

        String host = sourceUri.getHost().toLowerCase(Locale.ROOT);
        Duration effectiveTimeout = timeout == null ? sourceWaitTimeout : timeout;
        if (host.contains("codeforces")) {
            return crawlCodeforcesUrl(sourceUri.toString(), effectiveTimeout);
        }
        if (host.contains("vjudge")) {
            return crawlVjudgeUrl(sourceUri.toString(), effectiveTimeout);
        }
        return SourceFetchResult.unavailable("Unsupported source host: " + sourceUri.getHost() + ".");
    }

    private SourceFetchResult crawlCodeforces(String subId, Duration timeout) {
        String normalized = requireSubmissionId(subId);
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return crawlCodeforcesUrl(normalized, timeout);
        }
        return crawlWithSelector(
                codeforcesSubmissionPathToUrl(normalized),
                CODEFORCES_SELECTOR,
                SourceOrigin.CODEFORCES_AUTHORIZED_HTML,
                timeout
        );
    }

    private SourceFetchResult crawlCodeforcesUrl(String sourceUrl, Duration timeout) {
        return crawlWithSelector(
                sourceUrl,
                CODEFORCES_SELECTOR,
                SourceOrigin.CODEFORCES_AUTHORIZED_HTML,
                timeout
        );
    }

    private SourceFetchResult crawlVjudge(String subId, Duration timeout) {
        String normalized = requireSubmissionId(subId);
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return crawlVjudgeUrl(normalized, timeout);
        }
        return crawlVjudgeSourcePage(vjudgeBaseUrl + "/solution/" + normalized, timeout);
    }

    private SourceFetchResult crawlVjudgeUrl(String sourceUrl, Duration timeout) {
        return crawlVjudgeSourcePage(sourceUrl, timeout);
    }

    private SourceFetchResult crawlWithSelector(
            String sourceUrl,
            String selector,
            SourceOrigin sourceOrigin,
            Duration timeout
    ) {
        Duration effectiveTimeout = timeout == null ? sourceWaitTimeout : timeout;
        Page page = null;
        try {
            page = defaultBrowserContext().newPage();
            sleepRandomHumanDelay();
            Response response = page.navigate(sourceUrl, new Page.NavigateOptions().setTimeout(effectiveTimeout.toMillis()));
            if (response != null && response.status() == 404) {
                return SourceFetchResult.unavailable(
                        SourceAvailability.SOURCE_NOT_AVAILABLE,
                        sourceOrigin,
                        notFoundMessage(sourceOrigin, "Source page returned HTTP 404.")
                );
            }
            page.waitForSelector(selector, new Page.WaitForSelectorOptions().setTimeout(effectiveTimeout.toMillis()));
            String sourceCode = readSourceText(page, selector);
            if (!hasText(sourceCode)) {
                return SourceFetchResult.unavailable(
                        SourceAvailability.SOURCE_NOT_AVAILABLE,
                        sourceOrigin,
                        "Source selector " + selector + " was found, but it was empty."
                );
            }
            return SourceFetchResult.available(sourceOrigin, sourceCode);
        } catch (TimeoutError ex) {
            return classifyTimeout(page, sourceOrigin, selector, ex);
        } catch (RuntimeException ex) {
            return SourceFetchResult.unavailable(
                    SourceAvailability.SOURCE_NOT_AVAILABLE,
                    sourceOrigin,
                    "Playwright CDP source crawl failed: " + safeMessage(ex)
            );
        } finally {
            closePageQuietly(page);
        }
    }

    private SourceFetchResult crawlVjudgeSourcePage(String sourceUrl, Duration timeout) {
        Duration effectiveTimeout = timeout == null ? sourceWaitTimeout : timeout;
        Page page = null;
        try {
            page = defaultBrowserContext().newPage();
            sleepRandomHumanDelay();
            Response response = page.navigate(sourceUrl, new Page.NavigateOptions().setTimeout(effectiveTimeout.toMillis()));
            if (response != null && response.status() == 404) {
                return SourceFetchResult.unavailable(
                        SourceAvailability.SOURCE_NOT_AVAILABLE,
                        SourceOrigin.VJUDGE_AUTHORIZED_HTML,
                        notFoundMessage(SourceOrigin.VJUDGE_AUTHORIZED_HTML, "Source page returned HTTP 404.")
                );
            }

            SourceFetchResult dataEndpointResult = fetchVjudgeSourceFromDataEndpoint(page, sourceUrl, effectiveTimeout);
            if (dataEndpointResult.hasSourceCode()
                    || dataEndpointResult.availability() == SourceAvailability.OCR_REQUIRED
                    || dataEndpointResult.availability() == SourceAvailability.OCR_FAILED
                    || dataEndpointResult.availability() == SourceAvailability.PERMISSION_DENIED
                    || dataEndpointResult.availability() == SourceAvailability.LOGIN_REQUIRED) {
                return dataEndpointResult;
            }

            page.waitForSelector(VJUDGE_READY_SELECTOR, new Page.WaitForSelectorOptions().setTimeout(effectiveTimeout.toMillis()));
            String textSource = readFirstAvailableSourceText(
                    page,
                    VJUDGE_SELECTOR,
                    "#code-content",
                    "#code-panel pre code",
                    "#code-panel pre"
            );
            if (hasText(textSource)) {
                return SourceFetchResult.available(SourceOrigin.VJUDGE_AUTHORIZED_HTML, textSource);
            }

            String snapshotUrl = firstAttribute(page, "img[src*='solution/snapshot'], img[src*='snapshot']", "src");
            if (hasText(snapshotUrl)) {
                return ocrVjudgeSnapshot(page, snapshotUrl, effectiveTimeout);
            }

            return SourceFetchResult.unavailable(
                    SourceAvailability.SOURCE_NOT_AVAILABLE,
                    SourceOrigin.VJUDGE_AUTHORIZED_HTML,
                    "VJudge page loaded, but neither text source nor snapshot image was exposed."
            );
        } catch (TimeoutError ex) {
            return classifyTimeout(page, SourceOrigin.VJUDGE_AUTHORIZED_HTML, VJUDGE_SELECTOR, ex);
        } catch (RuntimeException ex) {
            return SourceFetchResult.unavailable(
                    SourceAvailability.SOURCE_NOT_AVAILABLE,
                    SourceOrigin.VJUDGE_AUTHORIZED_HTML,
                    "Playwright CDP VJudge crawl failed: " + safeMessage(ex)
            );
        } finally {
            closePageQuietly(page);
        }
    }

    private BrowserContext defaultBrowserContext() {
        Browser connectedBrowser = connectedBrowser();
        if (browserContext != null && connectedBrowser.contexts().contains(browserContext)) {
            return browserContext;
        }
        if (connectedBrowser.contexts().isEmpty()) {
            throw new CrawlException("No default Chrome context is available from Playwright CDP.");
        }
        browserContext = connectedBrowser.contexts().get(0);
        return browserContext;
    }

    private Browser connectedBrowser() {
        if (browser != null && browser.isConnected()) {
            return browser;
        }
        if (playwright == null) {
            playwright = Playwright.create();
        }
        browser = playwright.chromium().connectOverCDP(
                cdpEndpoint,
                new BrowserType.ConnectOverCDPOptions().setTimeout(connectTimeout.toMillis())
        );
        browserContext = null;
        return browser;
    }

    private SourceFetchResult classifyTimeout(Page page, SourceOrigin sourceOrigin, String selector, TimeoutError ex) {
        String html = "";
        try {
            html = page.content();
        } catch (RuntimeException ignored) {
            // Page content is only diagnostic here.
        }
        String lower = html == null ? "" : html.toLowerCase(Locale.ROOT);
        if (lower.contains("requested url was not found")
                || lower.contains("was not found on this server")
                || lower.contains("запрошенный url")) {
            return SourceFetchResult.unavailable(
                    SourceAvailability.SOURCE_NOT_AVAILABLE,
                    sourceOrigin,
                    notFoundMessage(sourceOrigin, "Source page was not found.")
            );
        }
        if (lower.contains("cloudflare") || lower.contains("captcha") || lower.contains("checking your browser")) {
            return SourceFetchResult.unavailable(
                    SourceAvailability.CAPTCHA_REQUIRED,
                    sourceOrigin,
                    "Timed out waiting for " + selector + " while a browser challenge may still be active."
            );
        }
        if (lower.contains("login") || lower.contains("sign in") || lower.contains("enter")) {
            return SourceFetchResult.unavailable(
                    SourceAvailability.LOGIN_REQUIRED,
                    sourceOrigin,
                    "Timed out waiting for " + selector + ". Log in with the bot Chrome profile and retry."
            );
        }
        return SourceFetchResult.unavailable(
                SourceAvailability.SOURCE_NOT_AVAILABLE,
                sourceOrigin,
                "Timed out waiting for " + selector + ": " + safeMessage(ex)
        );
    }

    private void sleepRandomHumanDelay() {
        long delayMillis = ThreadLocalRandom.current().nextLong(minDelay.toMillis(), maxDelay.toMillis() + 1);
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CrawlException("Interrupted during pre-navigation crawl delay.", ex);
        }
    }

    private String readSourceText(Page page, String selector) {
        String sourceCode = page.innerText(selector);
        if (hasText(sourceCode)) {
            return normalizeSourceText(sourceCode);
        }
        Object textContent = page.locator(selector).evaluate("node => node.textContent || ''");
        return normalizeSourceText(textContent == null ? "" : textContent.toString());
    }

    private String readFirstAvailableSourceText(Page page, String... selectors) {
        if (selectors == null) {
            return "";
        }
        for (String selector : selectors) {
            try {
                if (page.locator(selector).count() == 0) {
                    continue;
                }
                String source = readSourceText(page, selector);
                if (hasText(source)) {
                    return source;
                }
            } catch (RuntimeException ignored) {
                // Try the next VJudge source shape.
            }
        }
        return "";
    }

    private SourceFetchResult fetchVjudgeSourceFromDataEndpoint(Page page, String sourceUrl, Duration timeout) {
        String runId = extractSubmissionId(URI.create(sourceUrl));
        if (!hasText(runId)) {
            return SourceFetchResult.unavailable("Cannot determine VJudge run id from " + sourceUrl + ".");
        }
        try {
            Object rawResult = page.evaluate(vjudgeDataFetchScript(), runId);
            String resultJson = rawResult == null ? "" : rawResult.toString();
            if (!hasText(resultJson)) {
                return SourceFetchResult.unavailable("VJudge data endpoint did not return a response.");
            }
            JsonNode fetchResult = objectMapper.readTree(resultJson);
            int status = fetchResult.path("status").asInt(0);
            String body = fetchResult.path("body").asText("");
            if (status == 401 || status == 403) {
                return SourceFetchResult.unavailable(
                        SourceAvailability.PERMISSION_DENIED,
                        SourceOrigin.VJUDGE_AUTHORIZED_HTML,
                        "VJudge data endpoint denied access. Log in with an account that can view this solution."
                );
            }
            if (status < 200 || status >= 300 || !hasText(body)) {
                return SourceFetchResult.unavailable("VJudge data endpoint returned HTTP " + status + ".");
            }

            JsonNode data = objectMapper.readTree(body);
            String code = data.path("code").asText("");
            if (hasText(code)) {
                return SourceFetchResult.available(SourceOrigin.VJUDGE_AUTHORIZED_HTML, normalizeSourceText(code));
            }

            String codeImageUrl = firstText(
                    data.path("codeImgUrl").asText(""),
                    data.path("snapshotUrl").asText("")
            );
            if (hasText(codeImageUrl)) {
                return ocrVjudgeSnapshot(page, codeImageUrl, timeout);
            }

            if (data.hasNonNull("codeAccessInfo") || data.hasNonNull("error")) {
                return classifyVjudgeDataMessage(data);
            }
            return SourceFetchResult.unavailable("VJudge data endpoint did not expose text source or snapshot image.");
        } catch (IOException ex) {
            return SourceFetchResult.unavailable("Cannot parse VJudge data endpoint response: " + ex.getMessage());
        } catch (RuntimeException ex) {
            return SourceFetchResult.unavailable("Cannot read VJudge data endpoint in Chrome: " + safeMessage(ex));
        }
    }

    private SourceFetchResult classifyVjudgeDataMessage(JsonNode data) {
        String message = data == null ? "" : data.toString();
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("login") || lower.contains("sign in")) {
            return SourceFetchResult.unavailable(
                    SourceAvailability.LOGIN_REQUIRED,
                    SourceOrigin.VJUDGE_AUTHORIZED_HTML,
                    "VJudge source requires login in the bot Chrome profile."
            );
        }
        if (lower.contains("permission") || lower.contains("access") || lower.contains("not allowed")) {
            return SourceFetchResult.unavailable(
                    SourceAvailability.PERMISSION_DENIED,
                    SourceOrigin.VJUDGE_AUTHORIZED_HTML,
                    "VJudge did not grant source access for this account."
            );
        }
        if (lower.contains("captcha") || lower.contains("crawler_blocked") || lower.contains("cloudflare")) {
            return SourceFetchResult.unavailable(
                    SourceAvailability.CAPTCHA_REQUIRED,
                    SourceOrigin.VJUDGE_AUTHORIZED_HTML,
                    "VJudge blocked the data endpoint. Open the solution manually in the bot Chrome profile and retry."
            );
        }
        return SourceFetchResult.unavailable(
                SourceAvailability.SOURCE_NOT_AVAILABLE,
                SourceOrigin.VJUDGE_AUTHORIZED_HTML,
                "VJudge data endpoint returned an error: " + message
        );
    }

    private SourceFetchResult ocrVjudgeSnapshot(Page page, String snapshotUrl, Duration timeout) {
        try {
            Object rawDataUrl = page.evaluate(snapshotFetchScript(), snapshotUrl);
            String dataUrl = rawDataUrl == null ? "" : rawDataUrl.toString();
            return imageOcrClient.extractSourceFromDataUrl(dataUrl, timeout);
        } catch (RuntimeException ex) {
            return SourceFetchResult.unavailable(
                    SourceAvailability.OCR_REQUIRED,
                    SourceOrigin.VJUDGE_AUTHORIZED_SNAPSHOT_OCR,
                    "VJudge source is a snapshot image, but Chrome could not read it: " + safeMessage(ex)
            );
        }
    }

    private String firstAttribute(Page page, String selector, String attributeName) {
        try {
            if (page.locator(selector).count() == 0) {
                return "";
            }
            String value = page.locator(selector).first().getAttribute(attributeName);
            return value == null ? "" : value.trim();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private String vjudgeDataFetchScript() {
        return """
                async (runId) => {
                  try {
                    const form = new URLSearchParams();
                    form.set('shareCode', '');
                    const response = await fetch(`/solution/data/${encodeURIComponent(runId)}?inPage=true`, {
                      method: 'POST',
                      credentials: 'include',
                      cache: 'no-store',
                      headers: {
                        'Accept': 'application/json, text/javascript, */*; q=0.01',
                        'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
                        'X-Requested-With': 'XMLHttpRequest'
                      },
                      body: form.toString()
                    });
                    const body = await response.text();
                    return JSON.stringify({ status: response.status, body });
                  } catch (error) {
                    return JSON.stringify({ status: 0, body: String(error || '') });
                  }
                }
                """;
    }

    private String snapshotFetchScript() {
        return """
                async (src) => {
                  const response = await fetch(new URL(src, window.location.href).href, {
                    credentials: 'include',
                    cache: 'no-store'
                  });
                  if (!response.ok) {
                    return '';
                  }
                  const blob = await response.blob();
                  return await new Promise((resolve) => {
                    const reader = new FileReader();
                    reader.onloadend = () => resolve(reader.result || '');
                    reader.readAsDataURL(blob);
                  });
                }
                """;
    }

    private String normalizeSourceText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u00a0', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .strip();
    }

    private String firstText(String first, String second) {
        return hasText(first) ? first.trim() : hasText(second) ? second.trim() : "";
    }

    private String notFoundMessage(SourceOrigin sourceOrigin, String prefix) {
        if (sourceOrigin == SourceOrigin.CODEFORCES_AUTHORIZED_HTML) {
            return prefix + " For Codeforces use cf:<contestId>/<submissionId>, not only cf:<submissionId>.";
        }
        return prefix + " Check that the submission id is correct and the bot account can view it.";
    }

    private void closePageQuietly(Page page) {
        try {
            if (page != null && !page.isClosed()) {
                page.close();
            }
        } catch (RuntimeException ignored) {
            // Best-effort cleanup. The crawl result should not be masked by tab close failure.
        }
    }

    private List<String> chromeLaunchCommand(boolean headless) {
        List<String> command = new ArrayList<>();
        command.add(resolveChromeExecutable());
        if (headless) {
            command.add("--headless=new");
            command.add("--disable-gpu");
        } else {
            command.add("--new-window");
        }
        command.add("--remote-debugging-port=9222");
        command.add("--user-data-dir=" + profileDir);
        command.add("--no-first-run");
        command.add("--disable-default-apps");
        if (!headless) {
            command.add("about:blank");
        }
        return command;
    }

    private String resolveChromeExecutable() {
        Path configuredPath = Path.of(chromeExecutable);
        if ((configuredPath.isAbsolute() || chromeExecutable.contains("\\") || chromeExecutable.contains("/"))
                && Files.isRegularFile(configuredPath)) {
            return configuredPath.toString();
        }

        for (Path candidate : chromeExecutableCandidates()) {
            if (Files.isRegularFile(candidate)) {
                return candidate.toString();
            }
        }
        return chromeExecutable;
    }

    private List<Path> chromeExecutableCandidates() {
        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"));
        candidates.add(Path.of("C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe"));
        String localAppData = System.getenv("LOCALAPPDATA");
        if (hasText(localAppData)) {
            candidates.add(Path.of(localAppData, "Google", "Chrome", "Application", "chrome.exe"));
        }
        return candidates;
    }

    private List<ProcessHandle> botBrowserProcesses() {
        return ProcessHandle.allProcesses()
                .filter(this::isBotBrowserProcess)
                .toList();
    }

    private boolean isBotBrowserProcess(ProcessHandle processHandle) {
        return processHandle.info().commandLine()
                .map(this::isBotBrowserCommandLine)
                .orElse(false);
    }

    private boolean isBotBrowserCommandLine(String commandLine) {
        String normalized = normalizeWindowsCommandLine(commandLine);
        String normalizedProfile = normalizeWindowsCommandLine(profileDir.toString());
        return normalized.contains("--remote-debugging-port=9222")
                || normalized.contains(normalizedProfile);
    }

    private boolean isHeadlessBotBrowserProcess(ProcessHandle processHandle) {
        return processHandle.info().commandLine()
                .map(commandLine -> normalizeWindowsCommandLine(commandLine).contains("--headless"))
                .orElse(false);
    }

    private boolean hasVisibleBotBrowserProcess() {
        return botBrowserProcesses().stream().anyMatch(process -> !isHeadlessBotBrowserProcess(process));
    }

    private void stopBotBrowserProcesses() {
        for (ProcessHandle process : botBrowserProcesses()) {
            process.destroy();
        }
        for (ProcessHandle process : botBrowserProcesses()) {
            waitForProcessExit(process, Duration.ofSeconds(2));
            if (process.isAlive()) {
                process.destroyForcibly();
                waitForProcessExit(process, Duration.ofSeconds(2));
            }
        }
        closeConnectedCdpBrowserQuietly();
    }

    private void waitForProcessExit(ProcessHandle process, Duration timeout) {
        try {
            process.onExit().get(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            // Caller decides whether to destroy forcibly after this best-effort wait.
        }
    }

    private String normalizeWindowsCommandLine(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace('/', '\\');
    }

    private void closeConnectedCdpBrowserQuietly() {
        try {
            if (browser != null && browser.isConnected()) {
                browser.close();
            }
        } catch (RuntimeException ignored) {
            // Best-effort only: visible Chrome startup below will report readiness.
        } finally {
            browserContext = null;
            browser = null;
        }
    }

    private URI resolveCdpPath(String path) {
        String base = cdpEndpoint.endsWith("/") ? cdpEndpoint.substring(0, cdpEndpoint.length() - 1) : cdpEndpoint;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create(base + normalizedPath);
    }

    private String extractSubmissionId(URI sourceUri) {
        String path = sourceUri.getPath();
        if (!hasText(path)) {
            return "";
        }
        String[] parts = path.split("/");
        for (int index = parts.length - 1; index >= 0; index--) {
            if (hasText(parts[index])) {
                return parts[index].trim();
            }
        }
        return "";
    }

    private String requireSubmissionId(String subId) {
        if (!hasText(subId)) {
            throw new IllegalArgumentException("submission id must not be blank.");
        }
        return subId.trim();
    }

    private String codeforcesContestSubmissionUrl(String contestId, String subId) {
        return codeforcesBaseUrl + "/contest/" + requireSubmissionId(contestId) + "/submission/" + requireSubmissionId(subId);
    }

    private String codeforcesSubmissionPathToUrl(String value) {
        String normalized = value.trim().replace('\\', '/');
        if (normalized.startsWith("/")) {
            return codeforcesBaseUrl + normalized;
        }
        if (normalized.startsWith("contest/") || normalized.startsWith("gym/")) {
            return codeforcesBaseUrl + "/" + normalized;
        }
        if (normalized.contains("/")) {
            String[] parts = normalized.split("/");
            if (parts.length == 2 && hasText(parts[0]) && hasText(parts[1])) {
                return codeforcesBaseUrl + "/contest/" + parts[0].trim() + "/submission/" + parts[1].trim();
            }
            return codeforcesBaseUrl + "/" + normalized;
        }
        if (normalized.contains(":")) {
            String[] parts = normalized.split(":", 2);
            if (parts.length == 2 && hasText(parts[0]) && hasText(parts[1])) {
                return codeforcesBaseUrl + "/contest/" + parts[0].trim() + "/submission/" + parts[1].trim();
            }
        }
        return codeforcesBaseUrl + "/submission/" + normalized;
    }

    private String normalizeBaseUrl(String value, String fallback) {
        String baseUrl = hasText(value) ? value.trim() : fallback;
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private Duration normalizeDuration(Duration value, Duration fallback) {
        Duration effective = value == null ? fallback : value;
        if (effective.isZero() || effective.isNegative()) {
            throw new IllegalArgumentException("Duration values must be positive.");
        }
        return effective;
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        return hasText(message) ? message : throwable == null ? "Unknown error." : throwable.getClass().getSimpleName();
    }

    private String quoteForCommandLine(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(" ") || value.contains("\\")) {
            return "\"" + value.replace("\"", "\\\"") + "\"";
        }
        return value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    @Override
    public synchronized void close() {
        browserContext = null;
        browser = null;
        if (playwright != null) {
            playwright.close();
            playwright = null;
        }
    }
}
