package com.example.aicodeanalyzer.crawler;

/**
 * Signals a crawler failure that should be logged without crashing the whole scheduler cycle.
 */
public class CrawlException extends RuntimeException {
    public CrawlException(String message) {
        super(message);
    }

    public CrawlException(String message, Throwable cause) {
        super(message, cause);
    }
}
