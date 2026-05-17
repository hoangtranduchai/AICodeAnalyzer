package com.example.aicodeanalyzer.config;

import com.example.aicodeanalyzer.exception.DatabaseException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Creates JDBC connections and validates SQL Server connectivity.
 */
public class DatabaseConnectionFactory {
    private final DatabaseConfig databaseConfig;

    public DatabaseConnectionFactory(DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
        DriverManager.setLoginTimeout(databaseConfig.loginTimeoutSeconds());
    }

    public static DatabaseConnectionFactory fromApplicationProperties() {
        return new DatabaseConnectionFactory(DatabaseConfig.load());
    }

    public Connection createConnection() {
        try {
            return DriverManager.getConnection(
                    databaseConfig.url(),
                    databaseConfig.username(),
                    databaseConfig.password()
            );
        } catch (SQLException ex) {
            throw new DatabaseException(buildFriendlyConnectionError(ex), ex);
        }
    }

    public ConnectionTestResult testConnection() {
        try (Connection connection = createConnection()) {
            boolean valid = connection.isValid(databaseConfig.loginTimeoutSeconds());
            if (!valid) {
                return ConnectionTestResult.failure(
                        "SQL Server connection was opened, but validation failed. Please check database status."
                );
            }

            return ConnectionTestResult.success(
                    "Connected successfully to SQL Server using " + databaseConfig.maskedUrl()
            );
        } catch (DatabaseException ex) {
            return ConnectionTestResult.failure(ex.getMessage());
        } catch (SQLException ex) {
            return ConnectionTestResult.failure(buildFriendlyConnectionError(ex));
        }
    }

    private String buildFriendlyConnectionError(SQLException ex) {
        String technicalMessage = ex.getMessage() == null ? "" : ex.getMessage();
        String lowerMessage = technicalMessage.toLowerCase();

        if (lowerMessage.contains("login failed")) {
            return "Cannot log in to SQL Server. Please check db.username and db.password.";
        }

        if (lowerMessage.contains("connection refused")
                || lowerMessage.contains("connect timed out")
                || lowerMessage.contains("the connection to the host")) {
            return "Cannot reach SQL Server. Please check that SQL Server is running, TCP/IP is enabled, "
                    + "and port 1433 is open.";
        }

        if (lowerMessage.contains("database") && lowerMessage.contains("not found")) {
            return "Cannot open the configured database. Please create CodeAnalyzerDb and run the schema script.";
        }

        if (lowerMessage.contains("encrypt") || lowerMessage.contains("certificate")) {
            return "SQL Server TLS/certificate validation failed. For local development, use "
                    + "encrypt=true;trustServerCertificate=true in db.url.";
        }

        return "Cannot connect to SQL Server. Please verify db.url, db.username, db.password, and local SQL Server settings. "
                + "Technical detail: " + technicalMessage;
    }
}
