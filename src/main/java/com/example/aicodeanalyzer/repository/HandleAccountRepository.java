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
        String sql = "DELETE FROM dbo.programming_handles WHERE handle_id = ?";

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, handleId);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw databaseException("deleting handle", ex);
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
}
