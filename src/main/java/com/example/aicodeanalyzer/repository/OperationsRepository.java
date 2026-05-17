package com.example.aicodeanalyzer.repository;

import com.example.aicodeanalyzer.config.DatabaseConnectionFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Read models for operations screens: submissions, source issues, queue, logs, settings, and analysis history.
 */
public class OperationsRepository extends JdbcRepositorySupport {
    public OperationsRepository() {
        super();
    }

    public OperationsRepository(DatabaseConnectionFactory connectionFactory) {
        super(connectionFactory);
    }

    public List<SubmissionOpsRow> searchSubmissions(
            String handle,
            String platform,
            String verdict,
            String language,
            String sourceStatus,
            LocalDate from,
            LocalDate to,
            int limit
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT s.submission_id, p.code AS platform_code, h.handle, s.platform_submission_id,
                       s.problem_code, s.problem_name, s.language, s.verdict, s.submitted_at,
                       s.source_crawl_status, s.source_crawled_at, s.source_crawl_error, s.source_url,
                       latest.analysis_id, latest.model_name, latest.ai_risk_score,
                       latest.data_structures, latest.algorithms, latest.created_at AS latest_analysis_at
                FROM dbo.submissions s
                JOIN dbo.programming_handles h ON h.handle_id = s.handle_id
                JOIN dbo.platforms p ON p.platform_id = s.platform_id
                OUTER APPLY (
                    SELECT TOP (1) ar.analysis_id, ar.model_name, ar.ai_risk_score,
                           ar.data_structures, ar.algorithms, ar.created_at
                    FROM dbo.ai_analysis_results ar
                    WHERE ar.submission_id = s.submission_id
                    ORDER BY ar.created_at DESC, ar.analysis_id DESC
                ) latest
                WHERE 1 = 1
                """);
        List<SqlParam> params = new ArrayList<>();
        addTextFilter(sql, params, "h.handle", handle, true);
        addTextFilter(sql, params, "p.code", platform, false);
        addTextFilter(sql, params, "s.verdict", verdict, true);
        addTextFilter(sql, params, "s.language", language, true);
        addTextFilter(sql, params, "s.source_crawl_status", sourceStatus, false);
        if (from != null) {
            sql.append(" AND s.submitted_at >= ?");
            params.add(new SqlParam(from.atStartOfDay()));
        }
        if (to != null) {
            sql.append(" AND s.submitted_at < ?");
            params.add(new SqlParam(to.plusDays(1).atStartOfDay()));
        }
        sql.append("""
                 ORDER BY s.submitted_at DESC, s.submission_id DESC
                 OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY
                """);
        params.add(new SqlParam(Math.max(1, limit)));

        List<SubmissionOpsRow> rows = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            setParams(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new SubmissionOpsRow(
                            rs.getLong("submission_id"),
                            rs.getString("platform_code"),
                            rs.getString("handle"),
                            rs.getString("platform_submission_id"),
                            rs.getString("problem_code"),
                            rs.getString("problem_name"),
                            rs.getString("language"),
                            rs.getString("verdict"),
                            getLocalDateTime(rs, "submitted_at"),
                            rs.getString("source_crawl_status"),
                            getLocalDateTime(rs, "source_crawled_at"),
                            rs.getString("source_crawl_error"),
                            rs.getString("source_url"),
                            nullableLong(rs, "analysis_id"),
                            rs.getString("model_name"),
                            rs.getBigDecimal("ai_risk_score"),
                            rs.getString("data_structures"),
                            rs.getString("algorithms"),
                            getLocalDateTime(rs, "latest_analysis_at")
                    ));
                }
            }
            return rows;
        } catch (SQLException ex) {
            throw databaseException("searching submissions for operations screen", ex);
        }
    }

    public List<SourceIssueRow> findSourceIssues(int limit) {
        String sql = """
                SELECT s.submission_id, sc.source_code_id, h.handle_id, p.code AS platform_code, h.handle,
                       s.platform_submission_id, s.problem_code, s.language, s.verdict,
                       s.source_crawl_status, s.source_crawled_at, s.source_crawl_error, s.source_url,
                       sc.storage_type, sc.line_count, sc.char_count
                FROM dbo.submissions s
                JOIN dbo.programming_handles h ON h.handle_id = s.handle_id
                JOIN dbo.platforms p ON p.platform_id = s.platform_id
                LEFT JOIN dbo.source_codes sc ON sc.submission_id = s.submission_id
                WHERE s.source_crawl_status IN ('FAILED', 'SKIPPED', 'PENDING')
                   OR sc.source_code_id IS NULL
                   OR sc.code_content IS NULL
                   OR LTRIM(RTRIM(sc.code_content)) = N''
                ORDER BY COALESCE(s.source_crawled_at, s.updated_at, s.created_at) DESC, s.submission_id DESC
                OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY
                """;
        List<SourceIssueRow> rows = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new SourceIssueRow(
                            rs.getLong("submission_id"),
                            nullableLong(rs, "source_code_id"),
                            rs.getLong("handle_id"),
                            rs.getString("platform_code"),
                            rs.getString("handle"),
                            rs.getString("platform_submission_id"),
                            rs.getString("problem_code"),
                            rs.getString("language"),
                            rs.getString("verdict"),
                            rs.getString("source_crawl_status"),
                            getLocalDateTime(rs, "source_crawled_at"),
                            rs.getString("source_crawl_error"),
                            rs.getString("source_url"),
                            rs.getString("storage_type"),
                            nullableInt(rs, "line_count"),
                            nullableInt(rs, "char_count")
                    ));
                }
            }
            return rows;
        } catch (SQLException ex) {
            throw databaseException("finding source issues", ex);
        }
    }

    public List<AiQueueOpsRow> findAiQueue(int limit) {
        if (!tableExists("dbo.analysis_jobs")) {
            return List.of();
        }
        String sql = """
                SELECT aj.analysis_job_id, aj.source_code_id, aj.submission_id, p.code AS platform_code, h.handle,
                       s.platform_submission_id, aj.status, aj.attempt_count, aj.next_retry_at,
                       aj.started_at, aj.finished_at, aj.last_analysis_id, aj.last_error, aj.updated_at
                FROM dbo.analysis_jobs aj
                JOIN dbo.submissions s ON s.submission_id = aj.submission_id
                JOIN dbo.programming_handles h ON h.handle_id = s.handle_id
                JOIN dbo.platforms p ON p.platform_id = s.platform_id
                ORDER BY CASE aj.status
                    WHEN 'RUNNING' THEN 0
                    WHEN 'QUOTA_DELAYED' THEN 1
                    WHEN 'FAILED' THEN 2
                    WHEN 'PENDING' THEN 3
                    WHEN 'SKIPPED' THEN 4
                    ELSE 5 END,
                    aj.updated_at DESC, aj.analysis_job_id DESC
                OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY
                """;
        List<AiQueueOpsRow> rows = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new AiQueueOpsRow(
                            rs.getLong("analysis_job_id"),
                            rs.getLong("source_code_id"),
                            rs.getLong("submission_id"),
                            rs.getString("platform_code"),
                            rs.getString("handle"),
                            rs.getString("platform_submission_id"),
                            rs.getString("status"),
                            rs.getInt("attempt_count"),
                            getLocalDateTime(rs, "next_retry_at"),
                            getLocalDateTime(rs, "started_at"),
                            getLocalDateTime(rs, "finished_at"),
                            nullableLong(rs, "last_analysis_id"),
                            rs.getString("last_error"),
                            getLocalDateTime(rs, "updated_at")
                    ));
                }
            }
            return rows;
        } catch (SQLException ex) {
            throw databaseException("finding AI queue operations rows", ex);
        }
    }

    public List<AnalysisHistoryOpsRow> findAnalysisHistory(int limit) {
        String sql = """
                SELECT ar.analysis_id, ar.submission_id, p.code AS platform_code, h.handle,
                       s.platform_submission_id, s.problem_code, s.problem_name,
                       ar.analyzer_type, ar.analyzer_version, ar.model_name,
                       ar.data_structures, ar.algorithms, ar.code_quality_score, ar.ai_risk_score, ar.ai_risk_level,
                       ar.prompt_hash, ar.raw_response, ar.summary, ar.created_at
                FROM dbo.ai_analysis_results ar
                JOIN dbo.submissions s ON s.submission_id = ar.submission_id
                JOIN dbo.programming_handles h ON h.handle_id = s.handle_id
                JOIN dbo.platforms p ON p.platform_id = s.platform_id
                ORDER BY ar.created_at DESC, ar.analysis_id DESC
                OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY
                """;
        List<AnalysisHistoryOpsRow> rows = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new AnalysisHistoryOpsRow(
                            rs.getLong("analysis_id"),
                            rs.getLong("submission_id"),
                            rs.getString("platform_code"),
                            rs.getString("handle"),
                            rs.getString("platform_submission_id"),
                            rs.getString("problem_code"),
                            rs.getString("problem_name"),
                            rs.getString("analyzer_type"),
                            rs.getString("analyzer_version"),
                            rs.getString("model_name"),
                            rs.getString("data_structures"),
                            rs.getString("algorithms"),
                            rs.getBigDecimal("code_quality_score"),
                            rs.getBigDecimal("ai_risk_score"),
                            rs.getString("ai_risk_level"),
                            rs.getString("prompt_hash"),
                            rs.getString("raw_response"),
                            rs.getString("summary"),
                            getLocalDateTime(rs, "created_at")
                    ));
                }
            }
            return rows;
        } catch (SQLException ex) {
            throw databaseException("finding analysis history", ex);
        }
    }

    public List<CrawlLogOpsRow> findCrawlLogs(int limit) {
        String sql = """
                SELECT crawl_log_id, job_type, status, started_at, finished_at,
                       total_handles, total_new_submissions, total_errors, message, created_at
                FROM dbo.crawl_logs
                ORDER BY started_at DESC, crawl_log_id DESC
                OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY
                """;
        List<CrawlLogOpsRow> rows = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new CrawlLogOpsRow(
                            rs.getLong("crawl_log_id"),
                            rs.getString("job_type"),
                            rs.getString("status"),
                            getLocalDateTime(rs, "started_at"),
                            getLocalDateTime(rs, "finished_at"),
                            rs.getInt("total_handles"),
                            rs.getInt("total_new_submissions"),
                            rs.getInt("total_errors"),
                            rs.getString("message"),
                            getLocalDateTime(rs, "created_at")
                    ));
                }
            }
            return rows;
        } catch (SQLException ex) {
            throw databaseException("finding crawl logs for operations screen", ex);
        }
    }

    public List<AppSettingOpsRow> findAppSettings() {
        String sql = """
                SELECT setting_key, setting_value, description, created_at, updated_at
                FROM dbo.app_settings
                ORDER BY setting_key
                """;
        List<AppSettingOpsRow> rows = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                rows.add(new AppSettingOpsRow(
                        rs.getString("setting_key"),
                        rs.getString("setting_value"),
                        rs.getString("description"),
                        getLocalDateTime(rs, "created_at"),
                        getLocalDateTime(rs, "updated_at")
                ));
            }
            return rows;
        } catch (SQLException ex) {
            throw databaseException("finding app settings for operations screen", ex);
        }
    }

    public List<ErrorLogOpsRow> findErrorLogs(int limit) {
        String sql = """
                SELECT error_log_id, component, severity, sanitized_message, stack_trace, created_at
                FROM dbo.error_logs
                ORDER BY created_at DESC, error_log_id DESC
                OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY
                """;
        List<ErrorLogOpsRow> rows = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new ErrorLogOpsRow(
                            rs.getLong("error_log_id"),
                            rs.getString("component"),
                            rs.getString("severity"),
                            rs.getString("sanitized_message"),
                            rs.getString("stack_trace"),
                            getLocalDateTime(rs, "created_at")
                    ));
                }
            }
            return rows;
        } catch (SQLException ex) {
            throw databaseException("finding error logs for operations screen", ex);
        }
    }

    private boolean tableExists(String tableName) {
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT OBJECT_ID(?, 'U') AS object_id")) {
            statement.setString(1, tableName);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getObject("object_id") != null;
            }
        } catch (SQLException ignored) {
            return false;
        }
    }

    private void addTextFilter(StringBuilder sql, List<SqlParam> params, String column, String value, boolean contains) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value.trim())) {
            return;
        }
        if (contains) {
            sql.append(" AND LOWER(").append(column).append(") LIKE ?");
            params.add(new SqlParam("%" + value.trim().toLowerCase() + "%"));
        } else {
            sql.append(" AND ").append(column).append(" = ?");
            params.add(new SqlParam(value.trim().toUpperCase()));
        }
    }

    private void setParams(PreparedStatement statement, List<SqlParam> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object value = params.get(i).value();
            if (value instanceof LocalDateTime dateTime) {
                setLocalDateTime(statement, i + 1, dateTime);
            } else if (value instanceof Integer integer) {
                statement.setInt(i + 1, integer);
            } else {
                statement.setString(i + 1, String.valueOf(value));
            }
        }
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private record SqlParam(Object value) {
    }

    public record SubmissionOpsRow(
            Long submissionId,
            String platformCode,
            String handle,
            String remoteId,
            String problemCode,
            String problemName,
            String language,
            String verdict,
            LocalDateTime submittedAt,
            String sourceCrawlStatus,
            LocalDateTime sourceCrawledAt,
            String sourceCrawlError,
            String sourceUrl,
            Long latestAnalysisId,
            String latestModelName,
            BigDecimal latestAiRiskScore,
            String latestDataStructures,
            String latestAlgorithms,
            LocalDateTime latestAnalysisAt
    ) {
    }

    public record SourceIssueRow(
            Long submissionId,
            Long sourceCodeId,
            Long handleId,
            String platformCode,
            String handle,
            String remoteId,
            String problemCode,
            String language,
            String verdict,
            String sourceCrawlStatus,
            LocalDateTime sourceCrawledAt,
            String sourceCrawlError,
            String sourceUrl,
            String storageType,
            Integer lineCount,
            Integer charCount
    ) {
    }

    public record AiQueueOpsRow(
            Long analysisJobId,
            Long sourceCodeId,
            Long submissionId,
            String platformCode,
            String handle,
            String remoteId,
            String status,
            int attemptCount,
            LocalDateTime nextRetryAt,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            Long lastAnalysisId,
            String lastError,
            LocalDateTime updatedAt
    ) {
    }

    public record AnalysisHistoryOpsRow(
            Long analysisId,
            Long submissionId,
            String platformCode,
            String handle,
            String remoteId,
            String problemCode,
            String problemName,
            String analyzerType,
            String analyzerVersion,
            String modelName,
            String dataStructures,
            String algorithms,
            BigDecimal codeQualityScore,
            BigDecimal aiRiskScore,
            String aiRiskLevel,
            String promptHash,
            String rawResponse,
            String summary,
            LocalDateTime createdAt
    ) {
    }

    public record CrawlLogOpsRow(
            Long crawlLogId,
            String jobType,
            String status,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            int totalHandles,
            int totalNewSubmissions,
            int totalErrors,
            String message,
            LocalDateTime createdAt
    ) {
    }

    public record AppSettingOpsRow(
            String settingKey,
            String settingValue,
            String description,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record ErrorLogOpsRow(
            Long errorLogId,
            String component,
            String severity,
            String sanitizedMessage,
            String stackTrace,
            LocalDateTime createdAt
    ) {
    }
}
