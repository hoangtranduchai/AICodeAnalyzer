package com.example.aicodeanalyzer.repository;

import com.example.aicodeanalyzer.config.DatabaseConnectionFactory;
import com.example.aicodeanalyzer.exception.DatabaseException;
import com.example.aicodeanalyzer.model.AiAnalysisResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Provides database operations for AI/rule-based analysis results.
 */
public class AiAnalysisResultRepository extends JdbcRepositorySupport {

    public AiAnalysisResultRepository() {
        super();
    }

    public AiAnalysisResultRepository(DatabaseConnectionFactory connectionFactory) {
        super(connectionFactory);
    }

    public Optional<AiAnalysisResult> findById(long analysisId) {
        String sql = selectAnalysisSql() + " WHERE analysis_id = ?";

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, analysisId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw databaseException("finding AI analysis result by id", ex);
        }
    }

    public List<AiAnalysisResult> findAll() {
        String sql = selectAnalysisSql() + " ORDER BY created_at DESC, analysis_id DESC";

        List<AiAnalysisResult> results = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                results.add(mapRow(resultSet));
            }
            return results;
        } catch (SQLException ex) {
            throw databaseException("finding all AI analysis results", ex);
        }
    }

    public List<AiAnalysisResult> findRecent(int limit) {
        String sql = selectAnalysisSql() + """
                 ORDER BY created_at DESC, analysis_id DESC
                 OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY
                """;

        List<AiAnalysisResult> results = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(mapRow(resultSet));
                }
            }
            return results;
        } catch (SQLException ex) {
            throw databaseException("finding recent AI analysis results", ex);
        }
    }

    public List<AiAnalysisResult> findBySubmissionIds(Set<Long> submissionIds) {
        if (submissionIds == null || submissionIds.isEmpty()) {
            return List.of();
        }

        String sql = selectAnalysisSql()
                + " WHERE submission_id IN (" + placeholders(submissionIds.size()) + ")"
                + " ORDER BY created_at DESC, analysis_id DESC";

        List<AiAnalysisResult> results = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setLongValues(statement, 1, submissionIds);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(mapRow(resultSet));
                }
            }
            return results;
        } catch (SQLException ex) {
            throw databaseException("finding AI analysis results by submissions", ex);
        }
    }

    public List<AiAnalysisResult> findLatestBySubmissionIds(Set<Long> submissionIds) {
        if (submissionIds == null || submissionIds.isEmpty()) {
            return List.of();
        }

        String sql = """
                SELECT ar.analysis_id, ar.submission_id, ar.analyzer_type, ar.analyzer_version, ar.model_name,
                       ar.data_structures, ar.algorithms, ar.complexity_estimate, ar.code_quality_score,
                       ar.ai_risk_score, ar.ai_risk_level, ar.summary, ar.raw_response, ar.prompt_hash,
                       ar.created_at, ar.updated_at
                FROM dbo.ai_analysis_results ar
                WHERE ar.submission_id IN (%s)
                  AND ar.analysis_id = (
                      SELECT TOP 1 latest.analysis_id
                      FROM dbo.ai_analysis_results latest
                      WHERE latest.submission_id = ar.submission_id
                      ORDER BY latest.created_at DESC, latest.analysis_id DESC
                  )
                ORDER BY ar.submission_id ASC, ar.created_at ASC
                """.formatted(placeholders(submissionIds.size()));

        List<AiAnalysisResult> results = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setLongValues(statement, 1, submissionIds);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(mapRow(resultSet));
                }
            }
            return results;
        } catch (SQLException ex) {
            throw databaseException("finding latest AI analysis results by submissions", ex);
        }
    }

    public List<AiAnalysisResult> findBySubmissionId(long submissionId) {
        String sql = selectAnalysisSql() + " WHERE submission_id = ? ORDER BY created_at DESC, analysis_id DESC";

        List<AiAnalysisResult> results = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, submissionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(mapRow(resultSet));
                }
            }
            return results;
        } catch (SQLException ex) {
            throw databaseException("finding AI analysis results by submission", ex);
        }
    }

    public List<AiAnalysisResult> findByHandleId(long handleId) {
        String sql = """
                SELECT ar.analysis_id, ar.submission_id, ar.analyzer_type, ar.analyzer_version, ar.model_name,
                       ar.data_structures, ar.algorithms, ar.complexity_estimate, ar.code_quality_score,
                       ar.ai_risk_score, ar.ai_risk_level, ar.summary, ar.raw_response, ar.prompt_hash,
                       ar.created_at, ar.updated_at
                FROM dbo.ai_analysis_results ar
                JOIN dbo.submissions s ON s.submission_id = ar.submission_id
                WHERE s.handle_id = ?
                  AND ar.analysis_id = (
                      SELECT TOP 1 latest.analysis_id
                      FROM dbo.ai_analysis_results latest
                      WHERE latest.submission_id = ar.submission_id
                      ORDER BY latest.created_at DESC, latest.analysis_id DESC
                  )
                ORDER BY s.submitted_at ASC, ar.created_at ASC
                """;

        List<AiAnalysisResult> results = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, handleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(mapRow(resultSet));
                }
            }
            return results;
        } catch (SQLException ex) {
            throw databaseException("finding latest AI analysis results by handle", ex);
        }
    }

    public AiAnalysisResult save(AiAnalysisResult result) {
        String sql = """
                INSERT INTO dbo.ai_analysis_results
                    (submission_id, analyzer_type, analyzer_version, model_name, data_structures, algorithms,
                     complexity_estimate, code_quality_score, ai_risk_score, ai_risk_level, summary, raw_response,
                     prompt_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setAnalysisParameters(statement, result);
            statement.executeUpdate();

            long generatedId = readGeneratedId(statement);
            return findById(generatedId)
                    .orElseThrow(() -> new DatabaseException("AI analysis result was inserted but could not be reloaded."));
        } catch (SQLException ex) {
            throw databaseException("saving AI analysis result", ex);
        }
    }

    public boolean update(AiAnalysisResult result) {
        String sql = """
                UPDATE dbo.ai_analysis_results
                SET submission_id = ?,
                    analyzer_type = ?,
                    analyzer_version = ?,
                    model_name = ?,
                    data_structures = ?,
                    algorithms = ?,
                    complexity_estimate = ?,
                    code_quality_score = ?,
                    ai_risk_score = ?,
                    ai_risk_level = ?,
                    summary = ?,
                    raw_response = ?,
                    prompt_hash = ?,
                    updated_at = SYSUTCDATETIME()
                WHERE analysis_id = ?
                """;

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setAnalysisParameters(statement, result);
            statement.setLong(14, result.getAnalysisId());
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw databaseException("updating AI analysis result", ex);
        }
    }

    public boolean delete(long analysisId) {
        String clearJobsSql = """
                UPDATE dbo.analysis_jobs
                SET last_analysis_id = NULL,
                    updated_at = SYSUTCDATETIME()
                WHERE last_analysis_id = ?
                """;
        String deleteAnalysisSql = "DELETE FROM dbo.ai_analysis_results WHERE analysis_id = ?";

        try (Connection connection = connectionFactory.createConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement clearJobs = connection.prepareStatement(clearJobsSql);
                 PreparedStatement deleteAnalysis = connection.prepareStatement(deleteAnalysisSql)) {
                clearJobs.setLong(1, analysisId);
                clearJobs.executeUpdate();

                deleteAnalysis.setLong(1, analysisId);
                boolean deleted = deleteAnalysis.executeUpdate() > 0;
                connection.commit();
                return deleted;
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw databaseException("deleting AI analysis result", ex);
        }
    }

    private void setAnalysisParameters(PreparedStatement statement, AiAnalysisResult result) throws SQLException {
        statement.setLong(1, result.getSubmissionId());
        statement.setString(2, result.getAnalyzerType());
        statement.setString(3, result.getAnalyzerVersion());
        statement.setString(4, result.getModelName());
        statement.setString(5, result.getDataStructures());
        statement.setString(6, result.getAlgorithms());
        statement.setString(7, result.getComplexityEstimate());
        statement.setBigDecimal(8, result.getCodeQualityScore());
        statement.setBigDecimal(9, result.getAiRiskScore());
        statement.setString(10, result.getAiRiskLevel());
        statement.setString(11, result.getSummary());
        statement.setString(12, result.getRawResponse());
        statement.setString(13, result.getPromptHash());
    }

    private String selectAnalysisSql() {
        return """
                SELECT analysis_id, submission_id, analyzer_type, analyzer_version, model_name, data_structures,
                       algorithms, complexity_estimate, code_quality_score, ai_risk_score, ai_risk_level,
                       summary, raw_response, prompt_hash, created_at, updated_at
                FROM dbo.ai_analysis_results
                """;
    }

    private String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private int setLongValues(PreparedStatement statement, int startIndex, Set<Long> values) throws SQLException {
        int index = startIndex;
        for (Long value : values) {
            statement.setLong(index++, value);
        }
        return index;
    }

    private AiAnalysisResult mapRow(ResultSet resultSet) throws SQLException {
        return new AiAnalysisResult(
                resultSet.getLong("analysis_id"),
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
                getLocalDateTime(resultSet, "created_at"),
                getLocalDateTime(resultSet, "updated_at")
        );
    }
}
