package com.example.aicodeanalyzer.crawler;

final class DefaultSourceFetchers {
    private static final PlaywrightCdpSourceFetcher PLAYWRIGHT_CDP_SOURCE_FETCHER = new PlaywrightCdpSourceFetcher();

    private DefaultSourceFetchers() {
    }

    static SourceFetcher authorizedBrowserThenHttp() {
        return PLAYWRIGHT_CDP_SOURCE_FETCHER;
    }
}
