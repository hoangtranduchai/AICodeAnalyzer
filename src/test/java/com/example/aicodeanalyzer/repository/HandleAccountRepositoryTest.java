package com.example.aicodeanalyzer.repository;

import com.example.aicodeanalyzer.config.DatabaseConnectionFactory;
import com.example.aicodeanalyzer.exception.DatabaseException;
import com.example.aicodeanalyzer.model.HandleAccount;
import com.example.aicodeanalyzer.model.Platform;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandleAccountRepositoryTest {

    @Test
    void saveFindUpdateDeleteHandleAccount() {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = RepositoryTestSupport.seedPlatform(factory);
        HandleAccountRepository repository = new HandleAccountRepository(factory);

        HandleAccount saved = repository.save(new HandleAccount(
                null,
                platform.getPlatformId(),
                "tourist",
                "Tourist",
                "official",
                "PUBLIC",
                true,
                LocalDateTime.of(2026, 5, 13, 9, 0),
                "initial note",
                null,
                null
        ));

        assertNotNull(saved.getHandleId());
        assertEquals("tourist", saved.getHandle());
        assertEquals("Tourist", saved.getDisplayName());
        assertTrue(saved.isActive());

        Optional<HandleAccount> byId = repository.findById(saved.getHandleId());
        Optional<HandleAccount> byPlatformId = repository.findHandleByPlatformAndName(platform.getPlatformId(), "tourist");
        Optional<HandleAccount> byPlatformCode = repository.findHandleByPlatformAndName("CODEFORCES", "tourist");
        List<HandleAccount> all = repository.findAll();

        assertTrue(byId.isPresent());
        assertTrue(byPlatformId.isPresent());
        assertTrue(byPlatformCode.isPresent());
        assertEquals(1, all.size());

        saved.setDisplayName("Tourist Updated");
        saved.setGroupName("advanced");
        saved.setNotes("updated note");

        assertTrue(repository.update(saved));
        HandleAccount updated = repository.findById(saved.getHandleId()).orElseThrow();
        assertEquals("Tourist Updated", updated.getDisplayName());
        assertEquals("advanced", updated.getGroupName());
        assertEquals("updated note", updated.getNotes());

        assertTrue(repository.delete(saved.getHandleId()));
        assertFalse(repository.findById(saved.getHandleId()).isPresent());
    }

    @Test
    void findMethodsReturnEmptyWhenHandleDoesNotExist() {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = RepositoryTestSupport.seedPlatform(factory);
        HandleAccountRepository repository = new HandleAccountRepository(factory);

        assertTrue(repository.findAll().isEmpty());
        assertFalse(repository.findById(999).isPresent());
        assertFalse(repository.findHandleByPlatformAndName(platform.getPlatformId(), "missing").isPresent());
        assertFalse(repository.findHandleByPlatformAndName("CODEFORCES", "missing").isPresent());
    }

    @Test
    void saveRejectsDuplicatePlatformAndHandle() {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = RepositoryTestSupport.seedPlatform(factory);
        HandleAccountRepository repository = new HandleAccountRepository(factory);

        repository.save(new HandleAccount(
                null,
                platform.getPlatformId(),
                "duplicate_user",
                "Duplicate User",
                null,
                "PUBLIC",
                true,
                null,
                null,
                null,
                null
        ));

        DatabaseException exception = assertThrows(DatabaseException.class, () -> repository.save(new HandleAccount(
                null,
                platform.getPlatformId(),
                "duplicate_user",
                "Duplicate User 2",
                null,
                "PUBLIC",
                true,
                null,
                null,
                null,
                null
        )));

        assertTrue(exception.getMessage().contains("saving handle"));
    }

    @Test
    void repositoryWrapsConnectionFailures() {
        HandleAccountRepository repository = new HandleAccountRepository(RepositoryTestSupport.failingFactory());

        DatabaseException exception = assertThrows(DatabaseException.class, repository::findAll);

        assertTrue(exception.getMessage().contains("Forced connection failure"));
    }
}
