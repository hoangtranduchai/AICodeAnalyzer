package com.example.aicodeanalyzer.exception;

/**
 * Wraps rule-based or AI analyzer failures.
 */
public class AnalyzerException extends AppException {
    public AnalyzerException(String message) {
        super(message);
    }

    public AnalyzerException(String message, Throwable cause) {
        super(message, cause);
    }
}
