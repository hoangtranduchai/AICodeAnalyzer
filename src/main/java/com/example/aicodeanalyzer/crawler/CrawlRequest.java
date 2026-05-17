package com.example.aicodeanalyzer.crawler;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable input for platform crawler adapters.
 */
public record CrawlRequest(
        String handle,
        int maxSubmissions,
        boolean publicDataOnly,
        boolean hasUserConsent,
        Duration requestTimeout,
        Set<String> knownSubmissionIds
) {
    public static final int UNLIMITED_SUBMISSIONS = -1;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);

    public CrawlRequest(
            String handle,
            int maxSubmissions,
            boolean publicDataOnly,
            boolean hasUserConsent,
            Duration requestTimeout
    ) {
        this(handle, maxSubmissions, publicDataOnly, hasUserConsent, requestTimeout, Set.of());
    }

    public CrawlRequest {
        if (handle == null || handle.trim().isEmpty()) {
            throw new IllegalArgumentException("handle is required.");
        }
        handle = handle.trim();
        maxSubmissions = maxSubmissions <= 0 ? UNLIMITED_SUBMISSIONS : maxSubmissions;
        requestTimeout = requestTimeout == null ? DEFAULT_TIMEOUT : requestTimeout;
        knownSubmissionIds = knownSubmissionIds == null
                ? Set.of()
                : knownSubmissionIds.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(CrawlRequest::normalizeSubmissionId)
                        .collect(Collectors.toUnmodifiableSet());
    }

    public static CrawlRequest publicOnly(String handle, int maxSubmissions) {
        return new CrawlRequest(handle, maxSubmissions, true, false, DEFAULT_TIMEOUT, Set.of());
    }

    public boolean isUnlimited() {
        return maxSubmissions == UNLIMITED_SUBMISSIONS;
    }

    public boolean isKnownSubmission(String platformSubmissionId) {
        return platformSubmissionId != null
                && knownSubmissionIds.contains(normalizeSubmissionId(platformSubmissionId));
    }

    private static String normalizeSubmissionId(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
