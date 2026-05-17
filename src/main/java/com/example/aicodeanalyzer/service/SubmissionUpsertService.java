package com.example.aicodeanalyzer.service;

import com.example.aicodeanalyzer.crawler.CrawlResult;
import com.example.aicodeanalyzer.crawler.CrawledSubmission;
import com.example.aicodeanalyzer.crawler.SourceAvailability;
import com.example.aicodeanalyzer.model.HandleAccount;
import com.example.aicodeanalyzer.model.SourceCode;
import com.example.aicodeanalyzer.model.Submission;
import com.example.aicodeanalyzer.repository.SourceCodeRepository;
import com.example.aicodeanalyzer.repository.SubmissionRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/**
 * Enforces duplicate prevention and update rules using platform + remote submission id.
 */
public class SubmissionUpsertService {
    private final SubmissionRepository submissionRepository;
    private final SourceCodeRepository sourceCodeRepository;

    public SubmissionUpsertService() {
        this(new SubmissionRepository(), new SourceCodeRepository());
    }

    public SubmissionUpsertService(
            SubmissionRepository submissionRepository,
            SourceCodeRepository sourceCodeRepository
    ) {
        this.submissionRepository = Objects.requireNonNull(submissionRepository, "submissionRepository must not be null");
        this.sourceCodeRepository = Objects.requireNonNull(sourceCodeRepository, "sourceCodeRepository must not be null");
    }

    public CrawlResult upsertCrawlResult(HandleAccount handleAccount, CrawlResult crawlResult) {
        Objects.requireNonNull(handleAccount, "handleAccount must not be null");
        Objects.requireNonNull(crawlResult, "crawlResult must not be null");

        int newCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        for (CrawledSubmission crawledSubmission : crawlResult.submissions()) {
            try {
                UpsertAction action = upsert(handleAccount, crawlResult.platformCode(), crawledSubmission);
                switch (action) {
                    case CREATED -> newCount++;
                    case UPDATED -> updatedCount++;
                    case SKIPPED -> skippedCount++;
                }
            } catch (RuntimeException ex) {
                failedCount++;
            }
        }

        return crawlResult.withStatistics(newCount, updatedCount, skippedCount, failedCount);
    }

    public UpsertAction upsert(
            HandleAccount handleAccount,
            String platformCode,
            CrawledSubmission crawledSubmission
    ) {
        validate(handleAccount, platformCode, crawledSubmission);

        Optional<Submission> existingSubmission = submissionRepository.findByPlatformAndRemoteSubmissionId(
                normalizePlatformCode(platformCode),
                crawledSubmission.platformSubmissionId()
        );

        if (existingSubmission.isEmpty()) {
            Submission savedSubmission = submissionRepository.save(crawledSubmission.toSubmission(handleAccount.getHandleId()));
            sourceCodeRepository.save(prepareSourceCode(crawledSubmission, savedSubmission.getSubmissionId()));
            updateSourceCrawlStatus(savedSubmission.getSubmissionId(), crawledSubmission);
            return UpsertAction.CREATED;
        }

        Submission existing = existingSubmission.get();
        Submission incoming = crawledSubmission.toSubmission(handleAccount.getHandleId());
        boolean metadataChanged = applyMetadataChanges(existing, incoming);
        boolean sourceChanged = upsertSourceCode(existing.getSubmissionId(), crawledSubmission);

        if (metadataChanged) {
            submissionRepository.update(existing);
        }
        updateSourceCrawlStatus(existing.getSubmissionId(), crawledSubmission);

        return metadataChanged || sourceChanged ? UpsertAction.UPDATED : UpsertAction.SKIPPED;
    }

    private boolean applyMetadataChanges(Submission existing, Submission incoming) {
        boolean changed = false;
        changed |= setIfChanged(existing::getHandleId, existing::setHandleId, incoming.getHandleId());
        changed |= setIfChanged(existing::getProblemCode, existing::setProblemCode, incoming.getProblemCode());
        changed |= setIfChanged(existing::getProblemName, existing::setProblemName, incoming.getProblemName());
        changed |= setIfChanged(existing::getContestId, existing::setContestId, incoming.getContestId());
        changed |= setIfChanged(existing::getLanguage, existing::setLanguage, incoming.getLanguage());
        changed |= setIfChanged(existing::getVerdict, existing::setVerdict, incoming.getVerdict());
        changed |= setIfChanged(existing::getSubmittedAt, existing::setSubmittedAt, incoming.getSubmittedAt());
        changed |= setIfChanged(existing::getExecutionTimeMs, existing::setExecutionTimeMs, incoming.getExecutionTimeMs());
        changed |= setIfChanged(existing::getMemoryBytes, existing::setMemoryBytes, incoming.getMemoryBytes());
        changed |= setIfChanged(existing::getProblemRating, existing::setProblemRating, incoming.getProblemRating());
        changed |= setIfChanged(existing::getProblemTags, existing::setProblemTags, incoming.getProblemTags());
        changed |= setIfChanged(existing::getSourceUrl, existing::setSourceUrl, incoming.getSourceUrl());
        return changed;
    }

