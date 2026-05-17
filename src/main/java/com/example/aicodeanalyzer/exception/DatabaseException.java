package com.example.aicodeanalyzer.exception;

/**
 * Wraps SQL Server connection, query, and transaction errors.
 */
public class DatabaseException extends AppException {
    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
