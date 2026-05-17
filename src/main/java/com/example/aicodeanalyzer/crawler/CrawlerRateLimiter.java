package com.example.aicodeanalyzer.crawler;

import java.time.Duration;

/**
 * Applies request spacing and retry backoff so crawlers do not overwhelm target platforms.
 */
public class CrawlerRateLimiter {
    private final long minDelayMillis;
    private long lastRequestAtMillis;

    public CrawlerRateLimiter(Duration minDelay) {
        this.minDelayMillis = Math.max(0, minDelay == null ? 0 : minDelay.toMillis());
    }

    public static CrawlerRateLimiter politeDefault() {
        return new CrawlerRateLimiter(Duration.ofMillis(1500));
    }

    public synchronized void acquirePermit() {
        long now = System.currentTimeMillis();
        long waitMillis = lastRequestAtMillis + minDelayMillis - now;
        if (waitMillis > 0) {
            sleep(waitMillis);
        }
        lastRequestAtMillis = System.currentTimeMillis();
    }

    public void backoff(int attempt) {
        long backoffMillis = Math.min(10_000L, minDelayMillis * Math.max(1, attempt + 1));
        sleep(backoffMillis);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CrawlException("Crawler was interrupted while waiting for rate limit.", ex);
        }
    }
}
