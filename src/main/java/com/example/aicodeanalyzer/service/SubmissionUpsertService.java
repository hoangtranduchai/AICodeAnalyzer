package com.example.aicodeanalyzer.service;

import com.example.aicodeanalyzer.crawler.CrawlResult;
import com.example.aicodeanalyzer.crawler.CrawledSubmission;
import com.example.aicodeanalyzer.crawler.SourceAvailability;
import com.example.aicodeanalyzer.model.HandleAccount;
import com.example.aicodeanalyzer.model.SourceCode;
import com.example.aicodeanalyzer.model.Submission;
import com.example.aicodeanalyzer.repository.AnalysisJobRepository;
import com.example.aicodeanalyzer.repository.SourceCodeRepository;
import com.example.aicodeanalyzer.repository.SubmissionRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Enforces duplicate prevention and update rules using platform + remote submission id.
 */
public class SubmissionUpsertService {
    private final SubmissionRepository submissionRepository;
    private final SourceCodeRepository sourceCodeRepository;
    private final AnalysisJobRepository analysisJobRepository;
    private final List<Consumer<String>> progressListeners = new CopyOnWriteArrayList<>();

    public SubmissionUpsertService() {
        this(new SubmissionRepository(), new SourceCodeRepository(), new AnalysisJobRepository());
    }

    public SubmissionUpsertService(
            SubmissionRepository submissionRepository,
            SourceCodeRepository sourceCodeRepository
    ) {
        this(submissionRepository, sourceCodeRepository, new AnalysisJobRepository());
    }

    public SubmissionUpsertService(
            SubmissionRepository submissionRepository,
            SourceCodeRepository sourceCodeRepository,
            AnalysisJobRepository analysisJobRepository
    ) {
        this.submissionRepository = Objects.requireNonNull(submissionRepository, "submissionRepository must not be null");
        this.sourceCodeRepository = Objects.requireNonNull(sourceCodeRepository, "sourceCodeRepository must not be null");
        this.analysisJobRepository = Objects.requireNonNull(
                analysisJobRepository,
                "analysisJobRepository must not be null"
        );
    }

    public CrawlResult upsertCrawlResult(HandleAccount handleAccount, CrawlResult crawlResult) {
        Objects.requireNonNull(handleAccount, "handleAccount must not be null");
        Objects.requireNonNull(crawlResult, "crawlResult must not be null");

        emitProgress("Persisting crawl result for " + crawlResult.platformCode() + "/" + crawlResult.handle()
                + ". fetchedSubmissions=" + crawlResult.submissions().size() + ".");
        int newCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        for (CrawledSubmission crawledSubmission : crawlResult.submissions()) {
            try {
                UpsertAction action = upsert(handleAccount, crawlResult.platformCode(), crawledSubmission);
                emitProgress("Persisted remote_id=" + crawledSubmission.platformSubmissionId()
                        + " action=" + action + ".");
                switch (action) {
                    case CREATED -> newCount++;
                    case UPDATED -> updatedCount++;
                    case SKIPPED -> skippedCount++;
                }
            } catch (RuntimeException ex) {
                failedCount++;
                emitProgress("Persist failed for remote_id=" + safeRemoteId(crawledSubmission)
                        + ". Reason: " + sanitizeMessage(ex.getMessage()));
            }
        }

        emitProgress("Persist summary for " + crawlResult.platformCode() + "/" + crawlResult.handle()
                + ". new=" + newCount
                + ", updated=" + updatedCount
                + ", skipped=" + skippedCount
                + ", failed=" + failedCount + ".");
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
            emitProgress("Creating submission remote_id=" + crawledSubmission.platformSubmissionId()
                    + ", problem=" + blankToDash(crawledSubmission.problemCode())
                    + ", verdict=" + blankToDash(crawledSubmission.verdict()) + ".");
            Submission savedSubmission = submissionRepository.save(crawledSubmission.toSubmission(handleAccount.getHandleId()));
            SourceCode savedSource = sourceCodeRepository.save(prepareSourceCode(crawledSubmission, savedSubmission.getSubmissionId()));
            updateSourceCrawlStatus(savedSubmission.getSubmissionId(), crawledSubmission);
            markAnalysisPendingIfSourceHasCode(savedSource);
            return UpsertAction.CREATED;
        }

