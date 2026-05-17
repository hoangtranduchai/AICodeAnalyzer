package com.example.aicodeanalyzer.analyzer;

import java.util.List;

/**
 * Probability-oriented result for AI-use heuristics. It is never a definitive authorship decision.
 */
public record AIDetectionResult(
        int aiGeneratedProbability,
        List<String> evidence,
        List<String> warnings,
        int confidence
) {
    public AIDetectionResult {
        aiGeneratedProbability = clamp(aiGeneratedProbability);
        confidence = clamp(confidence);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
