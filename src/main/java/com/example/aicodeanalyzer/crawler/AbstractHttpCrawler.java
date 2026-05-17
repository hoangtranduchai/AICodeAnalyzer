package com.example.aicodeanalyzer.crawler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;

/**
 * Shared HTTP, rate-limit, and bounded-retry behavior for crawler adapters.
 */
abstract class AbstractHttpCrawler implements OnlineJudgeCrawler {
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final Set<Integer> RETRYABLE_STATUS_CODES = Set.of(429, 500, 502, 503, 504);

    private final HttpClient httpClient;
    private final CrawlerRateLimiter rateLimiter;
    private final int maxAttempts;

    protected AbstractHttpCrawler(HttpClient httpClient, CrawlerRateLimiter rateLimiter, int maxAttempts) {
        this.httpClient = httpClient == null
                ? HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
                : httpClient;
        this.rateLimiter = rateLimiter == null ? CrawlerRateLimiter.politeDefault() : rateLimiter;
        this.maxAttempts = maxAttempts <= 0 ? DEFAULT_MAX_ATTEMPTS : maxAttempts;
    }

    protected String getText(URI uri, Duration timeout) {
        CrawlException lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            rateLimiter.acquirePermit();
            try {
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(timeout == null ? Duration.ofSeconds(20) : timeout)
                        .header("Accept", "application/json,text/plain,text/html;q=0.8")
                        .header("User-Agent", "AI-Code-Analyzer-Desktop/1.0 educational crawler")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int statusCode = response.statusCode();
                if (statusCode >= 200 && statusCode < 300) {
                    return response.body();
                }
                if (!RETRYABLE_STATUS_CODES.contains(statusCode) || attempt == maxAttempts) {
                    throw new CrawlException("HTTP " + statusCode + " while requesting " + uri);
                }
            } catch (IOException ex) {
                lastException = new CrawlException("Network error while requesting " + uri + ": " + ex.getMessage(), ex);
                if (attempt == maxAttempts) {
                    throw lastException;
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new CrawlException("Crawler interrupted while requesting " + uri, ex);
            }

            rateLimiter.backoff(attempt);
        }

        throw lastException == null
                ? new CrawlException("Cannot request " + uri)
                : lastException;
    }
}
