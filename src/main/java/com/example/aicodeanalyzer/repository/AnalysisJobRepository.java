package com.example.aicodeanalyzer.repository;

import com.example.aicodeanalyzer.config.DatabaseConnectionFactory;
import com.example.aicodeanalyzer.model.AnalysisJob;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Stores persistent AI analysis queue state.
 */
public class AnalysisJobRepository extends JdbcRepositorySupport {
    public AnalysisJobRepository() {
        super();
    }

    public AnalysisJobRepository(DatabaseConnectionFactory connectionFactory) {
        super(connectionFactory);
    }

    public Optional<AnalysisJob> findBySourceCodeId(long sourceCodeId) {
        String sql = selectSql() + " WHERE source_code_id = ?";
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, sourceCodeId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw databaseException("finding analysis job by source code", ex);
        }
    }

    public List<AnalysisJob> findRecent(int limit) {
        String sql = selectSql() + """
                 ORDER BY updated_at DESC, analysis_job_id DESC
                 OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY
                """;
        List<AnalysisJob> jobs = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    jobs.add(mapRow(resultSet));
                }
            }
            return jobs;
        } catch (SQLException ex) {
            throw databaseException("finding recent analysis jobs", ex);
        }
    }

    public void markPending(long sourceCodeId, long submissionId) {
        Optional<AnalysisJob> existing = findBySourceCodeId(sourceCodeId);
        if (existing.isPresent()) {
            resetPending(sourceCodeId, submissionId);
            return;
        }
        String sql = """
                INSERT INTO dbo.analysis_jobs (source_code_id, submission_id, status)
                VALUES (?, ?, 'PENDING')
                """;
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, sourceCodeId);
            statement.setLong(2, submissionId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw databaseException("creating pending analysis job", ex);
        }
    }

    private void resetPending(long sourceCodeId, long submissionId) {
        String sql = """
                UPDATE dbo.analysis_jobs
                SET submission_id = ?,
                    status = 'PENDING',
                    attempt_count = 0,
                    next_retry_at = NULL,
                    started_at = NULL,
                    finished_at = NULL,
                    last_analysis_id = NULL,
                    last_error = NULL,
                    updated_at = SYSUTCDATETIME()
                WHERE source_code_id = ?
                """;
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, submissionId);
            statement.setLong(2, sourceCodeId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw databaseException("resetting pending analysis job", ex);
        }
    }

    public void markRunning(long sourceCodeId) {
        updateStatus(sourceCodeId, "RUNNING", null, LocalDateTime.now(), null, null, true);
    }

    public void markSucceeded(long sourceCodeId, long analysisId) {
        updateStatus(sourceCodeId, "SUCCEEDED", analysisId, null, LocalDateTime.now(), null, false);
    }

    public void markFailed(long sourceCodeId, String error) {
        updateStatus(sourceCodeId, "FAILED", null, null, LocalDateTime.now(), error, false);
    }

    public void markQuotaDelayed(long sourceCodeId, LocalDateTime nextRetryAt, String error) {
        updateStatus(sourceCodeId, "QUOTA_DELAYED", null, null, null, error, false, nextRetryAt);
    }

    public void markSkipped(long sourceCodeId, String reason) {
        updateStatus(sourceCodeId, "SKIPPED", null, null, LocalDateTime.now(), reason, false);
    }

    private void updateStatus(
            long sourceCodeId,
            String status,
            Long analysisId,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String error,
            boolean incrementAttempt
    ) {
        updateStatus(sourceCodeId, status, analysisId, startedAt, finishedAt, error, incrementAttempt, null);
    }

    private void updateStatus(
            long sourceCodeId,
            String status,
            Long analysisId,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String error,
            boolean incrementAttempt,
            LocalDateTime nextRetryAt
    ) {
        String sql = """
                UPDATE dbo.analysis_jobs
                SET status = ?,
                    attempt_count = attempt_count + ?,
                    next_retry_at = ?,
                    started_at = COALESCE(?, started_at),
                    finished_at = ?,
                    last_analysis_id = COALESCE(?, last_analysis_id),
                    last_error = ?,
                    updated_at = SYSUTCDATETIME()
                WHERE source_code_id = ?
                """;
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setInt(2, incrementAttempt ? 1 : 0);
            setLocalDateTime(statement, 3, nextRetryAt);
            setLocalDateTime(statement, 4, startedAt);
            setLocalDateTime(statement, 5, finishedAt);
            if (analysisId == null) {
                statement.setObject(6, null);
            } else {
                statement.setLong(6, analysisId);
            }
            statement.setString(7, truncate(error, 1000));
            statement.setLong(8, sourceCodeId);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw databaseException("updating analysis job status", ex);
        }
    }

    private String selectSql() {
        return """
                SELECT analysis_job_id, source_code_id, submission_id, status, attempt_count,
                       next_retry_at, started_at, finished_at, last_analysis_id, last_error,
                       created_at, updated_at
                FROM dbo.analysis_jobs
                """;
    }

    private AnalysisJob mapRow(ResultSet resultSet) throws SQLException {
        long analysisId = resultSet.getLong("last_analysis_id");
        Long lastAnalysisId = resultSet.wasNull() ? null : analysisId;
        return new AnalysisJob(
                resultSet.getLong("analysis_job_id"),
                resultSet.getLong("source_code_id"),
                resultSet.getLong("submission_id"),
                resultSet.getString("status"),
                resultSet.getInt("attempt_count"),
                getLocalDateTime(resultSet, "next_retry_at"),
                getLocalDateTime(resultSet, "started_at"),
                getLocalDateTime(resultSet, "finished_at"),
                lastAnalysisId,
                resultSet.getString("last_error"),
                getLocalDateTime(resultSet, "created_at"),
                getLocalDateTime(resultSet, "updated_at")
        );
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
