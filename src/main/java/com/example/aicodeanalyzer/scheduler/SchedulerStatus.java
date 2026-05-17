package com.example.aicodeanalyzer.scheduler;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Current scheduler state displayed in the JavaFX settings screen.
 */
public record SchedulerStatus(
        boolean started,
        boolean autoCrawlEnabled,
        LocalTime dailyRunTime,
        LocalDateTime lastManualTriggerAt
) {
}
