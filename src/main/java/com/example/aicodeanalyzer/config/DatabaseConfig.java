package com.example.aicodeanalyzer.config;

import com.example.aicodeanalyzer.exception.DatabaseException;

import java.io.IOException;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads SQL Server connection settings from application.properties.
 */
public record DatabaseConfig(
        String url,
        String username,
        String password,
        int loginTimeoutSeconds
) {
    private static final String DEFAULT_CONFIG_FILE = "application.properties";
    private static final int DEFAULT_LOGIN_TIMEOUT_SECONDS = 10;
    private static final Pattern ENV_PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");

    public DatabaseConfig {
        url = requireText(url, "db.url");
        username = requireText(username, "db.username");
        password = Objects.requireNonNullElse(password, "");
        if (loginTimeoutSeconds <= 0) {
            throw new DatabaseException("db.login-timeout-seconds must be greater than 0.");
        }
    }

    public static DatabaseConfig load() {
        return load(DEFAULT_CONFIG_FILE);
    }

    public static DatabaseConfig load(String resourceName) {
        try {
            Properties properties = ConfigResourceLoader.loadProperties(resourceName);
            applyEnvironmentOverrides(properties);
            return fromProperties(properties);
        } catch (IOException ex) {
            throw new DatabaseException(
                    "Cannot read application configuration. Use -Dapp.config or APP_CONFIG_FILE for local settings.",
                    ex
            );
        }
    }

    public static DatabaseConfig fromProperties(Properties properties) {
        String url = getRequired(properties, "db.url");
        String username = getRequired(properties, "db.username");
        String password = getOptional(properties, "db.password");
        int timeout = getPositiveInt(properties, "db.login-timeout-seconds", DEFAULT_LOGIN_TIMEOUT_SECONDS);

        return new DatabaseConfig(url, username, password, timeout);
    }

    public String maskedUrl() {
        return url.replaceAll("(?i)(password=)[^;]*", "$1****");
    }

    private static void applyEnvironmentOverrides(Properties properties) {
        copyEnvToPropertyIfPresent(properties, "DB_URL", "db.url");
        copyEnvToPropertyIfPresent(properties, "DB_USERNAME", "db.username");
        copyEnvToPropertyIfPresent(properties, "DB_PASSWORD", "db.password");
    }

    private static void copyEnvToPropertyIfPresent(Properties properties, String envKey, String propertyKey) {
        String envValue = System.getenv(envKey);
        if (hasText(envValue)) {
            properties.setProperty(propertyKey, envValue.trim());
        }
    }

    private static String getRequired(Properties properties, String propertyKey) {
        String value = getOptional(properties, propertyKey);
        return requireText(value, propertyKey);
    }

    private static String getOptional(Properties properties, String propertyKey) {
        String propertyValue = properties.getProperty(propertyKey);
        if (propertyValue == null) {
            return "";
        }
        return resolveEnvironmentPlaceholders(propertyValue.trim(), propertyKey);
    }

    private static int getPositiveInt(Properties properties, String propertyKey, int defaultValue) {
        String rawValue = properties.getProperty(propertyKey);
        if (!hasText(rawValue)) {
            return defaultValue;
        }

        try {
            int parsedValue = Integer.parseInt(rawValue.trim());
            if (parsedValue <= 0) {
                throw new NumberFormatException("Value must be positive.");
            }
            return parsedValue;
        } catch (NumberFormatException ex) {
            throw new DatabaseException(propertyKey + " must be a positive integer.", ex);
        }
    }

    private static String resolveEnvironmentPlaceholders(String value, String propertyKey) {
        Matcher matcher = ENV_PLACEHOLDER.matcher(value);
        StringBuilder resolvedValue = new StringBuilder();

        while (matcher.find()) {
            String envKey = matcher.group(1);
            String envValue = System.getenv(envKey);
            if (envValue == null) {
                throw new DatabaseException(
                        propertyKey + " references environment variable " + envKey
                                + ", but it is not set on this machine."
                );
            }
            matcher.appendReplacement(resolvedValue, Matcher.quoteReplacement(envValue));
        }

        matcher.appendTail(resolvedValue);
        return resolvedValue.toString();
    }

    private static String requireText(String value, String propertyKey) {
        if (!hasText(value)) {
            throw new DatabaseException(propertyKey + " is required. Please update application.properties.");
        }
        return value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
