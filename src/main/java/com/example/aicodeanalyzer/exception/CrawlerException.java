package com.example.aicodeanalyzer.exception;

/**
 * Wraps crawler API, network, parsing, rate-limit, and permission errors.
 */
public class CrawlerException extends AppException {
    public CrawlerException(String message, Throwable cause) {
        super(message, cause);
    }
}
