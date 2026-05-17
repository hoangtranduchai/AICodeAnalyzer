package com.example.aicodeanalyzer.config;

import com.example.aicodeanalyzer.util.SecretUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class ConfigResourceLoader {
    private static final String CONFIG_PROPERTY = "app.config";
    private static final String CONFIG_ENV = "APP_CONFIG_FILE";
    private static final String DEFAULT_EXTERNAL_FILE = "application.properties";
    private static final String SAFE_CLASSPATH_FALLBACK = "application.properties.example";

    private ConfigResourceLoader() {
    }

    static Properties loadProperties(String requestedResourceName) throws IOException {
        Properties properties = new Properties();

        Path explicitPath = explicitConfigPath();
        if (explicitPath != null) {
            loadExternal(properties, explicitPath);
            return properties;
        }

        Path localPath = Path.of(DEFAULT_EXTERNAL_FILE);
        if (Files.isRegularFile(localPath)) {
            loadExternal(properties, localPath);
            return properties;
        }

        Path developmentResourcePath = Path.of("src", "main", "resources", DEFAULT_EXTERNAL_FILE);
        if (Files.isRegularFile(developmentResourcePath)) {
            loadExternal(properties, developmentResourcePath);
            return properties;
        }

        String classpathResource = SAFE_CLASSPATH_FALLBACK;
        if (!DEFAULT_EXTERNAL_FILE.equals(requestedResourceName)) {
            classpathResource = requestedResourceName;
        }
        loadClasspath(properties, classpathResource);
        return properties;
    }

    private static Path explicitConfigPath() {
        String propertyValue = System.getProperty(CONFIG_PROPERTY);
        if (SecretUtils.hasText(propertyValue)) {
            return Path.of(propertyValue.trim());
        }

        String envValue = SecretUtils.env(CONFIG_ENV);
        if (SecretUtils.hasText(envValue)) {
            return Path.of(envValue);
        }

        return null;
    }

    private static void loadExternal(Properties properties, Path path) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path.toAbsolutePath().normalize())) {
            properties.load(inputStream);
        }
    }

    private static void loadClasspath(Properties properties, String resourceName) throws IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(resourceName)) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        }
    }
}
