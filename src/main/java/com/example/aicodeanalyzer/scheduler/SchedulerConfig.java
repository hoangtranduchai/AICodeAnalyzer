package com.example.aicodeanalyzer.scheduler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Properties;

/**
 * Loads optional scheduler defaults from application.properties.
 */
public record SchedulerConfig(boolean autoCrawlEnabled, LocalTime dailyRunTime) {
    private static final String CONFIG_FILE = "application.properties";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public SchedulerConfig {
        dailyRunTime = dailyRunTime == null ? LocalTime.of(1, 0) : dailyRunTime;
    }

    public static SchedulerConfig load() {
        Properties properties = new Properties();

        Path externalPath = explicitConfigPath();
        if (externalPath == null && Files.isRegularFile(Path.of(CONFIG_FILE))) {
            externalPath = Path.of(CONFIG_FILE);
        }
        if (externalPath == null && Files.isRegularFile(Path.of("src", "main", "resources", CONFIG_FILE))) {
            externalPath = Path.of("src", "main", "resources", CONFIG_FILE);
        }

        try (InputStream inputStream = externalPath == null
                ? Thread.currentThread().getContextClassLoader().getResourceAsStream(CONFIG_FILE)
                : Files.newInputStream(externalPath.toAbsolutePath().normalize())) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (IOException ignored) {
            return defaults();
        }

        boolean enabled = Boolean.parseBoolean(properties.getProperty("scheduler.auto-crawl-enabled", "false"));
        LocalTime runTime = parseTime(properties.getProperty("scheduler.daily-run-time", "01:00"));
        return new SchedulerConfig(enabled, runTime);
    }

    public static SchedulerConfig defaults() {
        return new SchedulerConfig(false, LocalTime.of(1, 0));
    }

    public String dailyRunTimeText() {
        return TIME_FORMATTER.format(dailyRunTime);
    }

    public static LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalTime.of(1, 0);
        }
        try {
            return LocalTime.parse(value.trim(), TIME_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Daily crawl time must use HH:mm format, for example 01:00.", ex);
        }
    }

    private static Path explicitConfigPath() {
        String propertyValue = System.getProperty("app.config");
        if (propertyValue != null && !propertyValue.isBlank()) {
            return Path.of(propertyValue.trim());
        }
        String envValue = System.getenv("APP_CONFIG_FILE");
        return envValue == null || envValue.isBlank() ? null : Path.of(envValue.trim());
    }

}
