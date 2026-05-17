package com.example.aicodeanalyzer.repository;

import com.example.aicodeanalyzer.config.DatabaseConnectionFactory;
import com.example.aicodeanalyzer.model.AnalysisJob;
import com.example.aicodeanalyzer.model.HandleAccount;
import com.example.aicodeanalyzer.model.Platform;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisJobRepositoryTest {

    @Test
    void markPendingResetsStaleAnalysisStateForExistingJob() throws Exception {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = RepositoryTestSupport.seedPlatform(factory);
        HandleAccountRepository handleRepository = new HandleAccountRepository(factory);
        HandleAccount handle = handleRepository.save(new HandleAccount(
                null,
                platform.getPlatformId(),
                "analysis_job_reset",
                "analysis_job_reset",
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
            submissionId = insertSubmission(connection, handle.getHandleId(), platform.getPlatformId());
            sourceCodeId = insertSourceCode(connection, submissionId);
            analysisId = insertAnalysis(connection, submissionId);
        }

        AnalysisJobRepository repository = new AnalysisJobRepository(factory);
        repository.markPending(sourceCodeId, submissionId);
        repository.markRunning(sourceCodeId);
        repository.markSucceeded(sourceCodeId, analysisId);

        repository.markPending(sourceCodeId, submissionId);

        AnalysisJob job = repository.findBySourceCodeId(sourceCodeId).orElseThrow();
        assertEquals("PENDING", job.getStatus());
        assertEquals(0, job.getAttemptCount());
        assertNull(job.getLastAnalysisId());
        assertNull(job.getLastError());
        assertNull(job.getStartedAt());
        assertNull(job.getFinishedAt());
        assertNull(job.getNextRetryAt());
    }

    @Test
    void deletingSubmissionRemovesRelatedAnalysisJobBeforeFkChecks() throws Exception {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = RepositoryTestSupport.seedPlatform(factory);
        HandleAccount handle = RepositoryTestSupport.seedHandle(factory, platform.getPlatformId(), "delete_submission_job");

        long submissionId;
        long sourceCodeId;
        try (Connection connection = factory.createConnection()) {
            submissionId = insertSubmission(connection, handle.getHandleId(), platform.getPlatformId());
            sourceCodeId = insertSourceCode(connection, submissionId);
        }

        AnalysisJobRepository jobs = new AnalysisJobRepository(factory);
        jobs.markPending(sourceCodeId, submissionId);

        assertTrue(new SubmissionRepository(factory).delete(submissionId));
        assertFalse(jobs.findBySourceCodeId(sourceCodeId).isPresent());
    }

    @Test
    void deletingSourceCodeRemovesRelatedAnalysisJobBeforeFkChecks() throws Exception {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = RepositoryTestSupport.seedPlatform(factory);
        HandleAccount handle = RepositoryTestSupport.seedHandle(factory, platform.getPlatformId(), "delete_source_job");

        long submissionId;
        long sourceCodeId;
        try (Connection connection = factory.createConnection()) {
            submissionId = insertSubmission(connection, handle.getHandleId(), platform.getPlatformId());
            sourceCodeId = insertSourceCode(connection, submissionId);
        }

        AnalysisJobRepository jobs = new AnalysisJobRepository(factory);
        jobs.markPending(sourceCodeId, submissionId);

        assertTrue(new SourceCodeRepository(factory).delete(sourceCodeId));
        assertFalse(jobs.findBySourceCodeId(sourceCodeId).isPresent());
    }

    @Test
    void deletingAnalysisResultClearsJobLastAnalysisBeforeFkChecks() throws Exception {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = RepositoryTestSupport.seedPlatform(factory);
        HandleAccount handle = RepositoryTestSupport.seedHandle(factory, platform.getPlatformId(), "delete_analysis_job");

        long submissionId;
        long sourceCodeId;
        long analysisId;
        try (Connection connection = factory.createConnection()) {
            submissionId = insertSubmission(connection, handle.getHandleId(), platform.getPlatformId());
            sourceCodeId = insertSourceCode(connection, submissionId);
            analysisId = insertAnalysis(connection, submissionId);
        }

        AnalysisJobRepository jobs = new AnalysisJobRepository(factory);
        jobs.markPending(sourceCodeId, submissionId);
        jobs.markSucceeded(sourceCodeId, analysisId);

        assertTrue(new AiAnalysisResultRepository(factory).delete(analysisId));

        AnalysisJob job = jobs.findBySourceCodeId(sourceCodeId).orElseThrow();
        assertEquals("SUCCEEDED", job.getStatus());
        assertNull(job.getLastAnalysisId());
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
            statement.setString(3, "AJ-001");
            statement.setString(4, "A");
            statement.setString(5, "Analysis job reset");
            statement.setString(6, "Java");
            statement.setString(7, "OK");
            statement.setTimestamp(8, Timestamp.valueOf(LocalDateTime.of(2026, 5, 18, 1, 0)));
            statement.setString(9, "https://example.test/submission/AJ-001");
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
            statement.setBigDecimal(6, BigDecimal.TEN);
            statement.setString(7, "LOW");
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getLong(1);
            }
        }
    }
}
