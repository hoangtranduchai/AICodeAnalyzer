package com.example.aicodeanalyzer.repository;

import com.example.aicodeanalyzer.config.DatabaseConnectionFactory;
import com.example.aicodeanalyzer.exception.DatabaseException;
import com.example.aicodeanalyzer.model.SkillScore;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Provides database operations for aggregated skill score records.
 */
public class SkillScoreRepository extends JdbcRepositorySupport {

    public SkillScoreRepository() {
        super();
    }

    public SkillScoreRepository(DatabaseConnectionFactory connectionFactory) {
        super(connectionFactory);
    }

    public Optional<SkillScore> findById(long scoreId) {
        String sql = selectScoreSql() + " WHERE score_id = ?";

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, scoreId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw databaseException("finding skill score by id", ex);
        }
    }

    public List<SkillScore> findAll() {
        String sql = selectScoreSql() + " ORDER BY period_end DESC, overall_score DESC";

        List<SkillScore> scores = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                scores.add(mapRow(resultSet));
            }
            return scores;
        } catch (SQLException ex) {
            throw databaseException("finding all skill scores", ex);
        }
    }

    public List<SkillScore> findRecent(int limit) {
        String sql = selectScoreSql() + """
                 ORDER BY period_end DESC, overall_score DESC
                 OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY
                """;

        List<SkillScore> scores = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    scores.add(mapRow(resultSet));
                }
            }
            return scores;
        } catch (SQLException ex) {
            throw databaseException("finding recent skill scores", ex);
        }
    }

    public List<SkillScore> findOverlappingPeriodsByHandleIds(
            Set<Long> handleIds,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
        if (handleIds == null || handleIds.isEmpty()) {
            return List.of();
        }

        String sql = selectScoreSql()
                + " WHERE handle_id IN (" + placeholders(handleIds.size()) + ")"
                + " AND period_end >= ? AND period_start <= ?"
                + " ORDER BY period_end DESC, overall_score DESC";

        List<SkillScore> scores = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = setLongValues(statement, 1, handleIds);
            setLocalDate(statement, index++, periodStart);
            setLocalDate(statement, index, periodEnd);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    scores.add(mapRow(resultSet));
                }
            }
            return scores;
        } catch (SQLException ex) {
            throw databaseException("finding skill scores by handles and period", ex);
        }
    }

    public Optional<SkillScore> findScoreByHandle(long handleId) {
        String sql = selectScoreSql() + """
                 WHERE handle_id = ?
                 ORDER BY period_end DESC, generated_at DESC, score_id DESC
                 OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY
                """;

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, handleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw databaseException("finding latest skill score by handle", ex);
        }
    }

    public Optional<SkillScore> findByHandleAndPeriod(long handleId, java.time.LocalDate periodStart, java.time.LocalDate periodEnd) {
        String sql = selectScoreSql() + " WHERE handle_id = ? AND period_start = ? AND period_end = ?";

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, handleId);
            setLocalDate(statement, 2, periodStart);
            setLocalDate(statement, 3, periodEnd);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw databaseException("finding skill score by handle and period", ex);
        }
    }

    public SkillScore saveOrUpdateByHandleAndPeriod(SkillScore score) {
        Optional<SkillScore> existing = findByHandleAndPeriod(
                score.getHandleId(),
                score.getPeriodStart(),
                score.getPeriodEnd()
        );
        if (existing.isEmpty()) {
            return save(score);
        }

        score.setScoreId(existing.get().getScoreId());
        update(score);
        return findById(score.getScoreId())
                .orElseThrow(() -> new DatabaseException("Skill score was updated but could not be reloaded."));
    }

    public SkillScore save(SkillScore score) {
        String sql = """
                INSERT INTO dbo.user_skill_scores
                    (handle_id, period_start, period_end, data_structure_score, algorithm_score, problem_solving_score,
                     code_quality_score, practice_consistency_score, ai_usage_risk_score, overall_score, summary,
                     generated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, SYSUTCDATETIME()))
                """;

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setScoreParameters(statement, score);
            statement.executeUpdate();

            long generatedId = readGeneratedId(statement);
            return findById(generatedId)
                    .orElseThrow(() -> new DatabaseException("Skill score was inserted but could not be reloaded."));
        } catch (SQLException ex) {
            throw databaseException("saving skill score", ex);
        }
    }

    public boolean update(SkillScore score) {
        String sql = """
                UPDATE dbo.user_skill_scores
                SET handle_id = ?,
                    period_start = ?,
                    period_end = ?,
                    data_structure_score = ?,
                    algorithm_score = ?,
                    problem_solving_score = ?,
                    code_quality_score = ?,
                    practice_consistency_score = ?,
                    ai_usage_risk_score = ?,
                    overall_score = ?,
                    summary = ?,
                    generated_at = COALESCE(?, generated_at),
                    updated_at = SYSUTCDATETIME()
                WHERE score_id = ?
                """;

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setScoreParameters(statement, score);
            statement.setLong(13, score.getScoreId());
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw databaseException("updating skill score", ex);
        }
    }

    public boolean delete(long scoreId) {
        String sql = "DELETE FROM dbo.user_skill_scores WHERE score_id = ?";

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, scoreId);
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw databaseException("deleting skill score", ex);
        }
    }

    private void setScoreParameters(PreparedStatement statement, SkillScore score) throws SQLException {
        statement.setLong(1, score.getHandleId());
        setLocalDate(statement, 2, score.getPeriodStart());
        setLocalDate(statement, 3, score.getPeriodEnd());
        statement.setBigDecimal(4, score.getDataStructureScore());
        statement.setBigDecimal(5, score.getAlgorithmScore());
        statement.setBigDecimal(6, score.getProblemSolvingScore());
        statement.setBigDecimal(7, score.getCodeQualityScore());
        statement.setBigDecimal(8, score.getPracticeConsistencyScore());
        statement.setBigDecimal(9, score.getAiUsageRiskScore());
        statement.setBigDecimal(10, score.getOverallScore());
        statement.setString(11, score.getSummary());
        setLocalDateTime(statement, 12, score.getGeneratedAt());
    }

    private String selectScoreSql() {
        return """
                SELECT score_id, handle_id, period_start, period_end, data_structure_score, algorithm_score,
                       problem_solving_score, code_quality_score, practice_consistency_score, ai_usage_risk_score,
                       overall_score, summary, generated_at, created_at, updated_at
                FROM dbo.user_skill_scores
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

    private SkillScore mapRow(ResultSet resultSet) throws SQLException {
        return new SkillScore(
                resultSet.getLong("score_id"),
                resultSet.getLong("handle_id"),
                getLocalDate(resultSet, "period_start"),
                getLocalDate(resultSet, "period_end"),
                resultSet.getBigDecimal("data_structure_score"),
                resultSet.getBigDecimal("algorithm_score"),
                resultSet.getBigDecimal("problem_solving_score"),
                resultSet.getBigDecimal("code_quality_score"),
                resultSet.getBigDecimal("practice_consistency_score"),
                resultSet.getBigDecimal("ai_usage_risk_score"),
                resultSet.getBigDecimal("overall_score"),
                resultSet.getString("summary"),
                getLocalDateTime(resultSet, "generated_at"),
                getLocalDateTime(resultSet, "created_at"),
                getLocalDateTime(resultSet, "updated_at")
        );
    }
}
