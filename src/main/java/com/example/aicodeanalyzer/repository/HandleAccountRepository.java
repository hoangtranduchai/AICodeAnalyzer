package com.example.aicodeanalyzer.repository;

import com.example.aicodeanalyzer.config.DatabaseConnectionFactory;
import com.example.aicodeanalyzer.exception.DatabaseException;
import com.example.aicodeanalyzer.model.HandleAccount;

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
 * Provides database operations for tracked programming handles.
 */
public class HandleAccountRepository extends JdbcRepositorySupport {

    public HandleAccountRepository() {
        super();
    }

    public HandleAccountRepository(DatabaseConnectionFactory connectionFactory) {
        super(connectionFactory);
    }

    public Optional<HandleAccount> findById(long handleId) {
        String sql = """
                SELECT handle_id, platform_id, handle, display_name, group_name, rating, rank_name,
                       general_evaluation, consent_status,
                       is_active, last_crawled_at, notes, created_at, updated_at
                FROM dbo.programming_handles
                WHERE handle_id = ?
                """;

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, handleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw databaseException("finding handle by id", ex);
        }
    }

    public List<HandleAccount> findAll() {
        String sql = """
                SELECT handle_id, platform_id, handle, display_name, group_name, rating, rank_name,
                       general_evaluation, consent_status,
                       is_active, last_crawled_at, notes, created_at, updated_at
                FROM dbo.programming_handles
                ORDER BY platform_id, handle
                """;

        List<HandleAccount> handles = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                handles.add(mapRow(resultSet));
            }
            return handles;
        } catch (SQLException ex) {
            throw databaseException("finding all handles", ex);
        }
    }

    public List<HandlePipelineStats> findPipelineStats() {
        String sql = """
                WITH analysis_by_submission AS (
                    SELECT submission_id,
                           COUNT(analysis_id) AS analysis_count
                    FROM dbo.ai_analysis_results
                    GROUP BY submission_id
                ),
                latest_handle_log AS (
                    SELECT h.handle_id,
                           cl.total_new_submissions,
                           cl.status,
                           ROW_NUMBER() OVER (
                               PARTITION BY h.handle_id
                               ORDER BY cl.started_at DESC, cl.crawl_log_id DESC
                           ) AS row_number
                    FROM dbo.programming_handles h
                    JOIN dbo.platforms p ON p.platform_id = h.platform_id
                    LEFT JOIN dbo.crawl_logs cl
                        ON cl.message LIKE '%' + p.code + '/' + h.handle + '%'
                       AND cl.total_handles = 1
                )
                SELECT h.handle_id,
                       COUNT(s.submission_id) AS total_submissions,
                       SUM(CASE
                               WHEN sc.source_code_id IS NOT NULL
                                AND sc.code_content IS NOT NULL
                                AND COALESCE(analysis.analysis_count, 0) = 0
                               THEN 1 ELSE 0
                           END) AS pending_ai,
                       SUM(CASE WHEN s.source_crawl_status IN ('FAILED', 'SKIPPED') THEN 1 ELSE 0 END) AS source_issues,
                       COALESCE(MAX(CASE WHEN latest.row_number = 1 THEN latest.total_new_submissions END), 0) AS last_new_submissions,
                       COALESCE(MAX(CASE WHEN latest.row_number = 1 THEN latest.status END), '-') AS last_status
                FROM dbo.programming_handles h
                LEFT JOIN dbo.submissions s ON s.handle_id = h.handle_id
                LEFT JOIN dbo.source_codes sc ON sc.submission_id = s.submission_id
                LEFT JOIN analysis_by_submission analysis ON analysis.submission_id = s.submission_id
                LEFT JOIN latest_handle_log latest ON latest.handle_id = h.handle_id AND latest.row_number = 1
                GROUP BY h.handle_id
                """;

        List<HandlePipelineStats> stats = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                stats.add(new HandlePipelineStats(
                        resultSet.getLong("handle_id"),
                        resultSet.getLong("total_submissions"),
                        resultSet.getLong("pending_ai"),
                        resultSet.getLong("source_issues"),
                        resultSet.getLong("last_new_submissions"),
                        resultSet.getString("last_status")
                ));
            }
            return stats;
        } catch (SQLException ex) {
            throw databaseException("finding handle pipeline stats", ex);
        }
    }

    public Optional<HandleAccount> findHandleByPlatformAndName(long platformId, String handle) {
        String sql = """
                SELECT handle_id, platform_id, handle, display_name, group_name, rating, rank_name,
                       general_evaluation, consent_status,
                       is_active, last_crawled_at, notes, created_at, updated_at
                FROM dbo.programming_handles
                WHERE platform_id = ? AND handle = ?
                """;

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, platformId);
            statement.setString(2, handle);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw databaseException("finding handle by platform and name", ex);
        }
    }

    public Optional<HandleAccount> findHandleByPlatformAndName(String platformCode, String handle) {
        String sql = """
                SELECT h.handle_id, h.platform_id, h.handle, h.display_name, h.group_name, h.rating, h.rank_name,
                       h.general_evaluation, h.consent_status,
                       h.is_active, h.last_crawled_at, h.notes, h.created_at, h.updated_at
                FROM dbo.programming_handles h
                JOIN dbo.platforms p ON p.platform_id = h.platform_id
                WHERE p.code = ? AND h.handle = ?
                """;

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, platformCode);
            statement.setString(2, handle);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw databaseException("finding handle by platform code and name", ex);
        }
    }

    public HandleAccount save(HandleAccount handleAccount) {
        String sql = """
                INSERT INTO dbo.programming_handles
                    (platform_id, handle, display_name, group_name, rating, rank_name, general_evaluation,
                     consent_status, is_active, last_crawled_at, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, handleAccount.getPlatformId());
            statement.setString(2, handleAccount.getHandle());
            statement.setString(3, handleAccount.getDisplayName());
            statement.setString(4, handleAccount.getGroupName());
            statement.setObject(5, handleAccount.getRating());
            statement.setString(6, handleAccount.getRankName());
            statement.setString(7, handleAccount.getGeneralEvaluation());
            statement.setString(8, handleAccount.getConsentStatus());
            statement.setBoolean(9, handleAccount.isActive());
            setLocalDateTime(statement, 10, handleAccount.getLastCrawledAt());
            statement.setString(11, handleAccount.getNotes());
            statement.executeUpdate();

            long generatedId = readGeneratedId(statement);
            return findById(generatedId)
                    .orElseThrow(() -> new DatabaseException("Handle was inserted but could not be reloaded."));
        } catch (SQLException ex) {
            throw databaseException("saving handle", ex);
        }
    }

    public boolean update(HandleAccount handleAccount) {
        String sql = """
                UPDATE dbo.programming_handles
                SET platform_id = ?,
                    handle = ?,
                    display_name = ?,
                    group_name = ?,
                    rating = ?,
                    rank_name = ?,
                    general_evaluation = ?,
                    consent_status = ?,
                    is_active = ?,
                    last_crawled_at = ?,
                    notes = ?,
                    updated_at = SYSUTCDATETIME()
                WHERE handle_id = ?
                """;

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, handleAccount.getPlatformId());
            statement.setString(2, handleAccount.getHandle());
            statement.setString(3, handleAccount.getDisplayName());
            statement.setString(4, handleAccount.getGroupName());
            statement.setObject(5, handleAccount.getRating());
            statement.setString(6, handleAccount.getRankName());
            statement.setString(7, handleAccount.getGeneralEvaluation());
            statement.setString(8, handleAccount.getConsentStatus());
            statement.setBoolean(9, handleAccount.isActive());
            setLocalDateTime(statement, 10, handleAccount.getLastCrawledAt());
            statement.setString(11, handleAccount.getNotes());
            statement.setLong(12, handleAccount.getHandleId());
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw databaseException("updating handle", ex);
        }
    }

    public boolean updateLastCrawledAt(long handleId, LocalDateTime lastCrawledAt) {
        String sql = """
                UPDATE dbo.programming_handles
                SET last_crawled_at = ?,
                    updated_at = SYSUTCDATETIME()
                WHERE handle_id = ?
                """;

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setLocalDateTime(statement, 1, lastCrawledAt);
            statement.setLong(2, handleId);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw databaseException("updating handle last crawled time", ex);
        }
    }

    public boolean delete(long handleId) {
        try (Connection connection = connectionFactory.createConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                deleteByHandleId(
                        connection,
                        "DELETE FROM dbo.analysis_jobs WHERE submission_id IN "
                                + "(SELECT submission_id FROM dbo.submissions WHERE handle_id = ?)",
                        handleId
                );
                deleteByHandleId(
                        connection,
                        "DELETE FROM dbo.ai_analysis_results WHERE submission_id IN "
                                + "(SELECT submission_id FROM dbo.submissions WHERE handle_id = ?)",
                        handleId
                );
                deleteByHandleId(
                        connection,
                        "DELETE FROM dbo.source_codes WHERE submission_id IN "
                                + "(SELECT submission_id FROM dbo.submissions WHERE handle_id = ?)",
                        handleId
                );
                deleteByHandleId(connection, "DELETE FROM dbo.submissions WHERE handle_id = ?", handleId);
                deleteByHandleId(connection, "DELETE FROM dbo.user_skill_scores WHERE handle_id = ?", handleId);
                boolean deleted = deleteByHandleId(
                        connection,
                        "DELETE FROM dbo.programming_handles WHERE handle_id = ?",
                        handleId
                ) > 0;
                connection.commit();
                connection.setAutoCommit(previousAutoCommit);
                return deleted;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                connection.setAutoCommit(previousAutoCommit);
                throw ex;
            }
        } catch (SQLException ex) {
            throw databaseException("deleting handle", ex);
        }
    }

    private int deleteByHandleId(Connection connection, String sql, long handleId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, handleId);
            return statement.executeUpdate();
        }
    }

    private HandleAccount mapRow(ResultSet resultSet) throws SQLException {
        return new HandleAccount(
                resultSet.getLong("handle_id"),
                resultSet.getLong("platform_id"),
                resultSet.getString("handle"),
                resultSet.getString("display_name"),
                resultSet.getString("group_name"),
                (Integer) resultSet.getObject("rating"),
                resultSet.getString("rank_name"),
                resultSet.getString("general_evaluation"),
                resultSet.getString("consent_status"),
                resultSet.getBoolean("is_active"),
                getLocalDateTime(resultSet, "last_crawled_at"),
                resultSet.getString("notes"),
                getLocalDateTime(resultSet, "created_at"),
                getLocalDateTime(resultSet, "updated_at")
        );
    }

    public record HandlePipelineStats(
            long handleId,
            long totalSubmissions,
            long pendingAi,
            long sourceIssues,
            long lastNewSubmissions,
            String lastStatus
    ) {
    }
}
