package com.example.aicodeanalyzer.crawler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

final class HttpPageSourceFetcher implements SourceFetcher {
    private final HttpClient httpClient;
    private final JsoupSourceCodeExtractor extractor;

    HttpPageSourceFetcher() {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build(), new JsoupSourceCodeExtractor());
    }

    HttpPageSourceFetcher(HttpClient httpClient, JsoupSourceCodeExtractor extractor) {
        this.httpClient = httpClient;
        this.extractor = extractor;
    }

    @Override
    public SourceFetchResult fetchSource(URI sourceUri, Duration timeout) {
        try {
            HttpRequest request = HttpRequest.newBuilder(sourceUri)
                    .timeout(timeout == null ? Duration.ofSeconds(20) : timeout)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("User-Agent", "AI-Code-Analyzer-Desktop/1.0 educational crawler")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return SourceFetchResult.unavailable("Source page returned HTTP " + response.statusCode() + ".");
            }
            return extractor.extractTextSource(response.body(), sourceUri)
                    .map(source -> SourceFetchResult.available(originFor(sourceUri), source))
                    .orElseGet(() -> SourceFetchResult.unavailable("No readable text source block found in source page."));
        } catch (IOException ex) {
            return SourceFetchResult.unavailable("Cannot fetch source page: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return SourceFetchResult.unavailable("Source fetch was interrupted.");
        } catch (RuntimeException ex) {
            return SourceFetchResult.unavailable("Cannot parse source page: " + ex.getMessage());
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
}
