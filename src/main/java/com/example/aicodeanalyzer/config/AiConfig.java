package com.example.aicodeanalyzer.config;

import com.example.aicodeanalyzer.exception.AnalyzerException;
import com.example.aicodeanalyzer.util.SecretUtils;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Properties;

/**
 * Loads AI provider settings such as endpoint, model name, timeout, and API key reference.
 */
public record AiConfig(
        String provider,
        String apiKeyEnv,
        String apiKey,
        String model,
        URI endpoint,
        Duration timeout,
        int maxRetries,
        boolean mockMode
) {
    private static final String DEFAULT_CONFIG_FILE = "application.properties";
    private static final String DEFAULT_PROVIDER = "gemini-rest";
    private static final String DEFAULT_GEMINI_API_KEY_ENV = "GEMINI_API_KEY";
    private static final String DEFAULT_GEMINI_MODEL = "gemini-2.5-flash";
    private static final String DEFAULT_OPENAI_API_KEY_ENV = "OPENAI_API_KEY";
    private static final String DEFAULT_OPENAI_MODEL = "gpt-4.1-mini";
    private static final String DEFAULT_OPENAI_ENDPOINT = "https://api.openai.com/v1/responses";
    private static final int DEFAULT_TIMEOUT_SECONDS = 45;
    private static final int DEFAULT_MAX_RETRIES = 3;

    public AiConfig {
        provider = textOrDefault(provider, DEFAULT_PROVIDER);
        ProviderDefaults defaults = defaultsForProvider(provider);
        apiKeyEnv = textOrDefault(apiKeyEnv, defaults.apiKeyEnv());
        apiKey = Objects.requireNonNullElse(apiKey, "").trim();
        model = textOrDefault(model, defaults.model());
        endpoint = endpoint == null ? URI.create(defaultEndpoint(provider, model)) : endpoint;
        timeout = timeout == null ? Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS) : timeout;
        if (timeout.isZero() || timeout.isNegative()) {
            throw new AnalyzerException("ai.timeout-seconds must be greater than 0.");
        }
        if (maxRetries < 1) {
            throw new AnalyzerException("ai.max-retries must be at least 1.");
        }
    }

    public static AiConfig load() {
        return load(DEFAULT_CONFIG_FILE);
    }

    public static AiConfig load(String resourceName) {
        try {
            Properties properties = ConfigResourceLoader.loadProperties(resourceName);
            return fromProperties(properties);
        } catch (IOException ex) {
            throw new AnalyzerException(
                    "Cannot read application configuration for AI settings. Use -Dapp.config or APP_CONFIG_FILE.",
                    ex
            );
        }
    }

    public static AiConfig fromProperties(Properties properties) {
        String provider = get(properties, "ai.provider", DEFAULT_PROVIDER);
        ProviderDefaults defaults = defaultsForProvider(provider);
        String apiKeyEnv = get(properties, "ai.api-key-env", defaults.apiKeyEnv());
        String apiKey = resolveApiKey(properties, apiKeyEnv, provider);
        String model = firstText(
                SecretUtils.env("AI_MODEL"),
                firstText(
                        providerSpecificEnv(provider, "MODEL"),
                        get(properties, "ai.model", defaults.model())
                )
        );
        URI endpoint = URI.create(firstText(
                SecretUtils.env("AI_API_ENDPOINT"),
                firstText(
                        providerSpecificEnv(provider, "API_ENDPOINT"),
                        get(properties, "ai.endpoint", defaultEndpoint(provider, model))
                )
        ));
        Duration timeout = Duration.ofSeconds(getPositiveInt(
                properties,
                "ai.timeout-seconds",
                DEFAULT_TIMEOUT_SECONDS
        ));
        int maxRetries = getPositiveInt(properties, "ai.max-retries", DEFAULT_MAX_RETRIES);
        boolean mockMode = Boolean.parseBoolean(get(properties, "ai.mock-mode", "false"));

        return new AiConfig(provider, apiKeyEnv, apiKey, model, endpoint, timeout, maxRetries, mockMode);
    }

    public boolean hasApiKey() {
        return SecretUtils.hasText(apiKey);
    }

    public boolean useMockMode() {
        return mockMode || !hasApiKey();
    }

    public boolean isGeminiProvider() {
        String normalized = provider.toLowerCase();
        return normalized.contains("gemini") || normalized.contains("google");
    }

    public boolean isOpenAiProvider() {
        String normalized = provider.toLowerCase();
        return normalized.contains("openai");
    }

    private static String resolveApiKey(Properties properties, String apiKeyEnv, String provider) {
        String envValue = SecretUtils.env(apiKeyEnv);
        if (SecretUtils.hasText(envValue)) {
            return envValue;
        }
        if (isGeminiProvider(provider)) {
            String geminiEnvValue = SecretUtils.env(DEFAULT_GEMINI_API_KEY_ENV);
            if (SecretUtils.hasText(geminiEnvValue)) {
                return geminiEnvValue;
            }
        }
        if (isOpenAiProvider(provider)) {
            String openAiEnvValue = SecretUtils.env(DEFAULT_OPENAI_API_KEY_ENV);
            if (SecretUtils.hasText(openAiEnvValue)) {
                return openAiEnvValue;
            }
        }
        return get(properties, "ai.api-key", "");
    }

    private static String get(Properties properties, String key, String defaultValue) {
        String value = properties.getProperty(key);
        return SecretUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private static int getPositiveInt(Properties properties, String key, int defaultValue) {
        String rawValue = properties.getProperty(key);
        if (!SecretUtils.hasText(rawValue)) {
            return defaultValue;
        }

        try {
            int value = Integer.parseInt(rawValue.trim());
            if (value <= 0) {
                throw new NumberFormatException("Value must be positive.");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new AnalyzerException(key + " must be a positive integer.", ex);
        }
    }

    private static String firstText(String first, String second) {
        return SecretUtils.hasText(first) ? first.trim() : textOrDefault(second, "");
    }

    private static String textOrDefault(String value, String defaultValue) {
        return SecretUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private static String providerSpecificEnv(String provider, String suffix) {
        if (isGeminiProvider(provider)) {
            return SecretUtils.env("GEMINI_" + suffix);
        }
        if (isOpenAiProvider(provider)) {
            return SecretUtils.env("OPENAI_" + suffix);
        }
        return "";
    }

    private static ProviderDefaults defaultsForProvider(String provider) {
        if (isOpenAiProvider(provider)) {
            return new ProviderDefaults(DEFAULT_OPENAI_API_KEY_ENV, DEFAULT_OPENAI_MODEL);
        }
        return new ProviderDefaults(DEFAULT_GEMINI_API_KEY_ENV, DEFAULT_GEMINI_MODEL);
    }

    private static String defaultEndpoint(String provider, String model) {
        if (isOpenAiProvider(provider)) {
            return DEFAULT_OPENAI_ENDPOINT;
        }
        return "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent".formatted(model);
    }

    private static boolean isGeminiProvider(String provider) {
        String normalized = textOrDefault(provider, DEFAULT_PROVIDER).toLowerCase();
        return normalized.contains("gemini") || normalized.contains("google");
    }

    private static boolean isOpenAiProvider(String provider) {
        return textOrDefault(provider, DEFAULT_PROVIDER).toLowerCase().contains("openai");
    }

    private record ProviderDefaults(String apiKeyEnv, String model) {
    }
}
