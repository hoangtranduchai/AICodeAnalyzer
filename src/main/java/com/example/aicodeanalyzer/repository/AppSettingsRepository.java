package com.example.aicodeanalyzer.repository;

import com.example.aicodeanalyzer.config.DatabaseConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Stores small application settings in SQL Server.
 */
public class AppSettingsRepository extends JdbcRepositorySupport {

    public AppSettingsRepository() {
        super();
    }

    public AppSettingsRepository(DatabaseConnectionFactory connectionFactory) {
        super(connectionFactory);
    }

    public Optional<String> findValue(String key) {
        String sql = "SELECT setting_value FROM dbo.app_settings WHERE setting_key = ?";

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.ofNullable(resultSet.getString("setting_value"))
                        : Optional.empty();
            }
        } catch (SQLException ex) {
            throw databaseException("finding app setting " + key, ex);
        }
    }

    public void upsertValue(String key, String value, String description) {
        String sql = """
                MERGE dbo.app_settings AS target
                USING (SELECT ? AS setting_key, ? AS setting_value, ? AS description) AS source
                ON target.setting_key = source.setting_key
                WHEN MATCHED THEN
                    UPDATE SET setting_value = source.setting_value,
                               description = source.description,
                               updated_at = SYSUTCDATETIME()
                WHEN NOT MATCHED THEN
                    INSERT (setting_key, setting_value, description)
                    VALUES (source.setting_key, source.setting_value, source.description);
                """;

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.setString(3, description);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw databaseException("saving app setting " + key, ex);
        }
    }
}