    private boolean upsertSourceCode(long submissionId, CrawledSubmission crawledSubmission) {
        SourceCode incoming = prepareSourceCode(crawledSubmission, submissionId);
        Optional<SourceCode> existingSource = sourceCodeRepository.findBySubmissionId(submissionId);
        if (existingSource.isEmpty()) {
            sourceCodeRepository.save(incoming);
            return true;
        }

        SourceCode existing = existingSource.get();
        if (!shouldUpdateSource(existing, incoming)) {
            return false;
        }

        existing.setCodeContent(incoming.getCodeContent());
        existing.setCodeHash(incoming.getCodeHash());
        existing.setLineCount(incoming.getLineCount());
        existing.setCharCount(incoming.getCharCount());
        existing.setFetchedAt(LocalDateTime.now());
        existing.setStorageType(incoming.getStorageType());
        existing.setEncrypted(incoming.isEncrypted());
        sourceCodeRepository.update(existing);
        return true;
    }

    private boolean shouldUpdateSource(SourceCode existing, SourceCode incoming) {
        boolean incomingHasSource = hasText(incoming.getCodeContent());
        boolean existingHasSource = hasText(existing.getCodeContent());

        if (!incomingHasSource && existingHasSource) {
            return false;
        }
        if (incomingHasSource && !existingHasSource) {
            return true;
        }
        if (incomingHasSource) {
            return !Objects.equals(existing.getCodeHash(), incoming.getCodeHash())
                    || !Objects.equals(existing.getCodeContent(), incoming.getCodeContent());
        }
        return !Objects.equals(existing.getStorageType(), incoming.getStorageType());
    }

    private SourceCode prepareSourceCode(CrawledSubmission crawledSubmission, long submissionId) {
        SourceCode sourceCode = crawledSubmission.toSourceCode(submissionId);
        if (hasText(sourceCode.getCodeContent())) {
            if (!hasText(sourceCode.getStorageType()) || "UNKNOWN".equalsIgnoreCase(sourceCode.getStorageType())) {
                sourceCode.setStorageType("DATABASE");
            }
            sourceCode.setLineCount(countLines(sourceCode.getCodeContent()));
            sourceCode.setCharCount(sourceCode.getCodeContent().length());
            sourceCode.setCodeHash(sha256(sourceCode.getCodeContent()));
        } else {
            if (!hasText(sourceCode.getStorageType())) {
                sourceCode.setStorageType(SourceAvailability.SOURCE_NOT_AVAILABLE.name());
            }
            sourceCode.setLineCount(0);
            sourceCode.setCharCount(0);
            sourceCode.setCodeHash(null);
        }
        if (sourceCode.getFetchedAt() == null) {
            sourceCode.setFetchedAt(LocalDateTime.now());
        }
        return sourceCode;
    }

    private void updateSourceCrawlStatus(long submissionId, CrawledSubmission crawledSubmission) {
        String status = switch (crawledSubmission.sourceAvailability()) {
            case AVAILABLE -> "CRAWLED";
            case SOURCE_NOT_AVAILABLE -> "SKIPPED";
            default -> "FAILED";
        };
        String error = "CRAWLED".equals(status) ? null : truncate(crawledSubmission.sourceUnavailableReason(), 1000);
        submissionRepository.updateSourceCrawlStatus(submissionId, status, LocalDateTime.now(), error);
    }

    private <T> boolean setIfChanged(ValueGetter<T> getter, ValueSetter<T> setter, T incomingValue) {
        if (incomingValue == null) {
            return false;
        }
        if (Objects.equals(getter.get(), incomingValue)) {
            return false;
        }
        setter.set(incomingValue);
        return true;
    }

    private void validate(HandleAccount handleAccount, String platformCode, CrawledSubmission crawledSubmission) {
        if (handleAccount.getHandleId() == null) {
            throw new IllegalArgumentException("handle id is required for submission upsert.");
        }
        if (!hasText(platformCode)) {
            throw new IllegalArgumentException("platform code is required for submission upsert.");
        }
        if (crawledSubmission == null || !hasText(crawledSubmission.platformSubmissionId())) {
            throw new IllegalArgumentException("remote submission id is required for submission upsert.");
        }
    }

    private String normalizePlatformCode(String platformCode) {
        return platformCode.trim().toUpperCase();
    }

    private int countLines(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.split("\\R", -1).length;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available in this Java runtime.", ex);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public enum UpsertAction {
        CREATED,
        UPDATED,
        SKIPPED
    }

    @FunctionalInterface
    private interface ValueGetter<T> {
        T get();
    }

    @FunctionalInterface
    private interface ValueSetter<T> {
        void set(T value);
    }
}
