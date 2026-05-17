package com.example.aicodeanalyzer.service;

import com.example.aicodeanalyzer.config.DatabaseConnectionFactory;
import com.example.aicodeanalyzer.exception.DatabaseException;
import com.example.aicodeanalyzer.model.HandleAccount;
import com.example.aicodeanalyzer.model.Platform;
import com.example.aicodeanalyzer.model.SourceCode;
import com.example.aicodeanalyzer.model.Submission;
import com.example.aicodeanalyzer.repository.HandleAccountRepository;
import com.example.aicodeanalyzer.repository.PlatformRepository;
import com.example.aicodeanalyzer.repository.RepositoryTestSupport;
import com.example.aicodeanalyzer.repository.SourceCodeRepository;
import com.example.aicodeanalyzer.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandleAccountServiceTest {

    @Test
    void addHandleNormalizesPlatformAliasTrimsHandleAndNotes() {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = RepositoryTestSupport.seedPlatform(factory);
        HandleAccountService service = new HandleAccountService(
                new PlatformRepository(factory),
                new HandleAccountRepository(factory)
        );

        HandleAccount saved = service.addHandle("Codeforces", "  tourist_01  ", "  has consent in class  ");

        assertEquals(platform.getPlatformId(), saved.getPlatformId());
        assertEquals("tourist_01", saved.getHandle());
        assertEquals("tourist_01", saved.getDisplayName());
        assertEquals("UNKNOWN", saved.getConsentStatus());
        assertEquals("has consent in class", saved.getNotes());
        assertTrue(saved.isActive());
    }

    @Test
    void addHandleRejectsInvalidHandleBeforeItCanReachDatabase() {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        RepositoryTestSupport.seedPlatform(factory);
        HandleAccountService service = new HandleAccountService(
                new PlatformRepository(factory),
                new HandleAccountRepository(factory)
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.addHandle("CODEFORCES", "abc'; DROP TABLE dbo.platforms;--", null)
        );

        assertTrue(exception.getMessage().contains("letters"));
        assertTrue(service.findAllHandles().isEmpty());
    }

    @Test
    void addHandleRejectsDuplicatePlatformAndHandleAtServiceLayer() {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        RepositoryTestSupport.seedPlatform(factory);
        HandleAccountService service = new HandleAccountService(
                new PlatformRepository(factory),
                new HandleAccountRepository(factory)
        );

        service.addHandle("CODEFORCES", "duplicate_user", null);

        DatabaseException exception = assertThrows(
                DatabaseException.class,
                () -> service.addHandle("CODEFORCES", "duplicate_user", "second")
        );

        assertTrue(exception.getMessage().contains("already exists"));
        assertEquals(1, service.findAllHandles().size());
    }

    @Test
    void updateHandleAllowsKeepingSameHandleButRejectsCollisionWithAnotherHandle() {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = RepositoryTestSupport.seedPlatform(factory);
        HandleAccount first = RepositoryTestSupport.seedHandle(factory, platform.getPlatformId(), "first_user");
        RepositoryTestSupport.seedHandle(factory, platform.getPlatformId(), "second_user");
        HandleAccountService service = new HandleAccountService(
                new PlatformRepository(factory),
                new HandleAccountRepository(factory)
        );

        HandleAccount unchangedName = service.updateHandle(first.getHandleId(), "Codeforces", " first_user ", "   ");
        assertEquals("first_user", unchangedName.getHandle());
        assertNull(unchangedName.getNotes());

        DatabaseException exception = assertThrows(
                DatabaseException.class,
                () -> service.updateHandle(first.getHandleId(), "CODEFORCES", "second_user", null)
        );

        assertTrue(exception.getMessage().contains("already exists"));
        List<HandleAccount> handles = service.findAllHandles();
        assertEquals(2, handles.size());
    }

    @Test
    void deleteHandleRemovesDependentSubmissionsSourcesAnalysisAndScores() throws Exception {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = RepositoryTestSupport.seedPlatform(factory);
        HandleAccount handle = RepositoryTestSupport.seedHandle(factory, platform.getPlatformId(), "delete_me");
        Submission savedSubmission = new SubmissionRepository(factory).save(
                RepositoryTestSupport.submission(handle.getHandleId(), "123456", java.time.LocalDateTime.now())
        );
        new SourceCodeRepository(factory).save(new SourceCode(
                null,
                savedSubmission.getSubmissionId(),
                "public class Main {}",
                "hash",
                1,
                20,
                java.time.LocalDateTime.now(),
                "DATABASE",
                false,
                null,
                null
        ));
        insertAnalysis(factory, savedSubmission.getSubmissionId());
        insertSkillScore(factory, handle.getHandleId());

        HandleAccountService service = new HandleAccountService(
                new PlatformRepository(factory),
                new HandleAccountRepository(factory)
        );

        service.deleteHandle(handle.getHandleId());

        assertEquals(0, countRows(factory, "dbo.ai_analysis_results"));
        assertEquals(0, countRows(factory, "dbo.source_codes"));
        assertEquals(0, countRows(factory, "dbo.submissions"));
        assertEquals(0, countRows(factory, "dbo.user_skill_scores"));
        assertTrue(service.findAllHandles().isEmpty());
    }

    private static void insertAnalysis(DatabaseConnectionFactory factory, long submissionId) throws Exception {
        try (Connection connection = factory.createConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO dbo.ai_analysis_results
                         (submission_id, analyzer_type, analyzer_version, model_name)
                     VALUES (?, 'RULE_BASED', 'test', 'test')
                     """)) {
            statement.setLong(1, submissionId);
            statement.executeUpdate();
        }
    }

    private static void insertSkillScore(DatabaseConnectionFactory factory, long handleId) throws Exception {
        try (Connection connection = factory.createConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO dbo.user_skill_scores
                         (handle_id, period_start, period_end)
                     VALUES (?, ?, ?)
                     """)) {
            statement.setLong(1, handleId);
            statement.setObject(2, LocalDate.of(2026, 5, 1));
            statement.setObject(3, LocalDate.of(2026, 5, 31));
            statement.executeUpdate();
        }
    }

    private static int countRows(DatabaseConnectionFactory factory, String tableName) throws Exception {
        try (Connection connection = factory.createConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + tableName);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
