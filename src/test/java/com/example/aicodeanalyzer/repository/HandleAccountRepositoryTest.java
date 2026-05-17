package com.example.aicodeanalyzer.repository;

import com.example.aicodeanalyzer.config.DatabaseConnectionFactory;
import com.example.aicodeanalyzer.exception.DatabaseException;
import com.example.aicodeanalyzer.model.HandleAccount;
import com.example.aicodeanalyzer.model.Platform;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
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
    void findPipelineStatsReturnsZeroCountersForTrackedHandleWithoutSubmissions() {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = RepositoryTestSupport.seedPlatform(factory);
        HandleAccountRepository repository = new HandleAccountRepository(factory);

        HandleAccount saved = repository.save(new HandleAccount(
                null,
                platform.getPlatformId(),
                "pipeline_empty",
                "pipeline_empty",
                null,
                "PUBLIC",
                true,
                null,
                null,
                null,
                null
        ));

        List<HandleAccountRepository.HandlePipelineStats> stats = repository.findPipelineStats();

        assertEquals(1, stats.size());
        assertEquals(saved.getHandleId().longValue(), stats.get(0).handleId());
        assertEquals(0, stats.get(0).totalSubmissions());
        assertEquals(0, stats.get(0).pendingAi());
        assertEquals(0, stats.get(0).sourceIssues());
        assertEquals(0, stats.get(0).lastNewSubmissions());
        assertEquals("-", stats.get(0).lastStatus());
    }


    @Test
    void deleteRemovesAnalysisJobsBeforeDependentSourceAndAnalysisRows() throws Exception {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = RepositoryTestSupport.seedPlatform(factory);
        HandleAccountRepository repository = new HandleAccountRepository(factory);

        HandleAccount saved = repository.save(new HandleAccount(
                null,
                platform.getPlatformId(),
                "delete_with_jobs",
                "delete_with_jobs",
                null,
                "PUBLIC",
                true,
                null,
                null,
                null,
                null
        ));

        long submissionId;
        long sourceCodeId;
        long analysisId;
        try (Connection connection = factory.createConnection()) {
            submissionId = insertSubmission(connection, saved.getHandleId(), platform.getPlatformId());
            sourceCodeId = insertSourceCode(connection, submissionId);
            analysisId = insertAnalysis(connection, submissionId);
            insertAnalysisJob(connection, sourceCodeId, submissionId, analysisId);
        }

        assertTrue(repository.delete(saved.getHandleId()));

        try (Connection connection = factory.createConnection()) {
            assertEquals(0, countRows(connection, "dbo.analysis_jobs"));
            assertEquals(0, countRows(connection, "dbo.ai_analysis_results"));
            assertEquals(0, countRows(connection, "dbo.source_codes"));
            assertEquals(0, countRows(connection, "dbo.submissions"));
            assertEquals(0, countRows(connection, "dbo.programming_handles"));
        }
    }

    @Test
    void repositoryWrapsConnectionFailures() {
        HandleAccountRepository repository = new HandleAccountRepository(RepositoryTestSupport.failingFactory());

        DatabaseException exception = assertThrows(DatabaseException.class, repository::findAll);

        assertTrue(exception.getMessage().contains("Forced connection failure"));
    }

    private long insertSubmission(Connection connection, long handleId, long platformId) throws Exception {
        String sql = """
                INSERT INTO dbo.submissions
                    (platform_id, handle_id, platform_submission_id, problem_code, problem_name,
                     language, verdict, submitted_at, source_url, source_crawl_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, platformId);
            statement.setLong(2, handleId);
            statement.setString(3, "DEL-001");
            statement.setString(4, "A");
            statement.setString(5, "Delete dependency test");
            statement.setString(6, "Java");
            statement.setString(7, "OK");
            statement.setTimestamp(8, Timestamp.valueOf(LocalDateTime.of(2026, 5, 18, 1, 0)));
            statement.setString(9, "https://example.test/submission/DEL-001");
            statement.setString(10, "CRAWLED");
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getLong(1);
            }
        }
    }

    private long insertSourceCode(Connection connection, long submissionId) throws Exception {
        String sql = """
                INSERT INTO dbo.source_codes
                    (submission_id, code_content, code_hash, line_count, char_count, fetched_at, storage_type, is_encrypted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, submissionId);
            statement.setString(2, "class Main {}");
            statement.setString(3, "hash");
            statement.setInt(4, 1);
            statement.setInt(5, 13);
            statement.setTimestamp(6, Timestamp.valueOf(LocalDateTime.of(2026, 5, 18, 1, 1)));
            statement.setString(7, "DATABASE");
            statement.setBoolean(8, false);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getLong(1);
            }
        }
    }

    private long insertAnalysis(Connection connection, long submissionId) throws Exception {
        String sql = """
                INSERT INTO dbo.ai_analysis_results
                    (submission_id, analyzer_type, model_name, data_structures, algorithms, ai_risk_score, ai_risk_level)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, submissionId);
            statement.setString(2, "RULE_BASED");
            statement.setString(3, "rule-based");
            statement.setString(4, "Array");
            statement.setString(5, "Simulation");
            statement.setBigDecimal(6, java.math.BigDecimal.TEN);
            statement.setString(7, "LOW");
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getLong(1);
            }
        }
    }

    private void insertAnalysisJob(
            Connection connection,
            long sourceCodeId,
            long submissionId,
            long analysisId
    ) throws Exception {
        String sql = """
                INSERT INTO dbo.analysis_jobs
                    (source_code_id, submission_id, status, last_analysis_id)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, sourceCodeId);
            statement.setLong(2, submissionId);
            statement.setString(3, "SUCCEEDED");
            statement.setLong(4, analysisId);
            statement.executeUpdate();
        }
    }

    private int countRows(Connection connection, String tableName) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + tableName);
             ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        }
    }
}
