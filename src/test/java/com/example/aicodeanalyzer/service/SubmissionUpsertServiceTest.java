package com.example.aicodeanalyzer.service;

import com.example.aicodeanalyzer.config.DatabaseConnectionFactory;
import com.example.aicodeanalyzer.crawler.CrawledSubmission;
import com.example.aicodeanalyzer.crawler.SourceAvailability;
import com.example.aicodeanalyzer.model.HandleAccount;
import com.example.aicodeanalyzer.model.Platform;
import com.example.aicodeanalyzer.model.SourceCode;
import com.example.aicodeanalyzer.model.Submission;
import com.example.aicodeanalyzer.repository.HandleAccountRepository;
import com.example.aicodeanalyzer.repository.PlatformRepository;
import com.example.aicodeanalyzer.repository.RepositoryTestSupport;
import com.example.aicodeanalyzer.repository.SourceCodeRepository;
import com.example.aicodeanalyzer.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.example.aicodeanalyzer.service.SubmissionUpsertService.UpsertAction.CREATED;
import static com.example.aicodeanalyzer.service.SubmissionUpsertService.UpsertAction.SKIPPED;
import static com.example.aicodeanalyzer.service.SubmissionUpsertService.UpsertAction.UPDATED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubmissionUpsertServiceTest {

    @Test
    void upsertCreatesSubmissionAndSourceWithComputedHashAndCounts() {
        TestContext context = createContext();
        CrawledSubmission submission = crawledSubmission("1001", "OK", "int main() {\n  return 0;\n}");

        assertEquals(CREATED, context.service.upsert(context.handle, "codeforces", submission));

        Submission savedSubmission = context.submissionRepository
                .findByPlatformAndRemoteSubmissionId("CODEFORCES", "1001")
                .orElseThrow();
        SourceCode sourceCode = context.sourceCodeRepository.findBySubmissionId(savedSubmission.getSubmissionId()).orElseThrow();
        assertEquals("DATABASE", sourceCode.getStorageType());
        assertEquals(3, sourceCode.getLineCount());
        assertEquals(submission.sourceCode().length(), sourceCode.getCharCount());
        assertNotNull(sourceCode.getCodeHash());
    }

    @Test
    void upsertSkipsWhenMetadataAndSourceAreUnchanged() {
        TestContext context = createContext();
        CrawledSubmission submission = crawledSubmission("1002", "OK", "int main(){return 0;}");

        assertEquals(CREATED, context.service.upsert(context.handle, "CODEFORCES", submission));
        assertEquals(SKIPPED, context.service.upsert(context.handle, "CODEFORCES", submission));
        assertEquals(1, context.submissionRepository.findAll().size());
    }

    @Test
    void upsertUpdatesMetadataAndSourceWhenIncomingDataChanges() {
        TestContext context = createContext();
        context.service.upsert(context.handle, "CODEFORCES", crawledSubmission("1003", "WRONG_ANSWER", "int main(){return 1;}"));

        assertEquals(UPDATED, context.service.upsert(context.handle, "CODEFORCES",
                crawledSubmission("1003", "OK", "int main(){return 0;}")));

        Submission updated = context.submissionRepository.findByPlatformAndRemoteSubmissionId("CODEFORCES", "1003")
                .orElseThrow();
        SourceCode sourceCode = context.sourceCodeRepository.findBySubmissionId(updated.getSubmissionId()).orElseThrow();
        assertEquals("OK", updated.getVerdict());
        assertEquals("int main(){return 0;}", sourceCode.getCodeContent());
    }

    @Test
    void upsertDoesNotOverwriteExistingSourceWithUnavailableSourcePlaceholder() {
        TestContext context = createContext();
        context.service.upsert(context.handle, "CODEFORCES", crawledSubmission("1004", "OK", "int main(){return 0;}"));

        assertEquals(SKIPPED, context.service.upsert(context.handle, "CODEFORCES", new CrawledSubmission(
                "1004",
                "1703A",
                "YES or YES?",
                "1703",
                "GNU C++17",
                "OK",
                LocalDateTime.of(2026, 5, 12, 10, 0),
                46,
                102400L,
                800,
                "implementation",
                "https://codeforces.com/contest/1703/submission/1004",
                SourceAvailability.SOURCE_NOT_AVAILABLE,
                null,
                "public API does not expose source"
        )));

        Submission saved = context.submissionRepository.findByPlatformAndRemoteSubmissionId("CODEFORCES", "1004")
                .orElseThrow();
        SourceCode sourceCode = context.sourceCodeRepository.findBySubmissionId(saved.getSubmissionId()).orElseThrow();
        assertEquals("int main(){return 0;}", sourceCode.getCodeContent());
        assertEquals("DATABASE", sourceCode.getStorageType());
    }

    @Test
    void upsertValidatesRequiredHandleAndRemoteSubmissionId() {
        TestContext context = createContext();
        HandleAccount missingId = new HandleAccount();
        missingId.setHandle("missing_id");

        assertThrows(IllegalArgumentException.class,
                () -> context.service.upsert(missingId, "CODEFORCES", crawledSubmission("1005", "OK", "code")));
        assertThrows(IllegalArgumentException.class,
                () -> context.service.upsert(context.handle, " ", crawledSubmission("1005", "OK", "code")));
        assertThrows(IllegalArgumentException.class,
                () -> new CrawledSubmission(" ", null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null));
    }

    private TestContext createContext() {
        DatabaseConnectionFactory factory = RepositoryTestSupport.createFactoryWithSchema();
        Platform platform = RepositoryTestSupport.seedPlatform(factory);
        HandleAccount handle = RepositoryTestSupport.seedHandle(factory, platform.getPlatformId(), "tourist");
        SubmissionRepository submissionRepository = new SubmissionRepository(factory);
        SourceCodeRepository sourceCodeRepository = new SourceCodeRepository(factory);
        return new TestContext(
                handle,
                submissionRepository,
                sourceCodeRepository,
                new SubmissionUpsertService(submissionRepository, sourceCodeRepository)
        );
    }

    private CrawledSubmission crawledSubmission(String remoteId, String verdict, String sourceCode) {
        return new CrawledSubmission(
                remoteId,
                "1703A",
                "YES or YES?",
                "1703",
                "GNU C++17",
                verdict,
                LocalDateTime.of(2026, 5, 12, 10, 0),
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

    private record TestContext(
            HandleAccount handle,
            SubmissionRepository submissionRepository,
            SourceCodeRepository sourceCodeRepository,
            SubmissionUpsertService service
    ) {
    }
}
