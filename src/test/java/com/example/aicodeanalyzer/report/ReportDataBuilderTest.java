package com.example.aicodeanalyzer.report;

import com.example.aicodeanalyzer.config.DatabaseConfig;
import com.example.aicodeanalyzer.config.DatabaseConnectionFactory;
import com.example.aicodeanalyzer.model.AiAnalysisResult;
import com.example.aicodeanalyzer.model.HandleAccount;
import com.example.aicodeanalyzer.model.Platform;
import com.example.aicodeanalyzer.model.Submission;
import com.example.aicodeanalyzer.repository.AiAnalysisResultRepository;
import com.example.aicodeanalyzer.repository.HandleAccountRepository;
import com.example.aicodeanalyzer.repository.PlatformRepository;
import com.example.aicodeanalyzer.repository.SubmissionRepository;
import com.example.aicodeanalyzer.service.SkillScoringService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportDataBuilderTest {
    private static final DatabaseConnectionFactory UNUSED_FACTORY = new DatabaseConnectionFactory(
            new DatabaseConfig("jdbc:h2:mem:report_data_builder_fake", "sa", "", 5)
    );

    @Test
    void buildLoadsSubmissionsAndAnalysesInBatch() {
        FakeSubmissionRepository submissionRepository = new FakeSubmissionRepository();
        FakeAnalysisRepository analysisRepository = new FakeAnalysisRepository();
        ReportDataBuilder builder = new ReportDataBuilder(
                new FakeHandleRepository(),
                new FakePlatformRepository(),
                submissionRepository,
                analysisRepository,
                SkillScoringService.calculationOnly()
        );

        EvaluationReportData data = builder.build(new ReportRequest(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                List.of(),
                false
        ));

        assertEquals(2, data.handles().size());
        assertEquals(1, submissionRepository.queryCount);
        assertEquals(1, analysisRepository.queryCount);
        assertEquals(1, data.handles().get(0).totalSubmissions());
        assertEquals(1, data.handles().get(1).totalSubmissions());
    }

    private static class FakePlatformRepository extends PlatformRepository {
        private FakePlatformRepository() {
            super(UNUSED_FACTORY);
        }

        @Override
        public List<Platform> findAll() {
            return List.of(new Platform(1L, "CODEFORCES", "Codeforces", null, null, true, null, null));
        }
    }

    private static class FakeHandleRepository extends HandleAccountRepository {
        private FakeHandleRepository() {
            super(UNUSED_FACTORY);
        }

        @Override
        public List<HandleAccount> findAll() {
            return List.of(
                    handle(10L, "alice"),
                    handle(20L, "bob")
            );
        }

        private static HandleAccount handle(long handleId, String handle) {
            return new HandleAccount(
                    handleId,
                    1L,
                    handle,
                    handle,
                    "demo",
                    "PUBLIC",
                    true,
                    null,
                    "",
                    null,
                    null
            );
        }
    }

    private static class FakeSubmissionRepository extends SubmissionRepository {
        private int queryCount;

        private FakeSubmissionRepository() {
            super(UNUSED_FACTORY);
        }

        @Override
        public List<Submission> findByHandleIdsAndSubmittedBetween(
                Set<Long> handleIds,
                LocalDate periodStart,
                LocalDate periodEnd
        ) {
            queryCount++;
            assertEquals(Set.of(10L, 20L), handleIds);
            return List.of(
                    submission(100L, 10L, "100"),
                    submission(200L, 20L, "200")
            );
        }

        private static Submission submission(long submissionId, long handleId, String remoteId) {
            return new Submission(
                    submissionId,
                    handleId,
                    remoteId,
                    "A",
                    "Problem",
                    "1",
                    "Java",
                    "OK",
                    LocalDateTime.of(2026, 5, 10, 10, 0),
                    10,
                    100L,
                    800,
                    "implementation",
                    null,
                    null,
                    null
            );
        }
    }

    private static class FakeAnalysisRepository extends AiAnalysisResultRepository {
        private int queryCount;

        private FakeAnalysisRepository() {
            super(UNUSED_FACTORY);
        }

        @Override
        public List<AiAnalysisResult> findLatestBySubmissionIds(Set<Long> submissionIds) {
            queryCount++;
            assertEquals(Set.of(100L, 200L), submissionIds);
            return List.of(
                    analysis(100L),
                    analysis(200L)
            );
        }

        private static AiAnalysisResult analysis(long submissionId) {
            return new AiAnalysisResult(
                    submissionId,
                    submissionId,
                    "RULE_BASED",
                    "1.0",
                    "local",
                    "array",
                    "sorting",
                    "O(n)",
                    BigDecimal.valueOf(80),
                    BigDecimal.valueOf(10),
                    "LOW",
                    "ok",
                    "{}",
                    "hash-" + submissionId,
                    LocalDateTime.of(2026, 5, 10, 11, 0),
                    null
            );
        }
    }
}
