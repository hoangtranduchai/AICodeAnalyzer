package com.example.aicodeanalyzer.analyzer;

import com.example.aicodeanalyzer.model.AiAnalysisResult;
import com.example.aicodeanalyzer.model.SourceCodeDetail;
import com.example.aicodeanalyzer.service.OpenAIAnalyzerService;

import java.util.Objects;

/**
 * Calls an AI REST API to generate structured source code analysis.
 */
public class OpenAiCodeAnalyzer implements CodeAnalyzer {
    private final OpenAIAnalyzerService openAIAnalyzerService;

    public OpenAiCodeAnalyzer() {
        this(new OpenAIAnalyzerService());
    }

    public OpenAiCodeAnalyzer(OpenAIAnalyzerService openAIAnalyzerService) {
        this.openAIAnalyzerService = Objects.requireNonNull(
                openAIAnalyzerService,
                "openAIAnalyzerService must not be null"
        );
    }

    @Override
    public AiAnalysisResult analyze(SourceCodeDetail sourceCodeDetail) {
        return openAIAnalyzerService.analyze(sourceCodeDetail);
    }
}
