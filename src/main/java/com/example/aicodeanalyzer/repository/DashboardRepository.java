package com.example.aicodeanalyzer.repository;

import com.example.aicodeanalyzer.config.DatabaseConnectionFactory;
import com.example.aicodeanalyzer.model.DashboardSnapshot.DashboardSummary;
import com.example.aicodeanalyzer.model.DashboardSnapshot.HandleAlgorithmStat;
import com.example.aicodeanalyzer.model.DashboardSnapshot.HandleScoreStat;
import com.example.aicodeanalyzer.model.DashboardSnapshot.PlatformSubmissionStat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs aggregate read-only queries for the dashboard screen.
 */
public class DashboardRepository extends JdbcRepositorySupport {
    private static final BigDecimal ZERO_SCORE = BigDecimal.ZERO;

    public DashboardRepository() {
        super();
    }

    public DashboardRepository(DatabaseConnectionFactory connectionFactory) {
        super(connectionFactory);
    }

    public DashboardSummary findSummary() {
        String sql = """
                SELECT
                    (SELECT COUNT(*) FROM dbo.programming_handles) AS total_handles,
                    (SELECT COUNT(*) FROM dbo.submissions) AS total_submissions,
                    (
                        SELECT COUNT(*)
                        FROM dbo.source_codes sc
                        WHERE sc.code_content IS NOT NULL
                          AND NOT EXISTS (
                              SELECT 1
                              FROM dbo.ai_analysis_results ar
                              WHERE ar.submission_id = sc.submission_id
                          )
                    ) AS pending_analysis_sources,
                    (
                        SELECT COUNT(DISTINCT sc.source_code_id)
                        FROM dbo.source_codes sc
                        JOIN dbo.ai_analysis_results ar ON ar.submission_id = sc.submission_id
                    ) AS analyzed_source_codes,
                    (
                        SELECT COUNT(*)
                        FROM dbo.submissions s
                        WHERE s.source_crawl_status IN ('FAILED', 'SKIPPED')
                    ) AS source_issue_count,
                    (
                        SELECT COALESCE(SUM(total_errors), 0)
                        FROM dbo.crawl_logs
                        WHERE started_at >= DATEADD(DAY, -7, SYSUTCDATETIME())
                    ) AS recent_crawl_errors
                """;

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return DashboardSummary.empty();
            }
            return new DashboardSummary(
                    resultSet.getLong("total_handles"),
                    resultSet.getLong("total_submissions"),
                    resultSet.getLong("pending_analysis_sources"),
                    resultSet.getLong("analyzed_source_codes"),
                    resultSet.getLong("source_issue_count"),
                    resultSet.getLong("recent_crawl_errors")
            );
        } catch (SQLException ex) {
            throw databaseException("loading dashboard summary", ex);
        }
    }

    public List<PlatformSubmissionStat> findSubmissionsByPlatform() {
        String sql = """
                SELECT p.name AS platform_name, COUNT(s.submission_id) AS submission_count
                FROM dbo.platforms p
                LEFT JOIN dbo.programming_handles h ON h.platform_id = p.platform_id
                LEFT JOIN dbo.submissions s ON s.handle_id = h.handle_id
                GROUP BY p.platform_id, p.name
                ORDER BY p.platform_id
                """;

        List<PlatformSubmissionStat> stats = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                stats.add(new PlatformSubmissionStat(
                        resultSet.getString("platform_name"),
                        resultSet.getLong("submission_count")
                ));
            }
            return stats;
        } catch (SQLException ex) {
            throw databaseException("loading submissions by platform", ex);
        }
    }

    public List<HandleAlgorithmStat> findAverageAlgorithmScoresByHandle(int limit) {
        String sql = """
                SELECT TOP (?) p.name AS platform_name,
                       h.handle,
                       AVG(CAST(s.algorithm_score AS DECIMAL(10, 2))) AS average_algorithm_score
                FROM dbo.programming_handles h
                JOIN dbo.platforms p ON p.platform_id = h.platform_id
                JOIN dbo.user_skill_scores s ON s.handle_id = h.handle_id
                WHERE s.algorithm_score IS NOT NULL
                GROUP BY p.name, h.handle
                ORDER BY average_algorithm_score DESC, h.handle
                """;

        List<HandleAlgorithmStat> stats = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    stats.add(new HandleAlgorithmStat(
                            resultSet.getString("platform_name"),
                            resultSet.getString("handle"),
                            scoreOrZero(resultSet.getBigDecimal("average_algorithm_score"))
                    ));
                }
            }
            return stats;
        } catch (SQLException ex) {
            throw databaseException("loading average algorithm scores", ex);
        }
    }

    public List<HandleScoreStat> findTopHandlesByOverallScore(int limit) {
        String sql = latestScoreSql("latest.overall_score DESC, h.handle");
        return findTopHandles(sql, limit, "loading top handles by overall score");
    }

    public List<HandleScoreStat> findTopHandlesByAiRisk(int limit) {
        String sql = latestScoreSql("latest.ai_usage_risk_score DESC, h.handle");
        return findTopHandles(sql, limit, "loading top handles by AI risk score");
    }

    private List<HandleScoreStat> findTopHandles(String sql, int limit, String action) {
        List<HandleScoreStat> stats = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    stats.add(new HandleScoreStat(
                            resultSet.getString("platform_name"),
                            resultSet.getString("handle"),
                            scoreOrZero(resultSet.getBigDecimal("data_structure_score")),
                            scoreOrZero(resultSet.getBigDecimal("algorithm_score")),
                            scoreOrZero(resultSet.getBigDecimal("ai_usage_risk_score")),
                            scoreOrZero(resultSet.getBigDecimal("overall_score")),
                            resultSet.getString("summary")
                    ));
                }
            }
            return stats;
        } catch (SQLException ex) {
            throw databaseException(action, ex);
        }
    }

    private String latestScoreSql(String orderBy) {
        return """
                WITH latest_scores AS (
                    SELECT score_id,
                           handle_id,
                           data_structure_score,
                           algorithm_score,
                           ai_usage_risk_score,
                           overall_score,
                           summary,
                           ROW_NUMBER() OVER (
                               PARTITION BY handle_id
                               ORDER BY period_end DESC, generated_at DESC, score_id DESC
                           ) AS row_number
                    FROM dbo.user_skill_scores
                )
                SELECT TOP (?) p.name AS platform_name,
                       h.handle,
                       latest.data_structure_score,
                       latest.algorithm_score,
                       latest.ai_usage_risk_score,
                       latest.overall_score,
                       latest.summary
                FROM latest_scores latest
                JOIN dbo.programming_handles h ON h.handle_id = latest.handle_id
                JOIN dbo.platforms p ON p.platform_id = h.platform_id
                WHERE latest.row_number = 1
                ORDER BY %s
                """.formatted(orderBy);
    }

    private BigDecimal scoreOrZero(BigDecimal value) {
        return value == null ? ZERO_SCORE : value;
    }
}
