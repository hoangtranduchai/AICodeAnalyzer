package com.example.aicodeanalyzer.repository;

import com.example.aicodeanalyzer.config.DatabaseConnectionFactory;
import com.example.aicodeanalyzer.exception.DatabaseException;
import com.example.aicodeanalyzer.model.Platform;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Provides database operations for online judge platforms.
 */
public class PlatformRepository extends JdbcRepositorySupport {

    public PlatformRepository() {
        super();
    }

    public PlatformRepository(DatabaseConnectionFactory connectionFactory) {
        super(connectionFactory);
    }

    public Optional<Platform> findById(long platformId) {
        String sql = """
                SELECT platform_id, code, name, base_url, api_url, is_active, created_at, updated_at
                FROM dbo.platforms
                WHERE platform_id = ?
                """;

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, platformId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw databaseException("finding platform by id", ex);
        }
    }

    public List<Platform> findAll() {
        String sql = """
                SELECT platform_id, code, name, base_url, api_url, is_active, created_at, updated_at
                FROM dbo.platforms
                ORDER BY platform_id
                """;

        List<Platform> platforms = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                platforms.add(mapRow(resultSet));
            }
            return platforms;
        } catch (SQLException ex) {
            throw databaseException("finding all platforms", ex);
        }
    }

    public Platform save(Platform platform) {
        String sql = """
                INSERT INTO dbo.platforms (code, name, base_url, api_url, is_active)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, platform.getCode());
            statement.setString(2, platform.getName());
            statement.setString(3, platform.getBaseUrl());
            statement.setString(4, platform.getApiUrl());
            statement.setBoolean(5, platform.isActive());
            statement.executeUpdate();

            long generatedId = readGeneratedId(statement);
            return findById(generatedId)
                    .orElseThrow(() -> new DatabaseException("Platform was inserted but could not be reloaded."));
        } catch (SQLException ex) {
            throw databaseException("saving platform", ex);
        }
    }

    public boolean update(Platform platform) {
        String sql = """
                UPDATE dbo.platforms
                SET code = ?,
                    name = ?,
                    base_url = ?,
                    api_url = ?,
                    is_active = ?,
                    updated_at = SYSUTCDATETIME()
                WHERE platform_id = ?
                """;

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, platform.getCode());
            statement.setString(2, platform.getName());
            statement.setString(3, platform.getBaseUrl());
            statement.setString(4, platform.getApiUrl());
            statement.setBoolean(5, platform.isActive());
            statement.setLong(6, platform.getPlatformId());
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw databaseException("updating platform", ex);
        }
    }

    public boolean delete(long platformId) {
        String sql = "DELETE FROM dbo.platforms WHERE platform_id = ?";

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, platformId);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw databaseException("deleting platform", ex);
        }
    }

    private Platform mapRow(ResultSet resultSet) throws SQLException {
        return new Platform(
                resultSet.getLong("platform_id"),
                resultSet.getString("code"),
                resultSet.getString("name"),
                resultSet.getString("base_url"),
                resultSet.getString("api_url"),
                resultSet.getBoolean("is_active"),
                getLocalDateTime(resultSet, "created_at"),
                getLocalDateTime(resultSet, "updated_at")
        );
    }
}
