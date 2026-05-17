package com.example.aicodeanalyzer.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Report-ready dataset for handle evaluation.
 */
public record EvaluationReportData(
        String title,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDateTime generatedAt,
        List<HandleReportRow> handles
) {
    public EvaluationReportData {
        handles = handles == null ? List.of() : List.copyOf(handles);
    }
}
