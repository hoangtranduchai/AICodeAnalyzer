package com.example.aicodeanalyzer.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretUtilsTest {

    @Test
    void maskDoesNotExposeShortSecrets() {
        assertEquals("", SecretUtils.mask(null));
        assertEquals("", SecretUtils.mask("   "));
        assertEquals("****", SecretUtils.mask("short"));
        assertEquals("****", SecretUtils.mask("12345678"));
    }

    @Test
    void maskShowsOnlyPrefixAndSuffixForLongSecrets() {
        String secret = "AIzaSy_TEST_SHOULD_NOT_LEAK";

        String masked = SecretUtils.mask(secret);

        assertEquals("AIza****LEAK", masked);
        assertFalse(masked.contains("Sy_TEST_SHOULD_NOT_"));
    }

    @Test
    void hasTextRejectsNullAndWhitespace() {
        assertFalse(SecretUtils.hasText(null));
        assertFalse(SecretUtils.hasText(" \t\n "));
        assertTrue(SecretUtils.hasText(" value "));
    }

    @Test
    void sanitizeMessageMasksCommonSecretPatterns() {
        String sanitized = SecretUtils.sanitizeMessage(
                "password=plain; token=abc123 api_key=AIzaSy_TEST_SHOULD_NOT_LEAK Authorization: Bearer sk-testsecretvalue"
        );

        assertTrue(sanitized.contains("password=****"));
        assertTrue(sanitized.contains("token=****"));
        assertTrue(sanitized.contains("api_key=****"));
        assertTrue(sanitized.contains("Authorization: Bearer ****"));
        assertFalse(sanitized.contains("plain"));
        assertFalse(sanitized.contains("abc123"));
        assertFalse(sanitized.contains("sk-testsecretvalue"));
    }
}
