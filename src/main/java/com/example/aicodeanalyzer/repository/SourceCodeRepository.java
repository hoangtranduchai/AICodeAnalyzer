package com.example.aicodeanalyzer.repository;

import com.example.aicodeanalyzer.config.DatabaseConnectionFactory;
import com.example.aicodeanalyzer.exception.DatabaseException;
import com.example.aicodeanalyzer.model.SourceCode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Provides database operations for source code records and source hash lookup.
 */
public class SourceCodeRepository extends JdbcRepositorySupport {

    public SourceCodeRepository() {
        super();
    }

    public SourceCodeRepository(DatabaseConnectionFactory connectionFactory) {
        super(connectionFactory);
    }

    public Optional<SourceCode> findById(long sourceCodeId) {
        String sql = selectSourceCodeSql() + " WHERE source_code_id = ?";

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, sourceCodeId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw databaseException("finding source code by id", ex);
        }
    }

    public List<SourceCode> findAll() {
        String sql = selectSourceCodeSql() + " ORDER BY source_code_id";

        List<SourceCode> sourceCodes = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                sourceCodes.add(mapRow(resultSet));
            }
            return sourceCodes;
        } catch (SQLException ex) {
            throw databaseException("finding all source codes", ex);
        }
    }

    public Optional<SourceCode> findBySubmissionId(long submissionId) {
        String sql = selectSourceCodeSql() + " WHERE sc.submission_id = ?";

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, submissionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw databaseException("finding source code by submission id", ex);
        }
    }

    public List<SourceCode> findUnanalyzedSourceCodes(int limit) {
        String sql = selectSourceCodeSql() + """
                 WHERE sc.code_content IS NOT NULL
                   AND NOT EXISTS (
                     SELECT 1
                     FROM dbo.ai_analysis_results ar
                     WHERE ar.submission_id = sc.submission_id
                 )
                 ORDER BY fetched_at, source_code_id
                 OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY
                """;

        List<SourceCode> sourceCodes = new ArrayList<>();
        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(limit, 1));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    sourceCodes.add(mapRow(resultSet));
                }
            }
            return sourceCodes;
        } catch (SQLException ex) {
            throw databaseException("finding unanalyzed source codes", ex);
        }
    }

    public SourceCode save(SourceCode sourceCode) {
        String sql = """
                INSERT INTO dbo.source_codes
                    (submission_id, code_content, code_hash, line_count, char_count, fetched_at, storage_type, is_encrypted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setSourceCodeParameters(statement, sourceCode);
            statement.executeUpdate();

            long generatedId = readGeneratedId(statement);
            return findById(generatedId)
                    .orElseThrow(() -> new DatabaseException("Source code was inserted but could not be reloaded."));
        } catch (SQLException ex) {
            throw databaseException("saving source code", ex);
        }
    }

    public boolean update(SourceCode sourceCode) {
        String sql = """
                UPDATE dbo.source_codes
                SET submission_id = ?,
                    code_content = ?,
                    code_hash = ?,
                    line_count = ?,
                    char_count = ?,
                    fetched_at = ?,
                    storage_type = ?,
                    is_encrypted = ?,
                    updated_at = SYSUTCDATETIME()
                WHERE source_code_id = ?
                """;

        try (Connection connection = connectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setSourceCodeParameters(statement, sourceCode);
            statement.setLong(9, sourceCode.getSourceCodeId());
            return statement.executeUpdate() > 0;
        } catch (SQLException ex) {
            throw databaseException("updating source code", ex);
        }
    }

    public boolean delete(long sourceCodeId) {
        String deleteJobsSql = "DELETE FROM dbo.analysis_jobs WHERE source_code_id = ?";
        String deleteSourceSql = "DELETE FROM dbo.source_codes WHERE source_code_id = ?";

        try (Connection connection = connectionFactory.createConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement deleteJobs = connection.prepareStatement(deleteJobsSql);
                 PreparedStatement deleteSource = connection.prepareStatement(deleteSourceSql)) {
                deleteJobs.setLong(1, sourceCodeId);
                deleteJobs.executeUpdate();

                deleteSource.setLong(1, sourceCodeId);
                boolean deleted = deleteSource.executeUpdate() > 0;
                connection.commit();
                return deleted;
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw databaseException("deleting source code", ex);
        }
    }

    private void setSourceCodeParameters(PreparedStatement statement, SourceCode sourceCode) throws SQLException {
        statement.setLong(1, sourceCode.getSubmissionId());
        statement.setString(2, sourceCode.getCodeContent());
        statement.setString(3, sourceCode.getCodeHash());
        statement.setObject(4, sourceCode.getLineCount());
        statement.setObject(5, sourceCode.getCharCount());
        setLocalDateTime(statement, 6, sourceCode.getFetchedAt());
        statement.setString(7, sourceCode.getStorageType());
        statement.setBoolean(8, sourceCode.isEncrypted());
    }

    private String selectSourceCodeSql() {
        return """
                SELECT sc.source_code_id, sc.submission_id, sc.code_content, sc.code_hash, sc.line_count,
                       sc.char_count, sc.fetched_at, sc.storage_type, sc.is_encrypted, sc.created_at, sc.updated_at
                FROM dbo.source_codes sc
                """;
    }

    private SourceCode mapRow(ResultSet resultSet) throws SQLException {
        return new SourceCode(
                resultSet.getLong("source_code_id"),
                resultSet.getLong("submission_id"),
                resultSet.getString("code_content"),
                resultSet.getString("code_hash"),
                (Integer) resultSet.getObject("line_count"),
                (Integer) resultSet.getObject("char_count"),
                getLocalDateTime(resultSet, "fetched_at"),
                resultSet.getString("storage_type"),
                resultSet.getBoolean("is_encrypted"),
                getLocalDateTime(resultSet, "created_at"),
                getLocalDateTime(resultSet, "updated_at")
        );
    }
}
