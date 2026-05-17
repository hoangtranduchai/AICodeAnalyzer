package com.example.aicodeanalyzer.crawler;

/**
 * Describes whether a crawler was legally and technically able to fetch source code.
 */
public enum SourceAvailability {
    AVAILABLE,
    SOURCE_NOT_AVAILABLE,
    LOGIN_REQUIRED,
    PERMISSION_DENIED,
    CONTEST_HIDDEN,
    RATE_LIMITED,
    CAPTCHA_REQUIRED,
    OCR_REQUIRED,
    OCR_FAILED
}
