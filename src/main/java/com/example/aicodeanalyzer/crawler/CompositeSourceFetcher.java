package com.example.aicodeanalyzer.crawler;

import java.net.URI;
import java.time.Duration;
import java.util.List;

final class CompositeSourceFetcher implements SourceFetcher {
    private final List<SourceFetcher> fetchers;

    CompositeSourceFetcher(List<SourceFetcher> fetchers) {
        this.fetchers = fetchers == null ? List.of() : List.copyOf(fetchers);
    }

    @Override
    public SourceFetchResult fetchSource(URI sourceUri, Duration timeout) {
        SourceFetchResult lastResult = SourceFetchResult.unavailable("No source fetcher is configured.");
        for (SourceFetcher fetcher : fetchers) {
            SourceFetchResult result = fetcher.fetchSource(sourceUri, timeout);
            if (result.hasSourceCode()) {
                return result;
            }
            lastResult = result;
        }
        return lastResult;
    }
}
