package com.example.aicodeanalyzer.service;

import com.example.aicodeanalyzer.model.AiAnalysisResult;
import com.example.aicodeanalyzer.model.SourceCode;
import com.example.aicodeanalyzer.repository.SourceCodeRepository;

import java.util.Comparator;
import java.util.List;

/**
 * Manual smoke test for analyzing one source code row and saving the result.
 * Smoke test thủ công để phân tích một source code trong DB và lưu kết quả AI/rule.
 */
public final class GeminiAnalysisSmokeTestMain {
    private GeminiAnalysisSmokeTestMain() {
    }

    public static void main(String[] args) {
        String target = args == null || args.length == 0 ? "latest" : args[0].trim();
        long sourceCodeId = resolveSourceCodeId(target);

        AiAnalysisResult result = new AnalysisService().analyzeSourceCode(sourceCodeId);
        System.out.printf(
                "analysis_id=%s | submission_id=%s | analyzer=%s | model=%s | data_structures=%s | algorithms=%s | ai_risk=%s%n",
                result.getAnalysisId(),
                result.getSubmissionId(),
                result.getAnalyzerType(),
                result.getModelName(),
                nullToDash(result.getDataStructures()),
                nullToDash(result.getAlgorithms()),
                result.getAiRiskScore()
        );
    }

    private static long resolveSourceCodeId(String target) {
        if (target == null || target.isBlank() || "latest".equalsIgnoreCase(target)) {
            List<SourceCode> sourceCodes = new SourceCodeRepository().findAll();
            return sourceCodes.stream()
                    .filter(sourceCode -> sourceCode.getCodeContent() != null && !sourceCode.getCodeContent().isBlank())
                    .max(Comparator.comparing(SourceCode::getSourceCodeId))
                    .map(SourceCode::getSourceCodeId)
                    .orElseThrow(() -> new IllegalStateException("No source code with content exists in the database."));
        }
        try {
            return Long.parseLong(target);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Expected source_code_id or latest, got: " + target, ex);
        }
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
