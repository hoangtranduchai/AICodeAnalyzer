package com.example.aicodeanalyzer.util;

/**
 * Reads secrets from environment variables and masks sensitive values before logging.
 */
public class SecretUtils {
    private static final int VISIBLE_PREFIX_LENGTH = 4;
    private static final int VISIBLE_SUFFIX_LENGTH = 4;

    private SecretUtils() {
    }

    public static String env(String key) {
        if (!hasText(key)) {
            return "";
        }
        String value = System.getenv(key.trim());
        return value == null ? "" : value.trim();
    }

    public static String mask(String secret) {
        if (!hasText(secret)) {
            return "";
        }

        String trimmedSecret = secret.trim();
        if (trimmedSecret.length() <= VISIBLE_PREFIX_LENGTH + VISIBLE_SUFFIX_LENGTH) {
            return "****";
        }

        return trimmedSecret.substring(0, VISIBLE_PREFIX_LENGTH)
                + "****"
                + trimmedSecret.substring(trimmedSecret.length() - VISIBLE_SUFFIX_LENGTH);
    }

    public static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static String sanitizeMessage(String message) {
        if (!hasText(message)) {
            return "";
        }
        return message
                .replaceAll("(?i)(password\\s*=\\s*)[^;\\s]+", "$1****")
                .replaceAll("(?i)(pwd\\s*=\\s*)[^;\\s]+", "$1****")
                .replaceAll("(?i)(token\\s*=\\s*)[^;\\s]+", "$1****")
                .replaceAll("(?i)(api[_-]?key\\s*=\\s*)[^;\\s]+", "$1****")
                .replaceAll("(?i)(authorization:\\s*bearer\\s+)[^;\\s]+", "$1****")
                .replaceAll("AIza[0-9A-Za-z_\\-]{20,}", "AIza****")
                .replaceAll("sk-[0-9A-Za-z_\\-]{20,}", "sk-****");
    }
}
