package com.example.aicodeanalyzer.report;

import com.example.aicodeanalyzer.model.SkillScore;

import java.util.List;

/**
 * Aggregated report row for one handle.
 */
public record HandleReportRow(
        long handleId,
        String platformName,
        String handle,
        int totalSubmissions,
        int acceptedSubmissions,
        double acceptedRate,
        int analyzedSourceCount,
        List<String> algorithms,
        List<String> dataStructures,
        SkillScore skillScore,
        String feedback
) {
    public HandleReportRow {
        algorithms = algorithms == null ? List.of() : List.copyOf(algorithms);
        dataStructures = dataStructures == null ? List.of() : List.copyOf(dataStructures);
    }
}
