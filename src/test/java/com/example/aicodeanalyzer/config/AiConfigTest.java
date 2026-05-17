package com.example.aicodeanalyzer.config;

import com.example.aicodeanalyzer.exception.AnalyzerException;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiConfigTest {

    @Test
    void defaultsToGeminiProviderAndGenerateContentEndpoint() {
        AiConfig config = AiConfig.fromProperties(new Properties());

        assertEquals("gemini-rest", config.provider());
        assertEquals("GEMINI_API_KEY", config.apiKeyEnv());
        assertEquals("gemini-2.5-flash", config.model());
        assertEquals(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
                config.endpoint().toString()
        );
        assertTrue(config.isGeminiProvider());
        assertFalse(config.isOpenAiProvider());
        assertEquals(!config.hasApiKey(), config.useMockMode());
    }

    @Test
    void openAiProviderKeepsOpenAiDefaultsForBackwardCompatibility() {
        Properties properties = new Properties();
        properties.setProperty("ai.provider", "openai-rest");
        properties.setProperty("ai.api-key", "sk-test");

        AiConfig config = AiConfig.fromProperties(properties);

        assertEquals("OPENAI_API_KEY", config.apiKeyEnv());
        assertEquals("gpt-4.1-mini", config.model());
        assertEquals("https://api.openai.com/v1/responses", config.endpoint().toString());
        assertTrue(config.isOpenAiProvider());
        assertFalse(config.useMockMode());
    }

    @Test
    void explicitGeminiModelUpdatesDefaultEndpoint() {
        Properties properties = new Properties();
        properties.setProperty("ai.provider", "gemini-rest");
        properties.setProperty("ai.model", "gemini-test-model");

        AiConfig config = AiConfig.fromProperties(properties);

        assertEquals(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-test-model:generateContent",
                config.endpoint().toString()
        );
    }

    @Test
    void rejectsInvalidTimeoutAndRetryValues() {
        Properties invalidTimeout = new Properties();
        invalidTimeout.setProperty("ai.timeout-seconds", "0");

        Properties invalidRetries = new Properties();
        invalidRetries.setProperty("ai.max-retries", "-1");

        assertThrows(AnalyzerException.class, () -> AiConfig.fromProperties(invalidTimeout));
        assertThrows(AnalyzerException.class, () -> AiConfig.fromProperties(invalidRetries));
    }
}
