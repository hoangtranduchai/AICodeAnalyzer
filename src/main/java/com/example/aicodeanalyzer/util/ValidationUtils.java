package com.example.aicodeanalyzer.util;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Provides reusable validation helpers for handles, URLs, scores, and user inputs.
 */
public final class ValidationUtils {
    private static final int MAX_HANDLE_LENGTH = 100;
    private static final Pattern HANDLE_PATTERN = Pattern.compile("[A-Za-z0-9_.-]+");

    private ValidationUtils() {
    }

    public static String normalizePlatformCode(String platformCode) {
        String value = requireText(platformCode, "Platform is required.").trim();
        if ("Codeforces".equalsIgnoreCase(value)) {
            return "CODEFORCES";
        }
        if ("VJudge".equalsIgnoreCase(value)) {
            return "VJUDGE";
        }
        return value.toUpperCase(Locale.ROOT);
    }

    public static String normalizeHandle(String handle) {
        String value = requireText(handle, "Handle cannot be empty.").trim();
        if (value.length() > MAX_HANDLE_LENGTH) {
            throw new IllegalArgumentException("Handle cannot be longer than " + MAX_HANDLE_LENGTH + " characters.");
        }
        if (!HANDLE_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Handle can only contain letters, numbers, dot, underscore, and hyphen.");
        }
        return value;
    }

    public static int requireScoreInRange(int score, String fieldName) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException(fieldName + " must be from 0 to 100.");
        }
        return score;
    }

    public static String requireText(String value, String message) {
        if (!SecretUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    public static boolean isSafeHttpUrl(String value) {
        if (!SecretUtils.hasText(value)) {
            return false;
        }
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && SecretUtils.hasText(uri.getHost());
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
