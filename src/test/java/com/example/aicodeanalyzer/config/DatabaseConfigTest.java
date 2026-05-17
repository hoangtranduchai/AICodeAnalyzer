package com.example.aicodeanalyzer.config;

import com.example.aicodeanalyzer.exception.DatabaseException;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseConfigTest {

    @Test
    void createsConfigFromRequiredProperties() {
        Properties properties = new Properties();
        properties.setProperty("db.url", "jdbc:sqlserver://localhost:1433;databaseName=CodeAnalyzerDb");
        properties.setProperty("db.username", "code_analyzer_app");
        properties.setProperty("db.password", "secret");
        properties.setProperty("db.login-timeout-seconds", "7");

        DatabaseConfig config = DatabaseConfig.fromProperties(properties);

        assertEquals("jdbc:sqlserver://localhost:1433;databaseName=CodeAnalyzerDb", config.url());
        assertEquals("code_analyzer_app", config.username());
        assertEquals("secret", config.password());
        assertEquals(7, config.loginTimeoutSeconds());
    }

    @Test
    void failsWhenRequiredUrlIsMissing() {
        Properties properties = new Properties();
        properties.setProperty("db.username", "code_analyzer_app");

        assertThrows(DatabaseException.class, () -> DatabaseConfig.fromProperties(properties));
    }
}
