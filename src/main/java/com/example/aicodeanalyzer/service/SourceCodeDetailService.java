package com.example.aicodeanalyzer.service;

import com.example.aicodeanalyzer.model.SourceCodeDetail;
import com.example.aicodeanalyzer.repository.SourceCodeDetailRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Provides source code details for the JavaFX source viewer screen.
 */
public class SourceCodeDetailService {
    private static final int DEFAULT_RECENT_LIMIT = 100;

    private final SourceCodeDetailRepository sourceCodeDetailRepository;

    public SourceCodeDetailService() {
        this(new SourceCodeDetailRepository());
    }

    public SourceCodeDetailService(SourceCodeDetailRepository sourceCodeDetailRepository) {
        this.sourceCodeDetailRepository = Objects.requireNonNull(
                sourceCodeDetailRepository,
                "sourceCodeDetailRepository must not be null"
        );
    }

    public List<SourceCodeDetail> findRecentSourceCodes() {
        return sourceCodeDetailRepository.findRecentMetadata(DEFAULT_RECENT_LIMIT);
    }

    public Optional<SourceCodeDetail> findBySourceCodeId(long sourceCodeId) {
        return sourceCodeDetailRepository.findBySourceCodeId(sourceCodeId);
    }
}
