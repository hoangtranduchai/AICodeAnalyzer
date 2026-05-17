package com.example.aicodeanalyzer.service;

import com.example.aicodeanalyzer.config.DatabaseConnectionFactory;
import com.example.aicodeanalyzer.exception.DatabaseException;
import com.example.aicodeanalyzer.model.HandleAccount;
import com.example.aicodeanalyzer.model.Platform;
import com.example.aicodeanalyzer.repository.HandleAccountRepository;
import com.example.aicodeanalyzer.repository.PlatformRepository;
import com.example.aicodeanalyzer.util.ValidationUtils;

import java.util.List;
import java.util.Objects;

/**
 * Business service for validating and persisting Codeforces/VJudge handles.
 */
public class HandleAccountService {
    private static final String DEFAULT_CONSENT_STATUS = "UNKNOWN";

    private final PlatformRepository platformRepository;
    private final HandleAccountRepository handleAccountRepository;

    public HandleAccountService() {
        this(DatabaseConnectionFactory.fromApplicationProperties());
    }

    public HandleAccountService(DatabaseConnectionFactory connectionFactory) {
        this(new PlatformRepository(connectionFactory), new HandleAccountRepository(connectionFactory));
    }

    public HandleAccountService(
            PlatformRepository platformRepository,
            HandleAccountRepository handleAccountRepository
    ) {
        this.platformRepository = Objects.requireNonNull(platformRepository, "platformRepository must not be null");
        this.handleAccountRepository = Objects.requireNonNull(handleAccountRepository, "handleAccountRepository must not be null");
    }

    public List<Platform> findPlatforms() {
        return platformRepository.findAll();
    }

    public List<HandleAccount> findAllHandles() {
        return handleAccountRepository.findAll();
    }

    public HandleAccount addHandle(String platformCode, String handle, String notes) {
        Platform platform = findPlatformOrThrow(platformCode);
        String normalizedHandle = normalizeHandle(handle);
        validateUnique(platform.getPlatformId(), normalizedHandle, null);

        HandleAccount handleAccount = new HandleAccount();
        handleAccount.setPlatformId(platform.getPlatformId());
        handleAccount.setHandle(normalizedHandle);
        handleAccount.setDisplayName(normalizedHandle);
        handleAccount.setConsentStatus(DEFAULT_CONSENT_STATUS);
        handleAccount.setActive(true);
        handleAccount.setNotes(trimToNull(notes));

        return handleAccountRepository.save(handleAccount);
    }

    public HandleAccount updateHandle(long handleId, String platformCode, String handle, String notes) {
        HandleAccount existing = handleAccountRepository.findById(handleId)
                .orElseThrow(() -> new DatabaseException("Cannot update handle because it no longer exists."));

        Platform platform = findPlatformOrThrow(platformCode);
        String normalizedHandle = normalizeHandle(handle);
        validateUnique(platform.getPlatformId(), normalizedHandle, handleId);

        existing.setPlatformId(platform.getPlatformId());
        existing.setHandle(normalizedHandle);
        existing.setDisplayName(normalizedHandle);
        existing.setNotes(trimToNull(notes));

        boolean updated = handleAccountRepository.update(existing);
        if (!updated) {
            throw new DatabaseException("Handle was not updated. Please refresh and try again.");
        }

        return handleAccountRepository.findById(handleId)
                .orElseThrow(() -> new DatabaseException("Handle was updated but could not be reloaded."));
    }

    public void deleteHandle(long handleId) {
        boolean deleted = handleAccountRepository.delete(handleId);
        if (!deleted) {
            throw new DatabaseException("Handle was not deleted. It may have already been removed.");
        }
    }

    private Platform findPlatformOrThrow(String platformCode) {
        String normalizedCode = normalizePlatformCode(platformCode);
        return platformRepository.findAll().stream()
                .filter(platform -> normalizedCode.equalsIgnoreCase(platform.getCode()))
                .findFirst()
                .orElseThrow(() -> new DatabaseException(
                        "Platform " + normalizedCode + " was not found. Please run ai-code-analyzer-complete.sql."
                ));
    }

    private void validateUnique(long platformId, String handle, Long currentHandleId) {
        handleAccountRepository.findHandleByPlatformAndName(platformId, handle)
                .filter(existing -> currentHandleId == null || !existing.getHandleId().equals(currentHandleId))
                .ifPresent(existing -> {
                    throw new DatabaseException("Handle already exists on this platform: " + handle);
                });
    }

    private String normalizePlatformCode(String platformCode) {
        return ValidationUtils.normalizePlatformCode(platformCode);
    }

    private String normalizeHandle(String handle) {
        return ValidationUtils.normalizeHandle(handle);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
