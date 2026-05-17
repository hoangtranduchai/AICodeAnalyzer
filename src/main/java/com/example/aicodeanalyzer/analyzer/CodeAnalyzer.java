package com.example.aicodeanalyzer.analyzer;

import com.example.aicodeanalyzer.model.AiAnalysisResult;
import com.example.aicodeanalyzer.model.SourceCodeDetail;

/**
 * Common contract for source code analyzers.
 */
public interface CodeAnalyzer {
    AiAnalysisResult analyze(SourceCodeDetail sourceCodeDetail);
}
