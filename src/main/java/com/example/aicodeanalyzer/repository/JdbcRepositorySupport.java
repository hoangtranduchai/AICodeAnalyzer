package com.example.aicodeanalyzer.repository;

import com.example.aicodeanalyzer.config.DatabaseConnectionFactory;
import com.example.aicodeanalyzer.exception.DatabaseException;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

abstract class JdbcRepositorySupport {
    protected final DatabaseConnectionFactory connectionFactory;

    protected JdbcRepositorySupport() {
        this(DatabaseConnectionFactory.fromApplicationProperties());
    }

    protected JdbcRepositorySupport(DatabaseConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "connectionFactory must not be null");
    }

    protected LocalDateTime getLocalDateTime(ResultSet resultSet, String columnName) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    protected LocalDate getLocalDate(ResultSet resultSet, String columnName) throws SQLException {
        Date date = resultSet.getDate(columnName);
        return date == null ? null : date.toLocalDate();
    }

    protected void setLocalDateTime(PreparedStatement statement, int parameterIndex, LocalDateTime value)
            throws SQLException {
        if (value == null) {
            statement.setTimestamp(parameterIndex, null);
            return;
        }
        statement.setTimestamp(parameterIndex, Timestamp.valueOf(value));
    }

    protected void setLocalDate(PreparedStatement statement, int parameterIndex, LocalDate value)
            throws SQLException {
        if (value == null) {
            statement.setDate(parameterIndex, null);
            return;
        }
        statement.setDate(parameterIndex, Date.valueOf(value));
    }

    protected long readGeneratedId(PreparedStatement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (keys.next()) {
                return keys.getLong(1);
            }
        }
        throw new DatabaseException("Insert succeeded, but SQL Server did not return a generated ID.");
    }

    protected DatabaseException databaseException(String action, SQLException ex) {
        return new DatabaseException("Database error while " + action + ": " + ex.getMessage(), ex);
    }
}
