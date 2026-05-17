package com.example.aicodeanalyzer.repository;

import com.example.aicodeanalyzer.config.DatabaseConnectionFactory;
import com.example.aicodeanalyzer.exception.DatabaseException;
import com.example.aicodeanalyzer.model.Submission;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Provides database operations for submission metadata and dashboard submission queries.
 */
public class SubmissionRepository extends JdbcRepositorySupport {

    public SubmissionRepository() {
        super();
    }

    public SubmissionRepository(DatabaseConnectionFactory connectionFactory) {
        super(connectionFactory);
    }

    public Optional<Submission> findById(long submissionId) {
        String sql = selectSubmissionSql() + " WHERE submission_id = ?";

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, submissionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw databaseException("finding submission by id", ex);
        }
    }

    public List<Submission> findAll() {
        String sql = selectSubmissionSql() + " ORDER BY submitted_at DESC, submission_id DESC";

        List<Submission> submissions = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                submissions.add(mapRow(resultSet));
            }
            return submissions;
        } catch (SQLException ex) {
            throw databaseException("finding all submissions", ex);
        }
    }

    public List<Submission> findRecent(int limit) {
        String sql = selectSubmissionSql() + """
                 ORDER BY submitted_at DESC, submission_id DESC
                 OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY
                """;

        List<Submission> submissions = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    submissions.add(mapRow(resultSet));
                }
            }
            return submissions;
        } catch (SQLException ex) {
            throw databaseException("finding recent submissions", ex);
        }
    }

    public List<Submission> findByHandleIdsAndSubmittedBetween(
            Set<Long> handleIds,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
        if (handleIds == null || handleIds.isEmpty()) {
            return List.of();
        }

        String sql = selectSubmissionSql()
                + " WHERE handle_id IN (" + placeholders(handleIds.size()) + ")"
                + " AND submitted_at >= ? AND submitted_at < ?"
                + " ORDER BY submitted_at ASC, submission_id ASC";

        List<Submission> submissions = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = setLongValues(statement, 1, handleIds);
            setLocalDateTime(statement, index++, periodStart.atStartOfDay());
            setLocalDateTime(statement, index, periodEnd.plusDays(1).atStartOfDay());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    submissions.add(mapRow(resultSet));
                }
            }
            return submissions;
        } catch (SQLException ex) {
            throw databaseException("finding submissions by handles and date range", ex);
        }
    }

    public Optional<Submission> findLatestSubmissionByHandle(long handleId) {
        String sql = selectSubmissionSql() + """
                 WHERE handle_id = ?
                 ORDER BY submitted_at DESC, submission_id DESC
                 OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY
                """;

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, handleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw databaseException("finding latest submission by handle", ex);
        }
    }

    public List<Submission> findByHandleId(long handleId) {
        String sql = selectSubmissionSql() + """
                 WHERE handle_id = ?
                 ORDER BY submitted_at ASC, submission_id ASC
                """;

        List<Submission> submissions = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, handleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    submissions.add(mapRow(resultSet));
                }
            }
            return submissions;
        } catch (SQLException ex) {
            throw databaseException("finding submissions by handle", ex);
        }
    }

    public Optional<Submission> findByPlatformAndRemoteSubmissionId(String platformCode, String remoteSubmissionId) {
        String sql = """
                SELECT s.submission_id, s.handle_id, s.platform_submission_id, s.problem_code, s.problem_name,
                       s.contest_id, s.language, s.verdict, s.submitted_at, s.execution_time_ms, s.memory_bytes,
                       s.problem_rating, s.problem_tags, s.source_url, s.created_at, s.updated_at
                FROM dbo.submissions s
                JOIN dbo.platforms p ON p.platform_id = s.platform_id
                WHERE p.code = ? AND s.platform_submission_id = ?
                """;

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, platformCode);
            statement.setString(2, remoteSubmissionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw databaseException("finding submission by platform and remote submission id", ex);
        }
    }

    public List<Submission> findRecentByPlatformAndHandle(String platformCode, String handle, int limit) {
        String sql = """
                SELECT s.submission_id, s.handle_id, s.platform_submission_id, s.problem_code, s.problem_name,
                       s.contest_id, s.language, s.verdict, s.submitted_at, s.execution_time_ms, s.memory_bytes,
                       s.problem_rating, s.problem_tags, s.source_url, s.created_at, s.updated_at
                FROM dbo.submissions s
                JOIN dbo.programming_handles h ON h.handle_id = s.handle_id
                JOIN dbo.platforms p ON p.platform_id = h.platform_id
                WHERE p.code = ? AND h.handle = ?
                ORDER BY s.submitted_at DESC, s.submission_id DESC
                OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY
                """;

        List<Submission> submissions = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, platformCode);
            statement.setString(2, handle);
            statement.setInt(3, Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    submissions.add(mapRow(resultSet));
                }
            }
            return submissions;
        } catch (SQLException ex) {
            throw databaseException("finding recent submissions by platform and handle", ex);
        }
    }

    public Set<String> findSubmissionIdsByPlatformAndHandle(String platformCode, String handle) {
        String sql = """
                SELECT s.platform_submission_id
                FROM dbo.submissions s
                JOIN dbo.programming_handles h ON h.handle_id = s.handle_id
                JOIN dbo.platforms p ON p.platform_id = h.platform_id
                WHERE p.code = ? AND h.handle = ?
                  AND s.platform_submission_id IS NOT NULL
                """;

        Set<String> submissionIds = new java.util.HashSet<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, platformCode);
            statement.setString(2, handle);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String value = resultSet.getString("platform_submission_id");
                    if (value != null && !value.isBlank()) {
                        submissionIds.add(value);
                    }
                }
            }
            return submissionIds;
        } catch (SQLException ex) {
            throw databaseException("finding known submission ids by platform and handle", ex);
        }
    }

    public Submission saveSubmissionIfNotExists(Submission submission) {
        Optional<Submission> existingSubmission = findByHandlePlatformAndRemoteSubmissionId(
                submission.getHandleId(),
                submission.getPlatformSubmissionId()
        );
        return existingSubmission.orElseGet(() -> save(submission));
    }

    public Submission save(Submission submission) {
        String sql = """
                INSERT INTO dbo.submissions
                    (platform_id, handle_id, platform_submission_id, problem_code, problem_name, contest_id, language, verdict,
                     submitted_at, execution_time_ms, memory_bytes, problem_rating, problem_tags, source_url)
                VALUES ((SELECT platform_id FROM dbo.programming_handles WHERE handle_id = ?), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setSubmissionInsertParameters(statement, submission);
            statement.executeUpdate();

            long generatedId = readGeneratedId(statement);
            return findById(generatedId)
                    .orElseThrow(() -> new DatabaseException("Submission was inserted but could not be reloaded."));
        } catch (SQLException ex) {
            throw databaseException("saving submission", ex);
        }
    }

    public boolean update(Submission submission) {
        String sql = """
                UPDATE dbo.submissions
                SET platform_id = (SELECT platform_id FROM dbo.programming_handles WHERE handle_id = ?),
                    handle_id = ?,
                    platform_submission_id = ?,
                    problem_code = ?,
                    problem_name = ?,
                    contest_id = ?,
                    language = ?,
                    verdict = ?,
                    submitted_at = ?,
                    execution_time_ms = ?,
                    memory_bytes = ?,
                    problem_rating = ?,
                    problem_tags = ?,
                    source_url = ?,
                    updated_at = SYSUTCDATETIME()
                WHERE submission_id = ?
                """;

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setSubmissionUpdateParameters(statement, submission);
            statement.setLong(15, submission.getSubmissionId());
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw databaseException("updating submission", ex);
        }
    }

    public boolean delete(long submissionId) {
        String sql = "DELETE FROM dbo.submissions WHERE submission_id = ?";

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, submissionId);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw databaseException("deleting submission", ex);
        }
    }

    public boolean updateSourceCrawlStatus(
            long submissionId,
            String sourceCrawlStatus,
            LocalDateTime sourceCrawledAt,
            String sourceCrawlError
    ) {
        String sql = """
                UPDATE dbo.submissions
                SET source_crawl_status = ?,
                    source_crawled_at = ?,
                    source_crawl_error = ?,
                    updated_at = SYSUTCDATETIME()
                WHERE submission_id = ?
                """;

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sourceCrawlStatus);
            setLocalDateTime(statement, 2, sourceCrawledAt);
            statement.setString(3, sourceCrawlError);
            statement.setLong(4, submissionId);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw databaseException("updating submission source crawl status", ex);
        }
    }

    public Optional<Submission> findByHandleAndPlatformSubmissionId(long handleId, String platformSubmissionId) {
        String sql = selectSubmissionSql() + " WHERE handle_id = ? AND platform_submission_id = ?";

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, handleId);
            statement.setString(2, platformSubmissionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw databaseException("finding submission by handle and platform submission id", ex);
        }
    }

    private Optional<Submission> findByHandlePlatformAndRemoteSubmissionId(long handleId, String platformSubmissionId) {
        String sql = """
                SELECT s.submission_id, s.handle_id, s.platform_submission_id, s.problem_code, s.problem_name,
                       s.contest_id, s.language, s.verdict, s.submitted_at, s.execution_time_ms, s.memory_bytes,
                       s.problem_rating, s.problem_tags, s.source_url, s.created_at, s.updated_at
                FROM dbo.submissions s
                JOIN dbo.programming_handles h ON h.platform_id = s.platform_id
                WHERE h.handle_id = ? AND s.platform_submission_id = ?
                """;

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, handleId);
            statement.setString(2, platformSubmissionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw databaseException("finding submission by handle platform and remote submission id", ex);
        }
    }

    private void setSubmissionInsertParameters(PreparedStatement statement, Submission submission) throws SQLException {
        statement.setLong(1, submission.getHandleId());
        setSubmissionParameters(statement, submission, 2);
    }

    private void setSubmissionUpdateParameters(PreparedStatement statement, Submission submission) throws SQLException {
        statement.setLong(1, submission.getHandleId());
        setSubmissionParameters(statement, submission, 2);
    }

    private void setSubmissionParameters(PreparedStatement statement, Submission submission, int startIndex) throws SQLException {
        int index = startIndex;
        statement.setLong(index++, submission.getHandleId());
        statement.setString(index++, submission.getPlatformSubmissionId());
        statement.setString(index++, submission.getProblemCode());
        statement.setString(index++, submission.getProblemName());
        statement.setString(index++, submission.getContestId());
        statement.setString(index++, submission.getLanguage());
        statement.setString(index++, submission.getVerdict());
        setLocalDateTime(statement, index++, submission.getSubmittedAt());
        statement.setObject(index++, submission.getExecutionTimeMs());
        statement.setObject(index++, submission.getMemoryBytes());
        statement.setObject(index++, submission.getProblemRating());
        statement.setString(index++, submission.getProblemTags());
        statement.setString(index, submission.getSourceUrl());
    }

    private String selectSubmissionSql() {
        return """
                SELECT submission_id, handle_id, platform_submission_id, problem_code, problem_name, contest_id,
                       language, verdict, submitted_at, execution_time_ms, memory_bytes, problem_rating,
                       problem_tags, source_url, created_at, updated_at
                FROM dbo.submissions
                """;
    }

    private String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private int setLongValues(PreparedStatement statement, int startIndex, Set<Long> values) throws SQLException {
        int index = startIndex;
        for (Long value : values) {
            statement.setLong(index++, value);
        }
        return index;
    }

    private Submission mapRow(ResultSet resultSet) throws SQLException {
        return new Submission(
                resultSet.getLong("submission_id"),
                resultSet.getLong("handle_id"),
                resultSet.getString("platform_submission_id"),
                resultSet.getString("problem_code"),
                resultSet.getString("problem_name"),
                resultSet.getString("contest_id"),
                resultSet.getString("language"),
                resultSet.getString("verdict"),
                getLocalDateTime(resultSet, "submitted_at"),
                (Integer) resultSet.getObject("execution_time_ms"),
                (Long) resultSet.getObject("memory_bytes"),
                (Integer) resultSet.getObject("problem_rating"),
                resultSet.getString("problem_tags"),
                resultSet.getString("source_url"),
                getLocalDateTime(resultSet, "created_at"),
                getLocalDateTime(resultSet, "updated_at")
        );
    }
}
