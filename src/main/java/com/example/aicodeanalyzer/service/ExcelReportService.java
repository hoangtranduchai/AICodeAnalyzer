package com.example.aicodeanalyzer.service;

import com.example.aicodeanalyzer.exception.ReportException;
import com.example.aicodeanalyzer.model.AiAnalysisResult;
import com.example.aicodeanalyzer.model.HandleAccount;
import com.example.aicodeanalyzer.model.Platform;
import com.example.aicodeanalyzer.model.SkillScore;
import com.example.aicodeanalyzer.model.Submission;
import com.example.aicodeanalyzer.report.ExcelReportData;
import com.example.aicodeanalyzer.report.ReportExportResult;
import com.example.aicodeanalyzer.report.ReportRequest;
import com.example.aicodeanalyzer.repository.AiAnalysisResultRepository;
import com.example.aicodeanalyzer.repository.HandleAccountRepository;
import com.example.aicodeanalyzer.repository.PlatformRepository;
import com.example.aicodeanalyzer.repository.SkillScoreRepository;
import com.example.aicodeanalyzer.repository.SubmissionRepository;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Exports the evaluation report workbook using Apache POI.
 */
public class ExcelReportService {
    private static final Path DEFAULT_REPORT_DIRECTORY = Path.of("reports");
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PlatformRepository platformRepository;
    private final HandleAccountRepository handleAccountRepository;
    private final SubmissionRepository submissionRepository;
    private final AiAnalysisResultRepository aiAnalysisResultRepository;
    private final SkillScoreRepository skillScoreRepository;

    public ExcelReportService() {
        this(
                new PlatformRepository(),
                new HandleAccountRepository(),
                new SubmissionRepository(),
                new AiAnalysisResultRepository(),
                new SkillScoreRepository()
        );
    }

    public ExcelReportService(
            PlatformRepository platformRepository,
            HandleAccountRepository handleAccountRepository,
            SubmissionRepository submissionRepository,
            AiAnalysisResultRepository aiAnalysisResultRepository,
            SkillScoreRepository skillScoreRepository
    ) {
        this.platformRepository = Objects.requireNonNull(platformRepository, "platformRepository must not be null");
        this.handleAccountRepository = Objects.requireNonNull(
                handleAccountRepository,
                "handleAccountRepository must not be null"
        );
        this.submissionRepository = Objects.requireNonNull(submissionRepository, "submissionRepository must not be null");
        this.aiAnalysisResultRepository = Objects.requireNonNull(
                aiAnalysisResultRepository,
                "aiAnalysisResultRepository must not be null"
        );
        this.skillScoreRepository = Objects.requireNonNull(skillScoreRepository, "skillScoreRepository must not be null");
    }

    public ExcelReportService(boolean workbookOnly) {
        if (!workbookOnly) {
            throw new IllegalArgumentException("Use the default constructor for repository-backed export.");
        }
        this.platformRepository = null;
        this.handleAccountRepository = null;
        this.submissionRepository = null;
        this.aiAnalysisResultRepository = null;
        this.skillScoreRepository = null;
    }

    public ReportExportResult exportExcel(LocalDate periodStart, LocalDate periodEnd, List<Long> handleIds) {
        return exportExcel(new ReportRequest(periodStart, periodEnd, handleIds, false));
    }

    public ReportExportResult exportExcel(ReportRequest request) {
        return exportExcel(request, DEFAULT_REPORT_DIRECTORY);
    }

    public ReportExportResult exportExcel(ReportRequest request, Path reportDirectory) {
        if (platformRepository == null
                || handleAccountRepository == null
                || submissionRepository == null
                || aiAnalysisResultRepository == null
                || skillScoreRepository == null) {
            throw new ReportException("This ExcelReportService instance cannot load data from repositories.");
        }

        ExcelReportData data = buildData(request);
        return exportExcel(data, reportDirectory, request.openAfterExport());
    }