        Submission existing = existingSubmission.get();
        emitProgress("Found existing submission_id=" + existing.getSubmissionId()
                + " for remote_id=" + crawledSubmission.platformSubmissionId() + ". Checking metadata/source changes.");
        Submission incoming = crawledSubmission.toSubmission(handleAccount.getHandleId());
        boolean metadataChanged = applyMetadataChanges(existing, incoming);
        boolean sourceChanged = upsertSourceCode(existing.getSubmissionId(), crawledSubmission);

        if (metadataChanged) {
            emitProgress("Updating metadata for submission_id=" + existing.getSubmissionId()
                    + ", remote_id=" + crawledSubmission.platformSubmissionId() + ".");
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
            emitProgress("Saving source row for submission_id=" + submissionId
                    + ", availability=" + crawledSubmission.sourceAvailability()
                    + ", lines=" + incoming.getLineCount()
                    + ", chars=" + incoming.getCharCount() + ".");
            SourceCode savedSource = sourceCodeRepository.save(incoming);
            markAnalysisPendingIfSourceHasCode(savedSource);
            return true;
        }

        SourceCode existing = existingSource.get();
        if (!shouldUpdateSource(existing, incoming)) {
            emitProgress("Source unchanged for submission_id=" + submissionId
                    + ", availability=" + crawledSubmission.sourceAvailability() + ".");
            return false;
        }

        emitProgress("Updating source row for submission_id=" + submissionId
                + ", source_code_id=" + existing.getSourceCodeId()
                + ", availability=" + crawledSubmission.sourceAvailability()
                + ", lines=" + incoming.getLineCount()
                + ", chars=" + incoming.getCharCount() + ".");
        existing.setCodeContent(incoming.getCodeContent());
        existing.setCodeHash(incoming.getCodeHash());
        existing.setLineCount(incoming.getLineCount());
        existing.setCharCount(incoming.getCharCount());
        existing.setFetchedAt(LocalDateTime.now());
        existing.setStorageType(incoming.getStorageType());
        existing.setEncrypted(incoming.isEncrypted());
        sourceCodeRepository.update(existing);
        markAnalysisPendingIfSourceHasCode(existing);
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
        emitProgress("Updating source crawl status for submission_id=" + submissionId
                + ": status=" + status
                + ", availability=" + crawledSubmission.sourceAvailability()
                + (error == null ? "." : ", reason=" + sanitizeMessage(error) + "."));
        submissionRepository.updateSourceCrawlStatus(submissionId, status, LocalDateTime.now(), error);
    }

    private void markAnalysisPendingIfSourceHasCode(SourceCode sourceCode) {
        if (sourceCode == null
                || sourceCode.getSourceCodeId() == null
                || sourceCode.getSubmissionId() == null
                || !hasText(sourceCode.getCodeContent())) {
            return;
        }

        try {
            analysisJobRepository.markPending(sourceCode.getSourceCodeId(), sourceCode.getSubmissionId());
            emitProgress("Queued AI analysis job source_code_id=" + sourceCode.getSourceCodeId()
                    + ", submission_id=" + sourceCode.getSubmissionId()
                    + ", status=PENDING.");
        } catch (RuntimeException ex) {
            emitProgress("Could not queue AI analysis job source_code_id=" + sourceCode.getSourceCodeId()
                    + ". Reason: " + sanitizeMessage(ex.getMessage()));
        }
    }

    public void addProgressListener(Consumer<String> listener) {
        if (listener != null) {
            progressListeners.add(listener);
        }
    }

    public void removeProgressListener(Consumer<String> listener) {
        progressListeners.remove(listener);
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

    private void emitProgress(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        for (Consumer<String> listener : progressListeners) {
            listener.accept(message);
        }
    }

    private String sanitizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown persistence error.";
        }
        String sanitized = message.replaceAll("(?i)(password=|token=|api[_-]?key=)[^;\\s]+", "$1****");
        return sanitized.length() <= 360 ? sanitized : sanitized.substring(0, 357) + "...";
    }

    private String safeRemoteId(CrawledSubmission crawledSubmission) {
        return crawledSubmission == null || crawledSubmission.platformSubmissionId() == null
                ? "-"
                : crawledSubmission.platformSubmissionId();
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
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
