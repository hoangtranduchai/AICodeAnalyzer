package com.example.aicodeanalyzer.crawler;

import java.net.URI;
import java.time.Duration;

/**
 * Fetches source code from a platform submission page without bypassing access controls.
 */
public interface SourceFetcher {
    SourceFetchResult fetchSource(URI sourceUri, Duration timeout);
}
