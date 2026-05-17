package com.example.aicodeanalyzer.crawler;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;

/**
 * Manual smoke test for authorized source crawling through the Chrome CDP bot.
 * Smoke test thủ công để kiểm tra crawl source qua Chrome CDP bot đã đăng nhập.
 */
public final class CrawlerSmokeTestMain {
    private CrawlerSmokeTestMain() {
    }

    public static void main(String[] args) {
        if (args == null || args.length == 0) {
            printUsage();
            return;
        }

        try (PlaywrightCdpSourceFetcher fetcher = new PlaywrightCdpSourceFetcher()) {
            boolean ready = fetcher.ensureVisibleBotBrowserReady(Duration.ofSeconds(30));
            if (!ready) {
                System.err.println("Chrome CDP bot is not ready. Run this command first:");
                System.err.println(fetcher.chromeCommandText());
                System.exit(2);
            }

            Arrays.stream(args)
                    .map(CrawlerSmokeTestMain::normalizeUrl)
                    .forEach(url -> smokeFetch(fetcher, url));
        }
    }

    private static void smokeFetch(SourceFetcher fetcher, String url) {
        try {
            SourceFetchResult result = fetcher.fetchSource(URI.create(url), Duration.ofSeconds(90));
            int length = result.sourceCode() == null ? 0 : result.sourceCode().length();
            System.out.printf(
                    "%s | availability=%s | origin=%s | length=%d | reason=%s%n",
                    url,
                    result.availability(),
                    result.origin(),
                    length,
                    result.unavailableReason() == null ? "-" : result.unavailableReason()
            );
        } catch (RuntimeException ex) {
            System.out.printf("%s | ERROR | %s%n", url, ex.getMessage());
        }
    }

    private static String normalizeUrl(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.startsWith("cf:")) {
            return trimmed.substring(3);
        }
        if (trimmed.startsWith("vj:")) {
            return trimmed.substring(3);
        }
        return trimmed;
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  mvn -q exec:java \"-Dexec.mainClass=com.example.aicodeanalyzer.crawler.CrawlerSmokeTestMain\" \"-Dexec.args=cf:https://codeforces.com/contest/723/submission/356287871 vj:https://vjudge.net/solution/64141858\"");
    }
}
