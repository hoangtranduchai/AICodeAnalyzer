package com.example.aicodeanalyzer.scheduler;

import com.example.aicodeanalyzer.model.CrawlLog;
import com.example.aicodeanalyzer.repository.AppSettingsRepository;
import com.example.aicodeanalyzer.repository.CrawlLogRepository;

import java.time.LocalTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Persists scheduler configuration and reads the latest crawl execution result.
 */
public class SchedulerSettingsService {
    private static final String AUTO_CRAWL_ENABLED_KEY = "scheduler.auto-crawl-enabled";
    private static final String DAILY_RUN_TIME_KEY = "scheduler.daily-run-time";

    private final AppSettingsRepository appSettingsRepository;
    private final CrawlLogRepository crawlLogRepository;

    public SchedulerSettingsService() {
        this(new AppSettingsRepository(), new CrawlLogRepository());
    }

    public SchedulerSettingsService(
            AppSettingsRepository appSettingsRepository,
            CrawlLogRepository crawlLogRepository
    ) {
        this.appSettingsRepository = Objects.requireNonNull(appSettingsRepository, "appSettingsRepository must not be null");
        this.crawlLogRepository = Objects.requireNonNull(crawlLogRepository, "crawlLogRepository must not be null");
    }

    public SchedulerConfig loadConfig() {
        SchedulerConfig fallback = SchedulerConfig.load();
        try {
            boolean enabled = appSettingsRepository.findValue(AUTO_CRAWL_ENABLED_KEY)
                    .map(Boolean::parseBoolean)
                    .orElse(fallback.autoCrawlEnabled());
            LocalTime runTime = appSettingsRepository.findValue(DAILY_RUN_TIME_KEY)
                    .map(SchedulerConfig::parseTime)
                    .orElse(fallback.dailyRunTime());
            return new SchedulerConfig(enabled, runTime);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    public void saveConfig(SchedulerConfig config) {
        appSettingsRepository.upsertValue(
                AUTO_CRAWL_ENABLED_KEY,
                String.valueOf(config.autoCrawlEnabled()),
                "Enable or disable ScheduledExecutorService daily crawl for new submissions."
        );
        appSettingsRepository.upsertValue(
                DAILY_RUN_TIME_KEY,
                config.dailyRunTimeText(),
                "Daily crawl run time in HH:mm local time."
        );
    }

    public Optional<CrawlLog> latestCrawlLog() {
        return crawlLogRepository.findLatest();
    }
}
