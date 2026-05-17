package com.example.aicodeanalyzer.report;

import com.example.aicodeanalyzer.exception.ReportException;
import com.example.aicodeanalyzer.model.AiAnalysisResult;
import com.example.aicodeanalyzer.model.HandleAccount;
import com.example.aicodeanalyzer.model.Platform;
import com.example.aicodeanalyzer.model.SkillScore;
import com.example.aicodeanalyzer.model.Submission;
import com.example.aicodeanalyzer.repository.AiAnalysisResultRepository;
import com.example.aicodeanalyzer.repository.HandleAccountRepository;
import com.example.aicodeanalyzer.repository.PlatformRepository;
import com.example.aicodeanalyzer.repository.SubmissionRepository;
import com.example.aicodeanalyzer.service.SkillScoringService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds report-ready datasets from repositories and service-level summaries.
 */
public class ReportDataBuilder {
    private final HandleAccountRepository handleAccountRepository;
    private final PlatformRepository platformRepository;
    private final SubmissionRepository submissionRepository;
    private final AiAnalysisResultRepository aiAnalysisResultRepository;
    private final SkillScoringService skillScoringService;

    public ReportDataBuilder() {
        this(
                new HandleAccountRepository(),
                new PlatformRepository(),
                new SubmissionRepository(),
                new AiAnalysisResultRepository(),
                SkillScoringService.calculationOnly()
        );
    }

    public ReportDataBuilder(
            HandleAccountRepository handleAccountRepository,
            PlatformRepository platformRepository,
            SubmissionRepository submissionRepository,
            AiAnalysisResultRepository aiAnalysisResultRepository,
            SkillScoringService skillScoringService
    ) {
        this.handleAccountRepository = Objects.requireNonNull(
                handleAccountRepository,
                "handleAccountRepository must not be null"
        );
        this.platformRepository = Objects.requireNonNull(platformRepository, "platformRepository must not be null");
        this.submissionRepository = Objects.requireNonNull(submissionRepository, "submissionRepository must not be null");
        this.aiAnalysisResultRepository = Objects.requireNonNull(
                aiAnalysisResultRepository,
                "aiAnalysisResultRepository must not be null"
        );
        this.skillScoringService = Objects.requireNonNull(skillScoringService, "skillScoringService must not be null");
    }

    public EvaluationReportData build(ReportRequest request) {
        validate(request);

        Map<Long, Platform> platformsById = platformRepository.findAll().stream()
                .filter(platform -> platform.getPlatformId() != null)
                .collect(Collectors.toMap(Platform::getPlatformId, Function.identity()));

        Set<Long> selectedHandleIds = new LinkedHashSet<>(request.handleIds());
        List<HandleAccount> selectedHandles = handleAccountRepository.findAll().stream()
                .filter(handle -> selectedHandleIds.isEmpty() || selectedHandleIds.contains(handle.getHandleId()))
                .sorted(Comparator.comparing(HandleAccount::getHandle, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        Set<Long> includedHandleIds = selectedHandles.stream()
                .map(HandleAccount::getHandleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Submission> allSubmissions = submissionRepository.findByHandleIdsAndSubmittedBetween(
                includedHandleIds,
                request.periodStart(),
                request.periodEnd()
        );
        Map<Long, List<Submission>> submissionsByHandleId = allSubmissions.stream()
                .collect(Collectors.groupingBy(
                        Submission::getHandleId,
                        Collectors.collectingAndThen(Collectors.toList(), List::copyOf)
                ));

        Set<Long> submissionIds = allSubmissions.stream()
                .map(Submission::getSubmissionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, List<AiAnalysisResult>> analysesBySubmissionId = aiAnalysisResultRepository
                .findLatestBySubmissionIds(submissionIds)
                .stream()
                .collect(Collectors.groupingBy(
                        AiAnalysisResult::getSubmissionId,
                        Collectors.collectingAndThen(Collectors.toList(), List::copyOf)
                ));

        List<HandleReportRow> rows = selectedHandles.stream()
                .map(handle -> buildRow(handle, platformsById, submissionsByHandleId, analysesBySubmissionId))
                .toList();

        return new EvaluationReportData(
                "Báo cáo kết quả đánh giá nick lập trình",
                request.periodStart(),
                request.periodEnd(),
                LocalDateTime.now(),
                rows
        );
    }

    private HandleReportRow buildRow(
            HandleAccount handle,
            Map<Long, Platform> platformsById,
            Map<Long, List<Submission>> submissionsByHandleId,
            Map<Long, List<AiAnalysisResult>> analysesBySubmissionId
    ) {
        List<Submission> submissions = submissionsByHandleId.getOrDefault(handle.getHandleId(), List.of());
        List<AiAnalysisResult> analyses = submissions.stream()
                .map(Submission::getSubmissionId)
                .filter(Objects::nonNull)
                .map(id -> analysesBySubmissionId.getOrDefault(id, List.of()))
                .flatMap(List::stream)
                .toList();

        SkillScore score = skillScoringService.calculateScore(handle.getHandleId(), analyses, submissions);
        int accepted = (int) submissions.stream().filter(this::isAccepted).count();
        int total = submissions.size();
        Platform platform = platformsById.get(handle.getPlatformId());

        return new HandleReportRow(
                handle.getHandleId(),
                platform == null ? "Unknown" : platform.getName(),
                handle.getHandle(),
                total,
                accepted,
                total == 0 ? 0 : accepted * 100.0 / total,
                analyses.size(),
                sortedTokens(analyses, AiAnalysisResult::getAlgorithms),
                sortedTokens(analyses, AiAnalysisResult::getDataStructures),
                score,
                score.getSummary()
        );
    }

    private void validate(ReportRequest request) {
        if (request == null) {
            throw new ReportException("Report request must not be null.");
        }
        if (request.periodStart() == null || request.periodEnd() == null) {
            throw new ReportException("Report period start and end are required.");
        }
        if (request.periodStart().isAfter(request.periodEnd())) {
            throw new ReportException("Report period start must be before or equal to period end.");
        }
        if (request.handleIds() == null) {
            throw new ReportException("Report handle list must not be null.");
        }
    }

    private boolean isAccepted(Submission submission) {
        return "OK".equalsIgnoreCase(submission.getVerdict());
    }

    private List<String> sortedTokens(
            List<AiAnalysisResult> analyses,
            Function<AiAnalysisResult, String> getter
    ) {
        return analyses.stream()
                .map(getter)
                .filter(Objects::nonNull)
                .flatMap(value -> List.of(value.split("[,;/|]")).stream())
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .filter(value -> !"-".equals(value))
                .filter(value -> !"unknown".equalsIgnoreCase(value))
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
