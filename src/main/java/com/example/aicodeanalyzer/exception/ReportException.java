package com.example.aicodeanalyzer.exception;

/**
 * Wraps PDF, Excel, and report data generation failures.
 */
public class ReportException extends AppException {
    public ReportException(String message) {
        super(message);
    }

    public ReportException(String message, Throwable cause) {
        super(message, cause);
    }
}