    public ReportExportResult exportExcel(ExcelReportData data, Path reportDirectory, boolean openAfterExport) {
        validate(data, reportDirectory);

        try {
            Files.createDirectories(reportDirectory);
        } catch (IOException ex) {
            throw new ReportException("Cannot create reports directory: " + reportDirectory.toAbsolutePath(), ex);
        }

        Path outputFile = reportDirectory.resolve(defaultFileName(data)).toAbsolutePath().normalize();
        writeWorkbook(data, outputFile);
        boolean opened = openAfterExport && openFile(outputFile);
        return new ReportExportResult(outputFile, opened, message(outputFile, openAfterExport, opened));
    }

    private ExcelReportData buildData(ReportRequest request) {
        validateRequest(request);

        Set<Long> selectedHandleIds = new LinkedHashSet<>(request.handleIds());
        List<Platform> platforms = platformRepository.findAll();
        List<HandleAccount> handles = handleAccountRepository.findAll().stream()
                .filter(handle -> selectedHandleIds.isEmpty() || selectedHandleIds.contains(handle.getHandleId()))
                .toList();
        Set<Long> includedHandleIds = handles.stream()
                .map(HandleAccount::getHandleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Submission> submissions = submissionRepository.findByHandleIdsAndSubmittedBetween(
                includedHandleIds,
                request.periodStart(),
                request.periodEnd()
        );
        Set<Long> includedSubmissionIds = submissions.stream()
                .map(Submission::getSubmissionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<AiAnalysisResult> analyses = aiAnalysisResultRepository.findBySubmissionIds(includedSubmissionIds);
        List<SkillScore> skillScores = skillScoreRepository.findOverlappingPeriodsByHandleIds(
                includedHandleIds,
                request.periodStart(),
                request.periodEnd()
        );

        return new ExcelReportData(
                request.periodStart(),
                request.periodEnd(),
                LocalDateTime.now(),
                platforms,
                handles,
                submissions,
                analyses,
                skillScores
        );
    }

    private void writeWorkbook(ExcelReportData data, Path outputFile) {
        try (Workbook workbook = new XSSFWorkbook();
             OutputStream outputStream = Files.newOutputStream(outputFile)) {
            WorkbookStyles styles = WorkbookStyles.create(workbook);
            Map<Long, Platform> platformsById = mapById(data.platforms(), Platform::getPlatformId);
            Map<Long, HandleAccount> handlesById = mapById(data.handles(), HandleAccount::getHandleId);
            Map<Long, Submission> submissionsById = mapById(data.submissions(), Submission::getSubmissionId);

            writeOverviewSheet(workbook, styles, data);
            writeHandlesSheet(workbook, styles, data.handles(), platformsById);
            writeSubmissionsSheet(workbook, styles, data.submissions(), handlesById, platformsById);
            writeAnalysisSheet(workbook, styles, data.analyses(), submissionsById, handlesById, platformsById);
            writeSkillScoresSheet(workbook, styles, data.skillScores(), handlesById, platformsById);

            workbook.write(outputStream);
        } catch (IOException ex) {
            throw new ReportException("Cannot write Excel report: " + outputFile.toAbsolutePath(), ex);
        }
    }

    private void writeOverviewSheet(Workbook workbook, WorkbookStyles styles, ExcelReportData data) {
        Sheet sheet = workbook.createSheet("Tong quan");
        int rowIndex = 0;
        rowIndex = writeTitle(sheet, styles, rowIndex, "Báo cáo Excel đánh giá nick lập trình");
        rowIndex = writeKeyValue(sheet, styles, rowIndex, "Ngày xuất báo cáo", formatDateTime(data.generatedAt()));
        rowIndex = writeKeyValue(sheet, styles, rowIndex, "Khoảng thời gian", formatDate(data.periodStart()) + " - " + formatDate(data.periodEnd()));
        rowIndex = writeKeyValue(sheet, styles, rowIndex, "Số handle", String.valueOf(data.handles().size()));
        rowIndex = writeKeyValue(sheet, styles, rowIndex, "Tổng submissions", String.valueOf(data.submissions().size()));
        rowIndex = writeKeyValue(sheet, styles, rowIndex, "Accepted submissions", String.valueOf(data.submissions().stream().filter(this::isAccepted).count()));
        rowIndex = writeKeyValue(sheet, styles, rowIndex, "AI analysis records", String.valueOf(data.analyses().size()));
        writeKeyValue(sheet, styles, rowIndex, "Skill score records", String.valueOf(data.skillScores().size()));
        autosize(sheet, 2);
    }

    private void writeHandlesSheet(
            Workbook workbook,
            WorkbookStyles styles,
            List<HandleAccount> handles,
            Map<Long, Platform> platformsById
    ) {
        Sheet sheet = workbook.createSheet("Danh sach handles");
        int rowIndex = writeHeader(sheet, styles, 0,
                "Handle ID", "Platform", "Handle", "Display Name", "Group", "Consent", "Active",
                "Last Crawled", "Notes"
        );
        for (HandleAccount handle : handles) {
            Platform platform = platformsById.get(handle.getPlatformId());
            Row row = sheet.createRow(rowIndex++);
            write(row, 0, handle.getHandleId(), styles.normal());
            write(row, 1, platform == null ? "-" : platform.getName(), styles.normal());
            write(row, 2, handle.getHandle(), styles.normal());
            write(row, 3, handle.getDisplayName(), styles.normal());
            write(row, 4, handle.getGroupName(), styles.normal());
            write(row, 5, handle.getConsentStatus(), styles.normal());
            write(row, 6, handle.isActive() ? "YES" : "NO", styles.normal());
            write(row, 7, formatDateTime(handle.getLastCrawledAt()), styles.normal());
            write(row, 8, handle.getNotes(), styles.wrap());
        }
        freezeAndAutosize(sheet, 9);
    }

    private void writeSubmissionsSheet(
            Workbook workbook,
            WorkbookStyles styles,
            List<Submission> submissions,
            Map<Long, HandleAccount> handlesById,
            Map<Long, Platform> platformsById
    ) {
        Sheet sheet = workbook.createSheet("Submissions");
        int rowIndex = writeHeader(sheet, styles, 0,
                "Submission ID", "Platform", "Handle", "Remote ID", "Problem Code", "Problem Name",
                "Language", "Verdict", "Submitted At", "Rating", "Tags"
        );
        for (Submission submission : submissions) {
            HandleAccount handle = handlesById.get(submission.getHandleId());
            Platform platform = handle == null ? null : platformsById.get(handle.getPlatformId());
            Row row = sheet.createRow(rowIndex++);
            write(row, 0, submission.getSubmissionId(), styles.normal());
            write(row, 1, platform == null ? "-" : platform.getName(), styles.normal());
            write(row, 2, handle == null ? "-" : handle.getHandle(), styles.normal());
            write(row, 3, submission.getPlatformSubmissionId(), styles.normal());
            write(row, 4, submission.getProblemCode(), styles.normal());
            write(row, 5, submission.getProblemName(), styles.wrap());
            write(row, 6, submission.getLanguage(), styles.normal());
            write(row, 7, submission.getVerdict(), styles.normal());
            write(row, 8, formatDateTime(submission.getSubmittedAt()), styles.normal());
            write(row, 9, submission.getProblemRating(), styles.normal());
            write(row, 10, submission.getProblemTags(), styles.wrap());
        }
        freezeAndAutosize(sheet, 11);
    }

    private void writeAnalysisSheet(
            Workbook workbook,
            WorkbookStyles styles,
            List<AiAnalysisResult> analyses,
            Map<Long, Submission> submissionsById,
            Map<Long, HandleAccount> handlesById,
            Map<Long, Platform> platformsById
    ) {
        Sheet sheet = workbook.createSheet("AI Analysis");
        int rowIndex = writeHeader(sheet, styles, 0,
                "Analysis ID", "Platform", "Handle", "Submission ID", "Analyzer", "Model",
                "Algorithms", "Data Structures", "Complexity", "Code Quality", "AI Risk", "Risk Level",
                "Created At", "Summary"
        );
        for (AiAnalysisResult analysis : analyses) {
            Submission submission = submissionsById.get(analysis.getSubmissionId());
            HandleAccount handle = submission == null ? null : handlesById.get(submission.getHandleId());
            Platform platform = handle == null ? null : platformsById.get(handle.getPlatformId());
            Row row = sheet.createRow(rowIndex++);
            write(row, 0, analysis.getAnalysisId(), styles.normal());
            write(row, 1, platform == null ? "-" : platform.getName(), styles.normal());
            write(row, 2, handle == null ? "-" : handle.getHandle(), styles.normal());
            write(row, 3, analysis.getSubmissionId(), styles.normal());
            write(row, 4, analysis.getAnalyzerType(), styles.normal());
            write(row, 5, analysis.getModelName(), styles.normal());
            write(row, 6, analysis.getAlgorithms(), styles.wrap());
            write(row, 7, analysis.getDataStructures(), styles.wrap());
            write(row, 8, analysis.getComplexityEstimate(), styles.wrap());
            write(row, 9, analysis.getCodeQualityScore(), styles.normal());
            write(row, 10, analysis.getAiRiskScore(), styles.normal());
            write(row, 11, analysis.getAiRiskLevel(), styles.normal());
            write(row, 12, formatDateTime(analysis.getCreatedAt()), styles.normal());
            write(row, 13, analysis.getSummary(), styles.wrap());
        }
        freezeAndAutosize(sheet, 14);
    }

    private void writeSkillScoresSheet(
            Workbook workbook,
            WorkbookStyles styles,
            List<SkillScore> skillScores,
            Map<Long, HandleAccount> handlesById,
            Map<Long, Platform> platformsById
    ) {
        Sheet sheet = workbook.createSheet("Skill Scores");
        int rowIndex = writeHeader(sheet, styles, 0,
                "Score ID", "Platform", "Handle", "Period Start", "Period End", "DS", "Algorithm",
                "Problem Solving", "Code Quality", "Consistency", "AI Risk", "Overall", "Generated At", "Summary"
        );
        for (SkillScore score : skillScores) {
            HandleAccount handle = handlesById.get(score.getHandleId());
            Platform platform = handle == null ? null : platformsById.get(handle.getPlatformId());
            Row row = sheet.createRow(rowIndex++);
            write(row, 0, score.getScoreId(), styles.normal());
            write(row, 1, platform == null ? "-" : platform.getName(), styles.normal());
            write(row, 2, handle == null ? "-" : handle.getHandle(), styles.normal());
            write(row, 3, formatDate(score.getPeriodStart()), styles.normal());
            write(row, 4, formatDate(score.getPeriodEnd()), styles.normal());
            write(row, 5, score.getDataStructureScore(), styles.normal());
            write(row, 6, score.getAlgorithmScore(), styles.normal());
            write(row, 7, score.getProblemSolvingScore(), styles.normal());
            write(row, 8, score.getCodeQualityScore(), styles.normal());
            write(row, 9, score.getPracticeConsistencyScore(), styles.normal());
            write(row, 10, score.getAiUsageRiskScore(), styles.normal());
            write(row, 11, score.getOverallScore(), styles.normal());
            write(row, 12, formatDateTime(score.getGeneratedAt()), styles.normal());
            write(row, 13, score.getSummary(), styles.wrap());
        }
        freezeAndAutosize(sheet, 14);
    }

    private int writeTitle(Sheet sheet, WorkbookStyles styles, int rowIndex, String title) {
        Row row = sheet.createRow(rowIndex++);
        Cell cell = row.createCell(0);
        cell.setCellValue(title);
        cell.setCellStyle(styles.title());
        return rowIndex + 1;
    }

    private int writeKeyValue(Sheet sheet, WorkbookStyles styles, int rowIndex, String key, String value) {
        Row row = sheet.createRow(rowIndex++);
        write(row, 0, key, styles.header());
        write(row, 1, value, styles.normal());
        return rowIndex;
    }

    private int writeHeader(Sheet sheet, WorkbookStyles styles, int rowIndex, String... values) {
        Row row = sheet.createRow(rowIndex++);
        for (int i = 0; i < values.length; i++) {
            write(row, i, values[i], styles.header());
        }
        return rowIndex;
    }

    private void write(Row row, int cellIndex, Object value, CellStyle style) {
        Cell cell = row.createCell(cellIndex);
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof BigDecimal decimal) {
            cell.setCellValue(decimal.doubleValue());
        } else {
            cell.setCellValue(String.valueOf(value));
        }
        cell.setCellStyle(style);
    }

    private void freezeAndAutosize(Sheet sheet, int columnCount) {
        sheet.createFreezePane(0, 1);
        autosize(sheet, columnCount);
    }

    private void autosize(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
            int currentWidth = sheet.getColumnWidth(i);
            sheet.setColumnWidth(i, Math.min(Math.max(currentWidth + 512, 2800), 12000));
        }
    }

