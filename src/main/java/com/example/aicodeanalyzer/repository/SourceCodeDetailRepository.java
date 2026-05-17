package com.example.aicodeanalyzer.repository;

import com.example.aicodeanalyzer.config.DatabaseConnectionFactory;
import com.example.aicodeanalyzer.model.AiAnalysisResult;
import com.example.aicodeanalyzer.model.SourceCodeDetail;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Loads source code together with submission, handle, platform, and latest analysis data.
 */
public class SourceCodeDetailRepository extends JdbcRepositorySupport {

    public SourceCodeDetailRepository() {
        super();
    }

    public SourceCodeDetailRepository(DatabaseConnectionFactory connectionFactory) {
        super(connectionFactory);
    }

    public List<SourceCodeDetail> findRecent(int limit) {
        return findRecent(limit, true);
    }

    public List<SourceCodeDetail> findRecentMetadata(int limit) {
        return findRecent(limit, false);
    }

    private List<SourceCodeDetail> findRecent(int limit, boolean includeCodeContent) {
        String sql = selectSourceCodeDetailSql(includeCodeContent) + """
                ORDER BY s.submitted_at DESC, sc.source_code_id DESC
                OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY
                """;

        List<SourceCodeDetail> details = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    details.add(mapRow(resultSet));
                }
            }
            return details;
        } catch (SQLException ex) {
            throw databaseException("loading recent source code details", ex);
        }
    }

    public Optional<SourceCodeDetail> findBySourceCodeId(long sourceCodeId) {
        String sql = selectSourceCodeDetailSql(true) + "WHERE sc.source_code_id = ?";

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, sourceCodeId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw databaseException("loading source code detail", ex);
        }
    }

    public List<SourceCodeDetail> findRecentByHandle(String platformCode, String handle, int limit) {
        String sql = selectSourceCodeDetailSql(true) + """
                WHERE p.code = ? AND h.handle = ?
                ORDER BY s.submitted_at DESC, sc.source_code_id DESC
                OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY
                """;

        List<SourceCodeDetail> details = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, platformCode);
            statement.setString(2, handle);
            statement.setInt(3, Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    details.add(mapRow(resultSet));
                }
            }
            return details;
        } catch (SQLException ex) {
            throw databaseException("loading source code history by handle", ex);
        }
    }

    private String selectSourceCodeDetailSql(boolean includeCodeContent) {
        String codeContentExpression = includeCodeContent ? "sc.code_content" : "NULL";
        return """
                SELECT sc.source_code_id,
                       sc.submission_id,
                       %s AS code_content,
                       sc.code_hash,
                       sc.line_count,
                       sc.char_count,
                       sc.fetched_at,
                       s.problem_code,
                       s.problem_name,
                       s.language,
                       s.verdict,
                       s.submitted_at,
                       h.handle,
                       p.code AS platform_code,
                       p.name AS platform_name,
                       ar.analysis_id,
                       ar.analyzer_type,
                       ar.analyzer_version,
                       ar.model_name,
                       ar.data_structures,
                       ar.algorithms,
                       ar.complexity_estimate,
                       ar.code_quality_score,
                       ar.ai_risk_score,
                       ar.ai_risk_level,
                       ar.summary,
                       ar.raw_response,
                       ar.prompt_hash,
                       ar.created_at AS analysis_created_at,
                       ar.updated_at AS analysis_updated_at
                FROM dbo.source_codes sc
                JOIN dbo.submissions s ON s.submission_id = sc.submission_id
                JOIN dbo.programming_handles h ON h.handle_id = s.handle_id
                JOIN dbo.platforms p ON p.platform_id = h.platform_id
                LEFT JOIN dbo.ai_analysis_results ar ON ar.analysis_id = (
                    SELECT TOP 1 latest.analysis_id
                    FROM dbo.ai_analysis_results latest
                    WHERE latest.submission_id = sc.submission_id
                    ORDER BY latest.created_at DESC, latest.analysis_id DESC
                )
                """.formatted(codeContentExpression);
    }

    private SourceCodeDetail mapRow(ResultSet resultSet) throws SQLException {
        return new SourceCodeDetail(
                resultSet.getLong("source_code_id"),
                resultSet.getLong("submission_id"),
                resultSet.getString("platform_code"),
                resultSet.getString("platform_name"),
                resultSet.getString("handle"),
                resultSet.getString("problem_code"),
                resultSet.getString("problem_name"),
                resultSet.getString("language"),
                resultSet.getString("verdict"),
                getLocalDateTime(resultSet, "submitted_at"),
                resultSet.getString("code_content"),
                resultSet.getString("code_hash"),
                (Integer) resultSet.getObject("line_count"),
                (Integer) resultSet.getObject("char_count"),
                getLocalDateTime(resultSet, "fetched_at"),
                mapAnalysis(resultSet)
        );
    }

    private AiAnalysisResult mapAnalysis(ResultSet resultSet) throws SQLException {
        long analysisId = resultSet.getLong("analysis_id");
        if (resultSet.wasNull()) {
            return null;
        }

        return new AiAnalysisResult(
                analysisId,
                resultSet.getLong("submission_id"),
                resultSet.getString("analyzer_type"),
                resultSet.getString("analyzer_version"),
                resultSet.getString("model_name"),
                resultSet.getString("data_structures"),
                resultSet.getString("algorithms"),
                resultSet.getString("complexity_estimate"),
                resultSet.getBigDecimal("code_quality_score"),
                resultSet.getBigDecimal("ai_risk_score"),
                resultSet.getString("ai_risk_level"),
                resultSet.getString("summary"),
                resultSet.getString("raw_response"),
                resultSet.getString("prompt_hash"),
                getLocalDateTime(resultSet, "analysis_created_at"),
                getLocalDateTime(resultSet, "analysis_updated_at")
        );
    }
}
