package com.example.aicodeanalyzer.report;

import com.example.aicodeanalyzer.model.AiAnalysisResult;
import com.example.aicodeanalyzer.model.HandleAccount;
import com.example.aicodeanalyzer.model.Platform;
import com.example.aicodeanalyzer.model.SkillScore;
import com.example.aicodeanalyzer.model.Submission;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Workbook-ready dataset for Excel reports.
 */
public record ExcelReportData(
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDateTime generatedAt,
        List<Platform> platforms,
        List<HandleAccount> handles,
        List<Submission> submissions,
        List<AiAnalysisResult> analyses,
        List<SkillScore> skillScores
) {
    public ExcelReportData {
        platforms = platforms == null ? List.of() : List.copyOf(platforms);
        handles = handles == null ? List.of() : List.copyOf(handles);
        submissions = submissions == null ? List.of() : List.copyOf(submissions);
        analyses = analyses == null ? List.of() : List.copyOf(analyses);
        skillScores = skillScores == null ? List.of() : List.copyOf(skillScores);
    }
}
