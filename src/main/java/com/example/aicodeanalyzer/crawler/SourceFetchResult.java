package com.example.aicodeanalyzer.crawler;

/**
 * Result of an authorized attempt to fetch source code from a submission page.
 */
public record SourceFetchResult(
        SourceAvailability availability,
        SourceOrigin origin,
        String sourceCode,
        String unavailableReason
) {
    public static SourceFetchResult available(SourceOrigin origin, String sourceCode) {
        return new SourceFetchResult(SourceAvailability.AVAILABLE, origin, sourceCode, null);
    }

    public static SourceFetchResult available(String sourceCode) {
        return available(SourceOrigin.UNKNOWN, sourceCode);
    }

    public static SourceFetchResult unavailable(SourceAvailability availability, SourceOrigin origin, String reason) {
        SourceAvailability normalized = availability == null || availability == SourceAvailability.AVAILABLE
                ? SourceAvailability.SOURCE_NOT_AVAILABLE
                : availability;
        return new SourceFetchResult(normalized, origin == null ? SourceOrigin.UNKNOWN : origin, null, reason);
    }

    public static SourceFetchResult unavailable(String reason) {
        return unavailable(SourceAvailability.SOURCE_NOT_AVAILABLE, SourceOrigin.UNKNOWN, reason);
    }

    public boolean hasSourceCode() {
        return availability == SourceAvailability.AVAILABLE
                && sourceCode != null
                && !sourceCode.isBlank();
    }
}
