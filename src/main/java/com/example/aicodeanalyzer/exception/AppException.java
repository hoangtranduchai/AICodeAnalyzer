package com.example.aicodeanalyzer.exception;

/**
 * Base unchecked exception for application-level failures.
 */
public class AppException extends RuntimeException {
    public AppException(String message) {
        super(message);
    }

    public AppException(String message, Throwable cause) {
        super(message, cause);
    }
}