    private <T> Map<Long, T> mapById(List<T> values, Function<T, Long> idGetter) {
        Map<Long, T> mapped = new LinkedHashMap<>();
        for (T value : values) {
            Long id = idGetter.apply(value);
            if (id != null) {
                mapped.put(id, value);
            }
        }
        return mapped;
    }

    private boolean isAccepted(Submission submission) {
        return "OK".equalsIgnoreCase(submission.getVerdict());
    }

    private void validateRequest(ReportRequest request) {
        if (request == null) {
            throw new ReportException("Excel report request must not be null.");
        }
        if (request.periodStart() == null || request.periodEnd() == null) {
            throw new ReportException("Excel report period start and end are required.");
        }
        if (request.periodStart().isAfter(request.periodEnd())) {
            throw new ReportException("Excel report period start must be before or equal to period end.");
        }
    }

    private void validate(ExcelReportData data, Path reportDirectory) {
        if (data == null) {
            throw new ReportException("Excel report data must not be null.");
        }
        if (data.periodStart() == null || data.periodEnd() == null) {
            throw new ReportException("Excel report period start and end are required.");
        }
        if (reportDirectory == null) {
            throw new ReportException("Excel report directory must not be null.");
        }
    }

    private String defaultFileName(ExcelReportData data) {
        String period = data.periodStart() + "_to_" + data.periodEnd();
        return "ai_code_analyzer_report_%s_%s.xlsx".formatted(period, LocalDateTime.now().format(FILE_TIMESTAMP));
    }

