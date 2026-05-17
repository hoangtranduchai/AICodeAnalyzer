package com.example.aicodeanalyzer.config;

/**
 * Result object for user-facing database connection checks.
 */
public record ConnectionTestResult(boolean success, String message) {

    public static ConnectionTestResult success(String message) {
        return new ConnectionTestResult(true, message);
    }

    public static ConnectionTestResult failure(String message) {
        return new ConnectionTestResult(false, message);
    }
}
