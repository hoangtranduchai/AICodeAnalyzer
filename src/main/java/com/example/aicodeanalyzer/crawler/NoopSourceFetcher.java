package com.example.aicodeanalyzer.crawler;

import java.net.URI;
import java.time.Duration;

final class NoopSourceFetcher implements SourceFetcher {
    private final String reason;

    NoopSourceFetcher(String reason) {
        this.reason = reason;
    }

    @Override
    public SourceFetchResult fetchSource(URI sourceUri, Duration timeout) {
        return SourceFetchResult.unavailable(reason);
    }
}
