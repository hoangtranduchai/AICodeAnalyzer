package com.example.aicodeanalyzer.analyzer;

import com.example.aicodeanalyzer.model.AiAnalysisResult;
import com.example.aicodeanalyzer.model.SourceCodeDetail;
import com.example.aicodeanalyzer.service.GeminiAnalyzerService;

import java.util.Objects;

/**
 * CodeAnalyzer adapter for the direct Google Gemini REST analyzer.
 */
public class GeminiCodeAnalyzer implements CodeAnalyzer {
    private final GeminiAnalyzerService geminiAnalyzerService;

    public GeminiCodeAnalyzer() {
        this(new GeminiAnalyzerService());
    }

    public GeminiCodeAnalyzer(GeminiAnalyzerService geminiAnalyzerService) {
        this.geminiAnalyzerService = Objects.requireNonNull(
                geminiAnalyzerService,
                "geminiAnalyzerService must not be null"
        );
    }

    @Override
    public AiAnalysisResult analyze(SourceCodeDetail sourceCodeDetail) {
        Objects.requireNonNull(sourceCodeDetail, "sourceCodeDetail must not be null");
        return geminiAnalyzerService.analyzeForSubmission(
                sourceCodeDetail.submissionId(),
                sourceCodeDetail.codeContent()
        );
    }
}
