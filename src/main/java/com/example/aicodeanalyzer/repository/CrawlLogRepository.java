package com.example.aicodeanalyzer.repository;

import com.example.aicodeanalyzer.config.DatabaseConnectionFactory;
import com.example.aicodeanalyzer.exception.DatabaseException;
import com.example.aicodeanalyzer.model.CrawlLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Provides database operations for crawl logs and per-handle crawl log items.
 */
public class CrawlLogRepository extends JdbcRepositorySupport {

    public CrawlLogRepository() {
        super();
    }

    public CrawlLogRepository(DatabaseConnectionFactory connectionFactory) {
        super(connectionFactory);
    }

    public Optional<CrawlLog> findById(long crawlLogId) {
        String sql = selectCrawlLogSql() + " WHERE crawl_log_id = ?";

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, crawlLogId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw databaseException("finding crawl log by id", ex);
        }
    }

    public List<CrawlLog> findAll() {
        String sql = selectCrawlLogSql() + " ORDER BY started_at DESC, crawl_log_id DESC";

        List<CrawlLog> crawlLogs = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                crawlLogs.add(mapRow(resultSet));
            }
            return crawlLogs;
        } catch (SQLException ex) {
            throw databaseException("finding all crawl logs", ex);
        }
    }

    public Optional<CrawlLog> findLatest() {
        String sql = selectCrawlLogSql() + """
                 ORDER BY started_at DESC, crawl_log_id DESC
                 OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY
                """;

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
        } catch (SQLException ex) {
            throw databaseException("finding latest crawl log", ex);
        }
    }

    public CrawlLog save(CrawlLog crawlLog) {
        String sql = """
                INSERT INTO dbo.crawl_logs
                    (job_type, status, started_at, finished_at, total_handles, total_new_submissions, total_errors,
                     message)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setCrawlLogParameters(statement, crawlLog);
            statement.executeUpdate();

            long generatedId = readGeneratedId(statement);
            return findById(generatedId)
                    .orElseThrow(() -> new DatabaseException("Crawl log was inserted but could not be reloaded."));
        } catch (SQLException ex) {
            throw databaseException("saving crawl log", ex);
        }
    }

    public boolean update(CrawlLog crawlLog) {
        String sql = """
                UPDATE dbo.crawl_logs
                SET job_type = ?,
                    status = ?,
                    started_at = ?,
                    finished_at = ?,
                    total_handles = ?,
                    total_new_submissions = ?,
                    total_errors = ?,
                    message = ?,
                    updated_at = SYSUTCDATETIME()
                WHERE crawl_log_id = ?
                """;

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setCrawlLogParameters(statement, crawlLog);
            statement.setLong(9, crawlLog.getCrawlLogId());
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw databaseException("updating crawl log", ex);
        }
    }

    public boolean delete(long crawlLogId) {
        String sql = "DELETE FROM dbo.crawl_logs WHERE crawl_log_id = ?";

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, crawlLogId);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw databaseException("deleting crawl log", ex);
        }
    }

    private void setCrawlLogParameters(PreparedStatement statement, CrawlLog crawlLog) throws SQLException {
        statement.setString(1, crawlLog.getJobType());
        statement.setString(2, crawlLog.getStatus());
        setLocalDateTime(statement, 3, crawlLog.getStartedAt());
        setLocalDateTime(statement, 4, crawlLog.getFinishedAt());
        statement.setInt(5, crawlLog.getTotalHandles());
        statement.setInt(6, crawlLog.getTotalNewSubmissions());
        statement.setInt(7, crawlLog.getTotalErrors());
        statement.setString(8, crawlLog.getMessage());
    }

    private String selectCrawlLogSql() {
        return """
                SELECT crawl_log_id, job_type, status, started_at, finished_at, total_handles,
                       total_new_submissions, total_errors, message, created_at, updated_at
                FROM dbo.crawl_logs
                """;
    }

    private CrawlLog mapRow(ResultSet resultSet) throws SQLException {
        return new CrawlLog(
                resultSet.getLong("crawl_log_id"),
                resultSet.getString("job_type"),
                resultSet.getString("status"),
                getLocalDateTime(resultSet, "started_at"),
                getLocalDateTime(resultSet, "finished_at"),
                resultSet.getInt("total_handles"),
                resultSet.getInt("total_new_submissions"),
                resultSet.getInt("total_errors"),
                resultSet.getString("message"),
                getLocalDateTime(resultSet, "created_at"),
                getLocalDateTime(resultSet, "updated_at")
        );
    }
}
