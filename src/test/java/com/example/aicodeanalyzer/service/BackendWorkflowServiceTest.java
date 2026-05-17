package com.example.aicodeanalyzer.service;

import com.example.aicodeanalyzer.analyzer.CodeAnalyzer;
import com.example.aicodeanalyzer.config.DatabaseConnectionFactory;
import com.example.aicodeanalyzer.exception.AiRateLimitException;
import com.example.aicodeanalyzer.crawler.CrawlRequest;
import com.example.aicodeanalyzer.crawler.CrawlResult;
import com.example.aicodeanalyzer.crawler.CrawledSubmission;
import com.example.aicodeanalyzer.crawler.OnlineJudgeCrawler;
import com.example.aicodeanalyzer.crawler.SourceAvailability;
import com.example.aicodeanalyzer.model.AiAnalysisResult;
import com.example.aicodeanalyzer.model.HandleAccount;
import com.example.aicodeanalyzer.model.Platform;
import com.example.aicodeanalyzer.model.SourceCodeDetail;
import com.example.aicodeanalyzer.repository.AiAnalysisResultRepository;
import com.example.aicodeanalyzer.repository.CrawlLogRepository;
import com.example.aicodeanalyzer.repository.HandleAccountRepository;
import com.example.aicodeanalyzer.repository.PlatformRepository;
import com.example.aicodeanalyzer.repository.RepositoryTestSupport;
import com.example.aicodeanalyzer.repository.SkillScoreRepository;
import com.example.aicodeanalyzer.repository.SourceCodeDetailRepository;
import com.example.aicodeanalyzer.repository.SourceCodeRepository;
import com.example.aicodeanalyzer.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackendWorkflowServiceTest {

    @Test
    void runOnceCrawlsNewSubmissionStoresSourceAnalyzesItAndSkipsDuplicateNextRun() {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = RepositoryTestSupport.seedPlatform(factory);
        HandleAccount handle = RepositoryTestSupport.seedHandle(factory, platform.getPlatformId(), "tourist");

        RecordingCrawler crawler = new RecordingCrawler();
        SubmissionRepository submissionRepository = new SubmissionRepository(factory);
        SourceCodeRepository sourceCodeRepository = new SourceCodeRepository(factory);
        AiAnalysisResultRepository analysisRepository = new AiAnalysisResultRepository(factory);
        SkillScoreRepository skillScoreRepository = new SkillScoreRepository(factory);

        CrawlService crawlService = new CrawlService(
                new PlatformRepository(factory),
                new HandleAccountRepository(factory),
                submissionRepository,
                sourceCodeRepository,
                new CrawlLogRepository(factory),
                List.of(crawler)
        );
        AnalysisService analysisService = new AnalysisService(
                new SourceCodeDetailRepository(factory),
                analysisRepository,
                submissionRepository,
                this::deterministicAnalysis,
                new SkillScoringService(analysisRepository, submissionRepository, skillScoreRepository)
        );
        BackendWorkflowService workflowService = new BackendWorkflowService(
                crawlService,
                sourceCodeRepository,
                analysisService
        );

        BackendWorkflowService.BackendWorkflowResult firstRun = workflowService.runOnce("SCHEDULED", 10, 10);

        assertEquals(1, crawler.callCount);
        assertEquals(1, firstRun.crawlLog().getTotalNewSubmissions());
        assertEquals(1, firstRun.analysisQueueResult().pendingCount());
        assertEquals(1, firstRun.analysisQueueResult().analyzedCount());
        assertEquals(0, firstRun.analysisQueueResult().failedCount());
        assertEquals(1, submissionRepository.findAll().size());
        assertEquals(1, sourceCodeRepository.findAll().size());
        assertEquals(1, analysisRepository.findAll().size());
        assertEquals(1, skillScoreRepository.findAll().size());
        assertEquals(handle.getHandleId(), skillScoreRepository.findAll().get(0).getHandleId());

        BackendWorkflowService.BackendWorkflowResult secondRun = workflowService.runOnce("SCHEDULED", 10, 10);

        assertEquals(2, crawler.callCount);
        assertEquals(0, secondRun.crawlLog().getTotalNewSubmissions());
        assertEquals(0, secondRun.analysisQueueResult().pendingCount());
        assertEquals(0, secondRun.analysisQueueResult().analyzedCount());
        assertEquals(1, submissionRepository.findAll().size());
        assertEquals(1, analysisRepository.findAll().size());
        assertEquals(1, skillScoreRepository.findAll().size());
    }

    @Test
    void runOnceFallsBackToRuleBasedAnalysisWhenAiProviderRateLimitIsReached() {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = RepositoryTestSupport.seedPlatform(factory);
        RepositoryTestSupport.seedHandle(factory, platform.getPlatformId(), "tourist");

        TwoSubmissionCrawler crawler = new TwoSubmissionCrawler();
        SubmissionRepository submissionRepository = new SubmissionRepository(factory);
        SourceCodeRepository sourceCodeRepository = new SourceCodeRepository(factory);
        AiAnalysisResultRepository analysisRepository = new AiAnalysisResultRepository(factory);
        SkillScoreRepository skillScoreRepository = new SkillScoreRepository(factory);
        RateLimitedAnalyzer analyzer = new RateLimitedAnalyzer();

        CrawlService crawlService = new CrawlService(
                new PlatformRepository(factory),
                new HandleAccountRepository(factory),
                submissionRepository,
                sourceCodeRepository,
                new CrawlLogRepository(factory),
                List.of(crawler)
        );
        AnalysisService analysisService = new AnalysisService(
                new SourceCodeDetailRepository(factory),
                analysisRepository,
                submissionRepository,
                analyzer,
                new SkillScoringService(analysisRepository, submissionRepository, skillScoreRepository)
        );
        BackendWorkflowService workflowService = new BackendWorkflowService(
                crawlService,
                sourceCodeRepository,
                analysisService
        );

        BackendWorkflowService.BackendWorkflowResult result = workflowService.runOnce("SCHEDULED", 10, 10);

        assertEquals(2, result.crawlLog().getTotalNewSubmissions());
        assertEquals(2, result.analysisQueueResult().pendingCount());
        assertEquals(2, result.analysisQueueResult().analyzedCount());
        assertEquals(0, result.analysisQueueResult().failedCount());
        assertEquals(2, analyzer.callCount);
        assertEquals(2, analysisRepository.findAll().size());
    }

    private AiAnalysisResult deterministicAnalysis(SourceCodeDetail sourceCodeDetail) {
        return new AiAnalysisResult(
                null,
                sourceCodeDetail.submissionId(),
                "TEST_ANALYZER",
                "1.0",
                "deterministic",
                "vector",
                "implementation",
                "unknown",
                BigDecimal.valueOf(80),
                BigDecimal.valueOf(20),
                "LOW",
                "Deterministic test analysis.",
                "{\"data_structures\":[\"vector\"],\"algorithms\":[\"implementation\"],\"ai_generated_probability\":20}",
                "stage4-test",
                null,
                null
        );
    }

    private static final class RecordingCrawler implements OnlineJudgeCrawler {
        private int callCount;

        @Override
        public String platformCode() {
            return "CODEFORCES";
        }

        @Override
        public CrawlResult crawl(CrawlRequest request) {
            callCount++;
            if (request.isKnownSubmission("stage4-1001")) {
                return new CrawlResult(
                        platformCode(),
                        request.handle(),
                        List.of(),
                        List.of("No new submissions in deterministic test."),
                        LocalDateTime.now()
                );
            }
            return new CrawlResult(
                    platformCode(),
                    request.handle(),
                    List.of(new CrawledSubmission(
                            "stage4-1001",
                            "1703A",
                            "YES or YES?",
                            "1703",
                            "GNU C++17",
                            "OK",
                            LocalDateTime.of(2026, 5, 16, 12, 0),
                            46,
                            102400L,
                            800,
                            "implementation",
                            "https://codeforces.com/contest/1703/submission/stage4-1001",
                            SourceAvailability.AVAILABLE,
                            "#include <bits/stdc++.h>\nint main(){return 0;}",
                            null
                    )),
                    List.of(),
                    LocalDateTime.now()
            );
        }
    }

    private static final class TwoSubmissionCrawler implements OnlineJudgeCrawler {
        @Override
        public String platformCode() {
            return "CODEFORCES";
        }

        @Override
        public CrawlResult crawl(CrawlRequest request) {
            return new CrawlResult(
                    platformCode(),
                    request.handle(),
                    List.of(
                            submission("stage4-2001", "int main(){return 0;}"),
                            submission("stage4-2002", "int main(){return 1;}")
                    ),
                    List.of(),
                    LocalDateTime.now()
            );
        }

        private CrawledSubmission submission(String remoteId, String sourceCode) {
            return new CrawledSubmission(
                    remoteId,
                    "1703A",
                    "YES or YES?",
                    "1703",
                    "GNU C++17",
                    "OK",
                    LocalDateTime.of(2026, 5, 16, 12, 0),
                    46,
                    102400L,
                    800,
                    "implementation",
                    "https://codeforces.com/contest/1703/submission/" + remoteId,
                    SourceAvailability.AVAILABLE,
                    sourceCode,
                    null
            );
        }
    }

    private static final class RateLimitedAnalyzer implements CodeAnalyzer {
        private int callCount;

        @Override
        public AiAnalysisResult analyze(SourceCodeDetail sourceCodeDetail) {
            callCount++;
            throw new AiRateLimitException("Test quota exhausted.", 429, Duration.ofSeconds(25));
        }
    }

}
