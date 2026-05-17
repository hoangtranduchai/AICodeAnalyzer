package com.example.aicodeanalyzer.report;

import java.time.LocalDate;
import java.util.List;

/**
 * Carries report export filters.
 */
public record ReportRequest(
        LocalDate periodStart,
        LocalDate periodEnd,
        List<Long> handleIds,
        boolean openAfterExport
) {
    public ReportRequest {
        handleIds = handleIds == null ? List.of() : List.copyOf(handleIds);
    }

    public static ReportRequest of(LocalDate periodStart, LocalDate periodEnd, List<Long> handleIds) {
        return new ReportRequest(periodStart, periodEnd, handleIds, false);
    }
}
