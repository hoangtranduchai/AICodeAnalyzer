package com.example.aicodeanalyzer.crawler;

/**
 * Describes where a source code record came from.
 */
public enum SourceOrigin {
    UNKNOWN,
    CODEFORCES_API,
    CODEFORCES_AUTHORIZED_HTML,
    VJUDGE_EXPORT_CSV,
    VJUDGE_EXPORT_HTML,
    VJUDGE_ZIP_ARCHIVE,
    VJUDGE_AUTHORIZED_HTML,
    VJUDGE_AUTHORIZED_SNAPSHOT_OCR
}