    private boolean openFile(Path outputFile) {
        if (!Desktop.isDesktopSupported()) {
            return false;
        }
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.OPEN)) {
            return false;
        }
        try {
            desktop.open(outputFile.toFile());
            return true;
        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }

    private String message(Path outputFile, boolean openAfterExport, boolean opened) {
        if (openAfterExport && opened) {
            return "Đã xuất báo cáo Excel và đã gửi yêu cầu mở file: " + outputFile;
        }
        if (openAfterExport) {
            return "Đã xuất báo cáo Excel: " + outputFile
                    + ". Không thể tự mở file trên môi trường hiện tại, vui lòng mở từ thư mục reports.";
        }
        return "Đã xuất báo cáo Excel: " + outputFile + ". File đã sẵn sàng trong thư mục reports.";
    }

    private String formatDate(LocalDate value) {
        return value == null ? "" : value.format(DATE_FORMATTER);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME_FORMATTER);
    }

    private record WorkbookStyles(CellStyle title, CellStyle header, CellStyle normal, CellStyle wrap) {
        static WorkbookStyles create(Workbook workbook) {
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleFont.setFontName("Arial");

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setFontName("Arial");

            Font normalFont = workbook.createFont();
            normalFont.setFontName("Arial");

            CellStyle title = workbook.createCellStyle();
            title.setFont(titleFont);

            CellStyle header = workbook.createCellStyle();
            header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            applyBorders(header);

            CellStyle normal = workbook.createCellStyle();
            normal.setFont(normalFont);
            normal.setVerticalAlignment(VerticalAlignment.TOP);
            applyBorders(normal);

            CellStyle wrap = workbook.createCellStyle();
            wrap.cloneStyleFrom(normal);
            wrap.setWrapText(true);

            return new WorkbookStyles(title, header, normal, wrap);
        }

        private static void applyBorders(CellStyle style) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
        }
    }
}
