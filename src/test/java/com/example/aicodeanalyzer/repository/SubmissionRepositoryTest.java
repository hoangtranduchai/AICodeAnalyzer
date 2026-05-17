package com.example.aicodeanalyzer.repository;

import com.example.aicodeanalyzer.config.DatabaseConnectionFactory;
import com.example.aicodeanalyzer.exception.DatabaseException;
import com.example.aicodeanalyzer.model.HandleAccount;
import com.example.aicodeanalyzer.model.Platform;
import com.example.aicodeanalyzer.model.Submission;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubmissionRepositoryTest {

    @Test
    void saveFindUpdateDeleteSubmission() {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = RepositoryTestSupport.seedPlatform(factory);
        HandleAccount handle = RepositoryTestSupport.seedHandle(factory, platform.getPlatformId(), "tourist");
        SubmissionRepository repository = new SubmissionRepository(factory);

        Submission saved = repository.save(RepositoryTestSupport.submission(
                handle.getHandleId(),
                "245123456",
                LocalDateTime.of(2026, 5, 12, 10, 0)
        ));

        assertNotNull(saved.getSubmissionId());
        assertEquals("245123456", saved.getPlatformSubmissionId());
        assertEquals("1703A", saved.getProblemCode());

        Optional<Submission> byId = repository.findById(saved.getSubmissionId());
        Optional<Submission> byHandleAndRemote = repository.findByHandleAndPlatformSubmissionId(
                handle.getHandleId(),
                "245123456"
        );
        Optional<Submission> byPlatformAndRemote = repository.findByPlatformAndRemoteSubmissionId(
                "CODEFORCES",
                "245123456"
        );

        assertTrue(byId.isPresent());
        assertTrue(byHandleAndRemote.isPresent());
        assertTrue(byPlatformAndRemote.isPresent());
        assertEquals(1, repository.findAll().size());
        assertEquals(1, repository.findByHandleId(handle.getHandleId()).size());

        saved.setVerdict("WRONG_ANSWER");
        saved.setLanguage("Java 21");
        saved.setProblemRating(1200);

        assertTrue(repository.update(saved));
        Submission updated = repository.findById(saved.getSubmissionId()).orElseThrow();
        assertEquals("WRONG_ANSWER", updated.getVerdict());
        assertEquals("Java 21", updated.getLanguage());
        assertEquals(1200, updated.getProblemRating());

        assertTrue(repository.delete(saved.getSubmissionId()));
        assertFalse(repository.findById(saved.getSubmissionId()).isPresent());
    }

    @Test
    void findLatestSubmissionByHandleReturnsNewestSubmission() {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = RepositoryTestSupport.seedPlatform(factory);
        HandleAccount handle = RepositoryTestSupport.seedHandle(factory, platform.getPlatformId(), "newest_user");
        SubmissionRepository repository = new SubmissionRepository(factory);

        Submission older = repository.save(RepositoryTestSupport.submission(
                handle.getHandleId(),
                "1001",
                LocalDateTime.of(2026, 5, 10, 8, 0)
        ));
        Submission newer = repository.save(RepositoryTestSupport.submission(
                handle.getHandleId(),
                "1002",
                LocalDateTime.of(2026, 5, 11, 8, 0)
        ));

        Submission latest = repository.findLatestSubmissionByHandle(handle.getHandleId()).orElseThrow();

        assertNotEquals(older.getSubmissionId(), latest.getSubmissionId());
        assertEquals(newer.getSubmissionId(), latest.getSubmissionId());
        assertEquals("1002", latest.getPlatformSubmissionId());
    }

    @Test
    void findRecentLimitsAndOrdersSubmissions() {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = RepositoryTestSupport.seedPlatform(factory);
        HandleAccount handle = RepositoryTestSupport.seedHandle(factory, platform.getPlatformId(), "recent_user");
        SubmissionRepository repository = new SubmissionRepository(factory);

        repository.save(RepositoryTestSupport.submission(
                handle.getHandleId(),
                "2001",
                LocalDateTime.of(2026, 5, 10, 8, 0)
        ));
        repository.save(RepositoryTestSupport.submission(
                handle.getHandleId(),
                "2002",
                LocalDateTime.of(2026, 5, 11, 8, 0)
        ));
        repository.save(RepositoryTestSupport.submission(
                handle.getHandleId(),
                "2003",
                LocalDateTime.of(2026, 5, 12, 8, 0)
        ));

        List<Submission> recent = repository.findRecent(2);

        assertEquals(2, recent.size());
        assertEquals("2003", recent.get(0).getPlatformSubmissionId());
        assertEquals("2002", recent.get(1).getPlatformSubmissionId());
    }

    @Test
    void findByHandleIdsAndSubmittedBetweenFiltersInDatabase() {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = RepositoryTestSupport.seedPlatform(factory);
        HandleAccount firstHandle = RepositoryTestSupport.seedHandle(factory, platform.getPlatformId(), "period_user_a");
        HandleAccount secondHandle = RepositoryTestSupport.seedHandle(factory, platform.getPlatformId(), "period_user_b");
        SubmissionRepository repository = new SubmissionRepository(factory);

        repository.save(RepositoryTestSupport.submission(
                firstHandle.getHandleId(),
                "3001",
                LocalDateTime.of(2026, 4, 30, 23, 59)
        ));
        repository.save(RepositoryTestSupport.submission(
                firstHandle.getHandleId(),
                "3002",
                LocalDateTime.of(2026, 5, 1, 0, 0)
        ));
        repository.save(RepositoryTestSupport.submission(
                firstHandle.getHandleId(),
                "3003",
                LocalDateTime.of(2026, 5, 31, 23, 59)
        ));
        repository.save(RepositoryTestSupport.submission(
                secondHandle.getHandleId(),
                "3004",
                LocalDateTime.of(2026, 5, 15, 12, 0)
        ));

        List<Submission> filtered = repository.findByHandleIdsAndSubmittedBetween(
                java.util.Set.of(firstHandle.getHandleId()),
                java.time.LocalDate.of(2026, 5, 1),
                java.time.LocalDate.of(2026, 5, 31)
        );

        assertEquals(2, filtered.size());
        assertEquals(List.of("3002", "3003"), filtered.stream().map(Submission::getPlatformSubmissionId).toList());
    }

    @Test
    void findMethodsReturnEmptyCollectionsWhenNoSubmissionsExist() {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = RepositoryTestSupport.seedPlatform(factory);
        HandleAccount handle = RepositoryTestSupport.seedHandle(factory, platform.getPlatformId(), "empty_user");
        SubmissionRepository repository = new SubmissionRepository(factory);

        assertTrue(repository.findAll().isEmpty());
        assertTrue(repository.findByHandleId(handle.getHandleId()).isEmpty());
        assertFalse(repository.findById(999).isPresent());
        assertFalse(repository.findLatestSubmissionByHandle(handle.getHandleId()).isPresent());
        assertFalse(repository.findByHandleAndPlatformSubmissionId(handle.getHandleId(), "missing").isPresent());
        assertFalse(repository.findByPlatformAndRemoteSubmissionId("CODEFORCES", "missing").isPresent());
    }

    @Test
    void saveSubmissionIfNotExistsSkipsDuplicateRemoteSubmission() {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = RepositoryTestSupport.seedPlatform(factory);
        HandleAccount handle = RepositoryTestSupport.seedHandle(factory, platform.getPlatformId(), "duplicate_submission_user");
        SubmissionRepository repository = new SubmissionRepository(factory);

        Submission first = repository.saveSubmissionIfNotExists(RepositoryTestSupport.submission(
                handle.getHandleId(),
                "777",
                LocalDateTime.of(2026, 5, 12, 10, 0)
        ));
        Submission second = RepositoryTestSupport.submission(
                handle.getHandleId(),
                "777",
                LocalDateTime.of(2026, 5, 13, 10, 0)
        );
        second.setVerdict("WRONG_ANSWER");

        Submission returned = repository.saveSubmissionIfNotExists(second);
        List<Submission> all = repository.findAll();

        assertEquals(first.getSubmissionId(), returned.getSubmissionId());
        assertEquals(1, all.size());
        assertEquals("OK", all.getFirst().getVerdict());
    }

    @Test
    void directSaveRejectsDuplicateHandleAndRemoteSubmission() {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = RepositoryTestSupport.seedPlatform(factory);
        HandleAccount handle = RepositoryTestSupport.seedHandle(factory, platform.getPlatformId(), "direct_duplicate_user");
        SubmissionRepository repository = new SubmissionRepository(factory);

        repository.save(RepositoryTestSupport.submission(
                handle.getHandleId(),
                "888",
                LocalDateTime.of(2026, 5, 12, 10, 0)
        ));

        DatabaseException exception = assertThrows(DatabaseException.class, () -> repository.save(
                RepositoryTestSupport.submission(handle.getHandleId(), "888", LocalDateTime.of(2026, 5, 13, 10, 0))
        ));

        assertTrue(exception.getMessage().contains("saving submission"));
    }

    @Test
    void directSaveRejectsDuplicatePlatformAndRemoteSubmissionAcrossHandles() {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = RepositoryTestSupport.seedPlatform(factory);
        HandleAccount firstHandle = RepositoryTestSupport.seedHandle(factory, platform.getPlatformId(), "first_owner");
        HandleAccount secondHandle = RepositoryTestSupport.seedHandle(factory, platform.getPlatformId(), "second_owner");
        SubmissionRepository repository = new SubmissionRepository(factory);

        repository.save(RepositoryTestSupport.submission(
                firstHandle.getHandleId(),
                "999001",
                LocalDateTime.of(2026, 5, 12, 10, 0)
        ));

        DatabaseException exception = assertThrows(DatabaseException.class, () -> repository.save(
                RepositoryTestSupport.submission(secondHandle.getHandleId(), "999001", LocalDateTime.of(2026, 5, 13, 10, 0))
        ));

        assertTrue(exception.getMessage().contains("saving submission"));
    }

    @Test
    void repositoryPropagatesConnectionFailure() {
        SubmissionRepository repository = new SubmissionRepository(RepositoryTestSupport.failingFactory());

        DatabaseException exception = assertThrows(DatabaseException.class, repository::findAll);

        assertTrue(exception.getMessage().contains("Forced connection failure"));
    }
}
