package com.example.aicodeanalyzer.ui.controller;

import com.example.aicodeanalyzer.i18n.I18n;
import com.example.aicodeanalyzer.model.AiAnalysisResult;
import com.example.aicodeanalyzer.model.SourceCodeDetail;
import com.example.aicodeanalyzer.service.AnalysisService;
import com.example.aicodeanalyzer.service.SourceCodeDetailService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Controls the source code detail screen and sends selected code to the analyzer.
 * Điều khiển màn hình chi tiết source code và gửi code được chọn sang bộ phân tích.
 */
public class SourceCodeDetailController {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DecimalFormat SCORE_FORMAT = new DecimalFormat("0.##");

    private final SourceCodeDetailService sourceCodeDetailService;
    private final AnalysisService analysisService;
    private final BiConsumer<String, Boolean> notifier;
    private final I18n i18n;
    private final ObservableList<SourceCodeDetail> allSourceCodes = FXCollections.observableArrayList();
    private final ObservableList<SourceCodeDetail> sourceCodes = FXCollections.observableArrayList();
    private final Set<Long> loadedSourceCodeIds = new HashSet<>();
    private final Set<Long> skippedSourceCodeIds = new HashSet<>();
    private String focusedPlatformCode;
    private String focusedHandle;

    private ComboBox<SourceCodeDetail> sourceSelector;
    private ComboBox<AnalysisFilter> filterComboBox;
    private Label emptyStateLabel;
    private Label platformLabel;
    private Label handleLabel;
    private Label submissionIdLabel;
    private Label sourceCodeIdLabel;
    private Label remoteIdLabel;
    private Label problemLabel;
    private Label languageLabel;
    private Label verdictLabel;
    private Label submittedAtLabel;
    private Label fetchedAtLabel;
    private Label sourceStatusLabel;
    private Label sourceErrorLabel;
    private Label aiStatusLabel;
    private Label sourceStatsLabel;
    private TextArea sourceArea;
    private TextArea analysisArea;
    private ProgressIndicator aiRiskGauge;
    private Label aiRiskScoreLabel;
    private Label aiRiskLevelLabel;
    private FlowPane dataStructureBadges;
    private FlowPane algorithmBadges;
    private Button copyButton;
    private Button analyzeButton;
    private Button skipButton;
    private SourceCodeDetail currentDetail;

    public SourceCodeDetailController(
            SourceCodeDetailService sourceCodeDetailService,
            AnalysisService analysisService,
            BiConsumer<String, Boolean> notifier
    ) {
        this(sourceCodeDetailService, analysisService, notifier, I18n.createDefault());
    }

    public SourceCodeDetailController(
            SourceCodeDetailService sourceCodeDetailService,
            AnalysisService analysisService,
            BiConsumer<String, Boolean> notifier,
            I18n i18n
    ) {
        this.sourceCodeDetailService = Objects.requireNonNull(
                sourceCodeDetailService,
                "sourceCodeDetailService must not be null"
        );
        this.analysisService = Objects.requireNonNull(analysisService, "analysisService must not be null");
        this.notifier = notifier == null ? (message, success) -> { } : notifier;
        this.i18n = Objects.requireNonNull(i18n, "i18n must not be null");
    }

    public Node createView() {
        sourceSelector = new ComboBox<>(sourceCodes);
        sourceSelector.setMaxWidth(Double.MAX_VALUE);
        sourceSelector.setPromptText(t("source.selector.prompt"));
        sourceSelector.setAccessibleText(t("source.selector.prompt"));
        sourceSelector.setConverter(sourceCodeConverter());
        sourceSelector.valueProperty().addListener((observable, oldValue, newValue) -> applyDetail(newValue));

        filterComboBox = new ComboBox<>(FXCollections.observableArrayList(AnalysisFilter.values()));
        filterComboBox.setMaxWidth(Double.MAX_VALUE);
        filterComboBox.setConverter(analysisFilterConverter());
        filterComboBox.setAccessibleText(t("source.filter.all"));
        filterComboBox.getSelectionModel().select(AnalysisFilter.ALL);
        filterComboBox.valueProperty().addListener((observable, oldValue, newValue) -> applySourceFilter(true));

        Button refreshButton = new Button(t("action.refresh"));
        refreshButton.getStyleClass().add("secondary-button");
        prepareActionButton(refreshButton);
        refreshButton.setOnAction(event -> refresh(true));

        copyButton = new Button(t("source.button.copy"));
        copyButton.getStyleClass().add("secondary-button");
        prepareActionButton(copyButton);
        copyButton.setOnAction(event -> copyCode());

        analyzeButton = new Button(t("source.button.analyze"));
        analyzeButton.getStyleClass().add("primary-button");
        prepareActionButton(analyzeButton);
        analyzeButton.setOnAction(event -> analyzeSelectedSource());

        skipButton = new Button(t("source.button.skip"));
        skipButton.getStyleClass().add("secondary-button");
        prepareActionButton(skipButton);
        skipButton.setOnAction(event -> skipSelectedSource());

        sourceSelector.setPrefWidth(320);
        sourceSelector.setMinWidth(0);
        filterComboBox.setPrefWidth(190);
        filterComboBox.setMinWidth(150);
        FlowPane filterRow = actionFlow(filterComboBox, refreshButton);
        FlowPane actionRow = actionFlow(copyButton, analyzeButton, skipButton);

        emptyStateLabel = new Label(t("source.empty"));
        emptyStateLabel.getStyleClass().add("empty-state");
        emptyStateLabel.setWrapText(true);
        emptyStateLabel.setVisible(false);
        emptyStateLabel.setManaged(false);

        VBox sourceListContent = new VBox(filterRow, sourceSelector, actionRow, emptyStateLabel, buildMetadataGrid());
        sourceListContent.setSpacing(12);
        VBox sourceListPanel = card(t("source.metadata.card"), sourceListContent);
        sourceListPanel.setMinWidth(320);
        sourceListPanel.setPrefWidth(390);
        sourceListPanel.setMaxWidth(520);

        VBox screen = new VBox(
                stateBanner(t("source.banner.title"), t("source.banner.detail")),
                buildSourceAndAnalysisSplit(sourceListPanel)
        );
        screen.setPadding(new Insets(0));
        screen.setSpacing(18);
        screen.setMaxWidth(Double.MAX_VALUE);
        screen.getStyleClass().add("screen");

        refresh(false);
        return screen;
    }

    public void refreshSourceList() {
        if (sourceSelector != null) {
            if (hasText(focusedPlatformCode) && hasText(focusedHandle)) {
                showHandleSources(focusedPlatformCode, focusedHandle);
            } else {
                refresh(false);
            }
        }
    }

    public void showAllSources() {
        focusedPlatformCode = null;
        focusedHandle = null;
        if (filterComboBox != null) {
            filterComboBox.getSelectionModel().select(AnalysisFilter.ALL);
        }
        refresh(false);
    }

    public void showHandleSources(String platformCode, String handle) {
        focusedPlatformCode = platformCode;
        focusedHandle = handle;
        if (sourceSelector == null) {
            return;
        }
        emptyStateLabel.setText(t("source.loading"));
        emptyStateLabel.setVisible(true);
        emptyStateLabel.setManaged(true);

        Task<List<SourceCodeDetail>> task = new Task<>() {
            @Override
            protected List<SourceCodeDetail> call() {
                return sourceCodeDetailService.findRecentSourceCodesByHandle(platformCode, handle);
            }
        };

        task.setOnSucceeded(event -> {
            loadedSourceCodeIds.clear();
            allSourceCodes.setAll(task.getValue());
            applySourceFilter(true);
            if (sourceCodes.isEmpty()) {
                emptyStateLabel.setText(t("source.empty.handle", platformCode + "/" + handle));
            }
        });
        task.setOnFailed(event -> {
            Throwable exception = task.getException();
            emptyStateLabel.setText(t("source.error.load"));
            emptyStateLabel.setVisible(true);
            emptyStateLabel.setManaged(true);
            notify(exception == null ? t("source.error.load") : exception.getMessage(), false);
        });

        Thread thread = new Thread(task, "source-code-detail-load-handle");
        thread.setDaemon(true);
        thread.start();
    }

    private GridPane buildMetadataGrid() {
        platformLabel = readOnlyValue();
        handleLabel = readOnlyValue();
        submissionIdLabel = readOnlyValue();
        sourceCodeIdLabel = readOnlyValue();
        remoteIdLabel = readOnlyValue();
        problemLabel = readOnlyValue();
        languageLabel = readOnlyValue();
        verdictLabel = readOnlyValue();
        submittedAtLabel = readOnlyValue();
        fetchedAtLabel = readOnlyValue();
        sourceStatusLabel = readOnlyValue();
        sourceErrorLabel = readOnlyValue();
        aiStatusLabel = readOnlyValue();
        sourceStatsLabel = readOnlyValue();

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.getStyleClass().add("form-grid");

        addReadOnlyRow(grid, 0, t("source.info.platform"), platformLabel);
        addReadOnlyRow(grid, 1, t("table.handle"), handleLabel);
        addReadOnlyRow(grid, 2, t("source.info.submissionId"), submissionIdLabel);
        addReadOnlyRow(grid, 3, t("source.info.sourceCodeId"), sourceCodeIdLabel);
        addReadOnlyRow(grid, 4, t("source.info.remoteId"), remoteIdLabel);
        addReadOnlyRow(grid, 5, t("source.info.problem"), problemLabel);
        addReadOnlyRow(grid, 6, t("source.info.language"), languageLabel);
        addReadOnlyRow(grid, 7, "Verdict", verdictLabel);
        addReadOnlyRow(grid, 8, t("source.info.submittedAt"), submittedAtLabel);
        addReadOnlyRow(grid, 9, t("source.info.fetchedAt"), fetchedAtLabel);
        addReadOnlyRow(grid, 10, t("source.info.sourceStatus"), sourceStatusLabel);
        addReadOnlyRow(grid, 11, t("source.info.sourceError"), sourceErrorLabel);
        addReadOnlyRow(grid, 12, t("source.info.aiStatus"), aiStatusLabel);
        addReadOnlyRow(grid, 13, t("source.info.size"), sourceStatsLabel);
        return grid;
    }

    private SplitPane buildSourceAndAnalysisSplit(Node sourceListPanel) {
        sourceArea = new TextArea();
        sourceArea.getStyleClass().add("code-area");
        sourceArea.setEditable(false);
        sourceArea.setWrapText(false);
        sourceArea.setPrefRowCount(32);
        sourceArea.setPrefColumnCount(80);
        sourceArea.setPromptText(t("source.source.prompt"));
        sourceArea.setAccessibleText(t("source.source.prompt"));
        sourceArea.setMinWidth(0);
        sourceArea.setMinHeight(360);

        analysisArea = new TextArea();
        analysisArea.setEditable(false);
        analysisArea.setWrapText(true);
        analysisArea.setPrefRowCount(12);
        analysisArea.setPromptText(t("source.analysis.prompt"));
        analysisArea.setAccessibleText(t("source.analysis.prompt"));
        analysisArea.setMinWidth(0);
        analysisArea.setMinHeight(240);
        analysisArea.getStyleClass().add("compact-summary");

        aiRiskGauge = new ProgressIndicator(0);
        aiRiskGauge.setMaxSize(96, 96);
        aiRiskGauge.getStyleClass().add("ai-risk-gauge");

        aiRiskScoreLabel = new Label("0%");
        aiRiskScoreLabel.getStyleClass().add("ai-risk-score");

        aiRiskLevelLabel = new Label(t("source.notAnalyzed"));
        aiRiskLevelLabel.getStyleClass().add("metric-hint");

        StackPane gaugeStack = new StackPane(aiRiskGauge, aiRiskScoreLabel);
        gaugeStack.setMinSize(104, 104);
        gaugeStack.setPrefSize(104, 104);
        gaugeStack.setMaxSize(104, 104);

        dataStructureBadges = new FlowPane();
        dataStructureBadges.getStyleClass().add("badge-strip");
        dataStructureBadges.setHgap(6);
        dataStructureBadges.setVgap(6);

        algorithmBadges = new FlowPane();
        algorithmBadges.getStyleClass().add("badge-strip");
        algorithmBadges.setHgap(6);
        algorithmBadges.setVgap(6);

        VBox gaugePanel = new VBox(gaugeStack, aiRiskLevelLabel);
        gaugePanel.setAlignment(Pos.CENTER);
        gaugePanel.setSpacing(8);

        VBox insightPanel = new VBox(
                gaugePanel,
                badgeGroup(t("source.analysis.ds"), dataStructureBadges),
                badgeGroup(t("source.analysis.algo"), algorithmBadges),
                analysisArea
        );
        insightPanel.setSpacing(14);
        VBox.setVgrow(analysisArea, Priority.ALWAYS);

        VBox sourceCard = card(t("table.source"), sourceArea);
        sourceCard.setMinWidth(360);
        VBox analysisCard = card(t("source.analysis.panel"), insightPanel);
        analysisCard.setMinWidth(320);
        SplitPane splitPane = new SplitPane(sourceListPanel, sourceCard, analysisCard);
        splitPane.getStyleClass().add("responsive-split");
        splitPane.setDividerPositions(0.30, 0.70);
        splitPane.setMinHeight(430);
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        return splitPane;
    }

    private void refresh(boolean showNotification) {
        try {
            focusedPlatformCode = null;
            focusedHandle = null;
            List<SourceCodeDetail> loadedSourceCodes = sourceCodeDetailService.findRecentSourceCodes();
            loadedSourceCodeIds.clear();
            allSourceCodes.setAll(loadedSourceCodes);
            applySourceFilter(true);
            if (showNotification) {
                notify(t("source.notify.refreshed"), true);
            }
        } catch (RuntimeException ex) {
            allSourceCodes.clear();
            sourceCodes.clear();
            applyDetail(null);
            emptyStateLabel.setText(t("source.error.load"));
            emptyStateLabel.setVisible(true);
            emptyStateLabel.setManaged(true);
            notify(ex.getMessage(), false);
        }
    }

    private void applyDetail(SourceCodeDetail detail) {
        currentDetail = detail;
        boolean hasDetail = detail != null;

        emptyStateLabel.setVisible(!hasDetail);
        emptyStateLabel.setManaged(!hasDetail);
        copyButton.setDisable(!hasDetail || !hasText(detail.codeContent()));
        analyzeButton.setDisable(!hasDetail || !hasText(detail.codeContent()));
        skipButton.setDisable(!hasDetail);

        if (!hasDetail) {
            platformLabel.setText("-");
            handleLabel.setText("-");
            submissionIdLabel.setText("-");
            sourceCodeIdLabel.setText("-");
            remoteIdLabel.setText("-");
            problemLabel.setText("-");
            languageLabel.setText("-");
            verdictLabel.setText("-");
            submittedAtLabel.setText("-");
            fetchedAtLabel.setText("-");
            sourceStatusLabel.setText("-");
            sourceErrorLabel.setText("-");
            aiStatusLabel.setText("-");
            sourceStatsLabel.setText("-");
            sourceArea.clear();
            analysisArea.clear();
            updateAnalysisVisuals(null);
            return;
        }

        analyzeButton.setText(detail.latestAnalysis() == null
                ? t("source.button.analyze")
                : t("source.button.reanalyze"));
        platformLabel.setText(valueOrDash(detail.platformName()));
        handleLabel.setText(valueOrDash(detail.handle()));
        submissionIdLabel.setText(detail.submissionId() == null ? "-" : String.valueOf(detail.submissionId()));
        sourceCodeIdLabel.setText(detail.sourceCodeId() == null ? "-" : String.valueOf(detail.sourceCodeId()));
        remoteIdLabel.setText(valueOrDash(detail.remoteId()));
        problemLabel.setText(detail.problemDisplay());
        languageLabel.setText(valueOrDash(detail.language()));
        verdictLabel.setText(valueOrDash(detail.verdict()));
        submittedAtLabel.setText(detail.submittedAt() == null ? "-" : DATE_TIME_FORMATTER.format(detail.submittedAt()));
        fetchedAtLabel.setText(detail.fetchedAt() == null ? "-" : DATE_TIME_FORMATTER.format(detail.fetchedAt()));
        sourceStatusLabel.setText(formatSourceStatus(detail));
        sourceErrorLabel.setText(valueOrDash(detail.sourceCrawlError()));
        aiStatusLabel.setText(formatAiStatus(detail));
        sourceStatsLabel.setText(formatSourceStats(detail));
        analysisArea.setText(formatAnalysis(detail.latestAnalysis()));
        updateAnalysisVisuals(detail.latestAnalysis());

        if (shouldLoadSourceContent(detail)) {
            sourceArea.setText(t("source.loading"));
            copyButton.setDisable(true);
            analyzeButton.setDisable(true);
            loadSourceContent(detail.sourceCodeId());
            return;
        }

        if (!hasText(detail.codeContent())) {
            sourceArea.setText(sourceContentUnavailableMessage(detail));
            copyButton.setDisable(true);
            analyzeButton.setDisable(true);
            return;
        }

        sourceArea.setText(detail.codeContent());
        sourceArea.positionCaret(0);
    }

    private boolean shouldLoadSourceContent(SourceCodeDetail detail) {
        return detail.sourceCodeId() != null
                && !loadedSourceCodeIds.contains(detail.sourceCodeId())
                && !hasText(detail.codeContent());
    }

    private String sourceContentUnavailableMessage(SourceCodeDetail detail) {
        if (detail == null) {
            return t("source.source.prompt");
        }
        String status = valueOrDash(detail.sourceCrawlStatus());
        String error = valueOrDash(detail.sourceCrawlError());
        if (!"-".equals(error)) {
            return t("source.error.emptyContentWithReason", status, error);
        }
        return t("source.error.emptyContent", status);
    }

    private void loadSourceContent(long sourceCodeId) {
        Task<SourceCodeDetail> task = new Task<>() {
            @Override
            protected SourceCodeDetail call() {
                return sourceCodeDetailService.findBySourceCodeId(sourceCodeId)
                        .orElseThrow(() -> new IllegalStateException(t("source.error.loadById", sourceCodeId)));
            }
        };

        task.setOnSucceeded(event -> {
            SourceCodeDetail loadedDetail = task.getValue();
            if (hasText(loadedDetail.codeContent())) {
                loadedSourceCodeIds.add(sourceCodeId);
            }
            replaceSourceCode(loadedDetail);
            replaceSourceCodeInAll(loadedDetail);
            if (currentDetail != null && Objects.equals(currentDetail.sourceCodeId(), sourceCodeId)) {
                selectAndApplyDetail(loadedDetail);
            }
        });

        task.setOnFailed(event -> {
            if (currentDetail != null && Objects.equals(currentDetail.sourceCodeId(), sourceCodeId)) {
                sourceArea.setText(t("source.error.load"));
            }
            Throwable exception = task.getException();
            notify(exception == null ? t("source.error.load") : exception.getMessage(), false);
        });

        Thread thread = new Thread(task, "source-code-detail-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void reloadSubmissionDetail(long submissionId) {
        Task<SourceCodeDetail> task = new Task<>() {
            @Override
            protected SourceCodeDetail call() {
                return sourceCodeDetailService.findBySubmissionId(submissionId)
                        .orElseThrow(() -> new IllegalStateException(t("source.error.load")));
            }
        };

        task.setOnSucceeded(event -> {
            SourceCodeDetail loadedDetail = task.getValue();
            replaceSourceCode(loadedDetail);
            replaceSourceCodeInAll(loadedDetail);
            if (currentDetail != null && Objects.equals(currentDetail.submissionId(), submissionId)) {
                selectAndApplyDetail(loadedDetail);
            }
        });

        task.setOnFailed(event -> {
            if (currentDetail != null && Objects.equals(currentDetail.submissionId(), submissionId)) {
                sourceArea.setText(t("source.error.load"));
            }
            Throwable exception = task.getException();
            notify(exception == null ? t("source.error.load") : exception.getMessage(), false);
        });

        Thread thread = new Thread(task, "source-submission-detail-load");
        thread.setDaemon(true);
        thread.start();
    }

    private void copyCode() {
        if (currentDetail == null || !hasText(sourceArea.getText())) {
            notify(t("source.error.copy"), false);
            return;
        }

        ClipboardContent content = new ClipboardContent();
        content.putString(sourceArea.getText());
        Clipboard.getSystemClipboard().setContent(content);
        notify(t("source.notify.copied"), true);
    }

    private void analyzeSelectedSource() {
        if (currentDetail == null || currentDetail.sourceCodeId() == null) {
            notify(t("source.error.choose"), false);
            return;
        }

        long sourceCodeId = currentDetail.sourceCodeId();
        analyzeButton.setDisable(true);
        analysisArea.setText(t("source.analyzing"));

        Task<SourceCodeDetail> task = new Task<>() {
            @Override
            protected SourceCodeDetail call() {
                analysisService.analyzeSourceCode(sourceCodeId);
                return sourceCodeDetailService.findBySourceCodeId(sourceCodeId)
                        .orElseThrow(() -> new IllegalStateException(t("source.error.reloadAfterAnalyze")));
            }
        };

        task.setOnSucceeded(event -> {
            SourceCodeDetail updatedDetail = task.getValue();
            replaceSourceCode(updatedDetail);
            replaceSourceCodeInAll(updatedDetail);
            applySourceFilter(false);
            sourceSelector.getSelectionModel().select(updatedDetail);
            applyDetail(updatedDetail);
            notify(t("source.notify.analyzed"), true);
        });

        task.setOnFailed(event -> {
            analyzeButton.setDisable(false);
            analysisArea.setText(formatAnalysis(currentDetail.latestAnalysis()));
            Throwable exception = task.getException();
            notify(exception == null ? t("source.error.analyze") : exception.getMessage(), false);
        });

        Thread thread = new Thread(task, "source-code-analysis");
        thread.setDaemon(true);
        thread.start();
    }

    private void skipSelectedSource() {
        if (currentDetail == null || currentDetail.sourceCodeId() == null) {
            notify(t("source.error.choose"), false);
            return;
        }
        skippedSourceCodeIds.add(currentDetail.sourceCodeId());
        notify(t("source.notify.skipped"), true);
        applySourceFilter(true);
    }

    private void replaceSourceCode(SourceCodeDetail updatedDetail) {
        for (int i = 0; i < sourceCodes.size(); i++) {
            SourceCodeDetail item = sourceCodes.get(i);
            if (sameSubmissionDetail(item, updatedDetail)) {
                sourceCodes.set(i, updatedDetail);
                return;
            }
        }
        sourceCodes.add(0, updatedDetail);
    }

    private void replaceSourceCodeInAll(SourceCodeDetail updatedDetail) {
        for (int i = 0; i < allSourceCodes.size(); i++) {
            SourceCodeDetail item = allSourceCodes.get(i);
            if (sameSubmissionDetail(item, updatedDetail)) {
                allSourceCodes.set(i, updatedDetail);
                return;
            }
        }
        allSourceCodes.add(0, updatedDetail);
    }

    private boolean sameSubmissionDetail(SourceCodeDetail left, SourceCodeDetail right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.sourceCodeId() != null && right.sourceCodeId() != null) {
            return Objects.equals(left.sourceCodeId(), right.sourceCodeId());
        }
        return Objects.equals(left.submissionId(), right.submissionId());
    }

    private void applySourceFilter(boolean selectFirst) {
        if (emptyStateLabel == null || platformLabel == null || copyButton == null || analyzeButton == null) {
            return;
        }
        AnalysisFilter filter = filterComboBox == null || filterComboBox.getValue() == null
                ? AnalysisFilter.ALL
                : filterComboBox.getValue();
        Long selectedSubmissionId = currentDetail == null ? null : currentDetail.submissionId();
        List<SourceCodeDetail> filtered = allSourceCodes.stream()
                .filter(detail -> matchesFilter(detail, filter))
                .toList();
        sourceCodes.setAll(filtered);

        if (sourceCodes.isEmpty()) {
            applyDetail(null);
            emptyStateLabel.setText(t(filter.emptyKey()));
            return;
        }

        if (!selectFirst && selectedSubmissionId != null) {
            for (SourceCodeDetail detail : sourceCodes) {
                if (Objects.equals(detail.submissionId(), selectedSubmissionId)) {
                    selectAndApplyDetail(detail);
                    return;
                }
            }
        }
        selectAndApplyDetail(sourceCodes.getFirst());
    }

    private void selectAndApplyDetail(SourceCodeDetail detail) {
        if (detail == null) {
            applyDetail(null);
            return;
        }
        SourceCodeDetail selected = sourceSelector.getSelectionModel().getSelectedItem();
        sourceSelector.getSelectionModel().select(detail);
        if (Objects.equals(selected, detail)) {
            applyDetail(detail);
        }
    }

    private boolean matchesFilter(SourceCodeDetail detail, AnalysisFilter filter) {
        if (detail == null) {
            return false;
        }
        boolean skipped = detail.sourceCodeId() != null && skippedSourceCodeIds.contains(detail.sourceCodeId());
        String jobStatus = detail.analysisJobStatus();
        boolean attention = isStatus(jobStatus, "FAILED", "QUOTA_DELAYED");
        boolean analyzed = detail.latestAnalysis() != null || isStatus(jobStatus, "SUCCEEDED");
        boolean pending = !analyzed && (jobStatus == null || isStatus(jobStatus, "PENDING", "RUNNING"));
        return switch (filter) {
            case ALL -> !skipped;
            case PENDING -> !skipped && pending && !attention;
            case ANALYZED -> !skipped && analyzed;
            case SKIPPED -> skipped;
            case ATTENTION -> !skipped && attention;
        };
    }

    private boolean isStatus(String value, String... candidates) {
        if (!hasText(value) || candidates == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (candidate != null && candidate.equalsIgnoreCase(value.trim())) {
                return true;
            }
        }
        return false;
    }

    private String formatAnalysis(AiAnalysisResult analysis) {
        if (analysis == null) {
            return t("source.noAnalysis");
        }

        return """
                %s: %s %s
                %s: %s
                %s: %s
                %s: %s
                %s: %s
                %s: %s/100
                %s: %s/100 (%s)

                %s:
                %s
                """.formatted(
                t("source.analysis.text.analyzer"),
                valueOrDash(analysis.getAnalyzerType()),
                valueOrDash(analysis.getAnalyzerVersion()),
                t("source.analysis.text.model"),
                valueOrDash(analysis.getModelName()),
                t("source.analysis.ds"),
                valueOrDash(analysis.getDataStructures()),
                t("source.analysis.algo"),
                valueOrDash(analysis.getAlgorithms()),
                t("source.analysis.text.complexity"),
                valueOrDash(analysis.getComplexityEstimate()),
                t("source.analysis.text.quality"),
                score(analysis.getCodeQualityScore()),
                t("source.analysis.text.risk"),
                score(analysis.getAiRiskScore()),
                valueOrDash(analysis.getAiRiskLevel()),
                t("source.analysis.text.summary"),
                valueOrDash(analysis.getSummary())
        );
    }

    private VBox badgeGroup(String title, FlowPane badges) {
        Label label = new Label(title);
        label.getStyleClass().add("form-label");
        VBox group = new VBox(label, badges);
        group.setSpacing(8);
        return group;
    }

    private void updateAnalysisVisuals(AiAnalysisResult analysis) {
        if (aiRiskGauge == null) {
            return;
        }
        if (analysis == null) {
            aiRiskGauge.setProgress(0);
            aiRiskScoreLabel.setText("0%");
            aiRiskLevelLabel.setText(t("source.noAnalysisShort"));
            setBadges(dataStructureBadges, List.of("Waiting"), "analysis-badge");
            setBadges(algorithmBadges, List.of("Analyze"), "analysis-badge");
            return;
        }

        int risk = roundedScore(analysis.getAiRiskScore());
        aiRiskGauge.setProgress(risk / 100.0);
        aiRiskScoreLabel.setText(risk + "%");
        aiRiskLevelLabel.setText(t("source.analysis.probability", risk, valueOrDash(analysis.getAiRiskLevel())));
        setBadges(dataStructureBadges, tokens(analysis.getDataStructures()), "analysis-badge-primary");
        setBadges(algorithmBadges, tokens(analysis.getAlgorithms()), "analysis-badge-secondary");
    }

    private void setBadges(FlowPane pane, List<String> values, String styleClass) {
        if (pane == null) {
            return;
        }
        pane.getChildren().clear();
        List<String> safeValues = values == null || values.isEmpty() ? List.of("-") : values;
        for (String value : safeValues) {
            Label badge = new Label(value);
            badge.getStyleClass().addAll("analysis-badge", styleClass);
            pane.getChildren().add(badge);
        }
    }

    private List<String> tokens(String rawValue) {
        if (!hasText(rawValue) || "-".equals(rawValue.trim())) {
            return List.of("Unknown");
        }
        return java.util.Arrays.stream(rawValue.split("[,;/|]"))
                .map(String::trim)
                .filter(this::hasText)
                .limit(8)
                .toList();
    }

    private int roundedScore(BigDecimal value) {
        if (value == null) {
            return 0;
        }
        int score = (int) Math.round(value.doubleValue());
        return Math.max(0, Math.min(100, score));
    }

    private String formatSourceStats(SourceCodeDetail detail) {
        int lines = detail.lineCount() == null ? countLines(detail.codeContent()) : detail.lineCount();
        int chars = detail.charCount() == null
                ? (detail.codeContent() == null ? 0 : detail.codeContent().length())
                : detail.charCount();
        return lines + " lines, " + chars + " chars, hash " + valueOrDash(detail.codeHash());
    }

    private String formatSourceStatus(SourceCodeDetail detail) {
        String status = valueOrDash(detail.sourceCrawlStatus());
        if (hasText(detail.sourceCrawlError())) {
            return status + " - " + detail.sourceCrawlError();
        }
        return status;
    }

    private String formatAiStatus(SourceCodeDetail detail) {
        if (detail.latestAnalysis() != null) {
            return t("source.status.analyzed") + " #" + detail.latestAnalysis().getAnalysisId();
        }
        return valueOrDash(detail.analysisJobStatus());
    }

    private VBox sectionHeader(String title, String subtitle) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-title");

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("muted-text");
        subtitleLabel.setWrapText(true);

        VBox box = new VBox(titleLabel, subtitleLabel);
        box.setSpacing(4);
        return box;
    }

    private VBox card(String title, Node content) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("card-title");
        titleLabel.setWrapText(true);
        titleLabel.setMinHeight(Region.USE_PREF_SIZE);

        VBox box = new VBox(titleLabel, content);
        box.setSpacing(12);
        box.setPadding(new Insets(16));
        box.getStyleClass().add("card");
        box.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(content, Priority.ALWAYS);
        return box;
    }

    private FlowPane actionFlow(Node... nodes) {
        FlowPane flowPane = new FlowPane(nodes);
        flowPane.setHgap(10);
        flowPane.setVgap(10);
        flowPane.setAlignment(Pos.CENTER_LEFT);
        flowPane.getStyleClass().add("action-flow");
        return flowPane;
    }

    private void prepareActionButton(Button button) {
        button.setWrapText(true);
        button.setMinWidth(0);
        button.setTooltip(new Tooltip(button.getText()));
        button.setAccessibleText(button.getText());
    }

    private VBox stateBanner(String title, String detail) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("state-title");
        titleLabel.setWrapText(true);
        titleLabel.setMinHeight(Region.USE_PREF_SIZE);

        Label detailLabel = new Label(detail);
        detailLabel.getStyleClass().add("state-detail");
        detailLabel.setWrapText(true);
        detailLabel.setMinHeight(Region.USE_PREF_SIZE);

        VBox box = new VBox(titleLabel, detailLabel);
        box.setSpacing(4);
        box.getStyleClass().addAll("state-banner", "state-banner-info");
        return box;
    }

    private void addReadOnlyRow(GridPane grid, int row, String label, Label valueLabel) {
        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("form-label");
        labelNode.setWrapText(true);
        labelNode.setMinHeight(Region.USE_PREF_SIZE);
        labelNode.setMinWidth(110);
        labelNode.setPrefWidth(118);
        labelNode.setMaxWidth(126);
        grid.add(labelNode, 0, row);
        grid.add(valueLabel, 1, row);
        GridPane.setHgrow(valueLabel, Priority.ALWAYS);
    }

    private Label readOnlyValue() {
        Label label = new Label("-");
        label.getStyleClass().add("read-only-value");
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setWrapText(true);
        return label;
    }

    private StringConverter<SourceCodeDetail> sourceCodeConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(SourceCodeDetail detail) {
                if (detail == null) {
                    return "";
                }
                String sourceMarker = detail.sourceCodeId() == null
                        ? t("source.status.noSourceRow")
                        : "source #" + detail.sourceCodeId();
                return detail.displayLabel() + " / submission #" + detail.submissionId() + " / " + sourceMarker;
            }

            @Override
            public SourceCodeDetail fromString(String string) {
                return null;
            }
        };
    }

    private StringConverter<AnalysisFilter> analysisFilterConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(AnalysisFilter filter) {
                return filter == null ? "" : t(filter.labelKey());
            }

            @Override
            public AnalysisFilter fromString(String string) {
                return AnalysisFilter.ALL;
            }
        };
    }

    private String score(BigDecimal value) {
        return value == null ? "-" : SCORE_FORMAT.format(value);
    }

    private String valueOrDash(String value) {
        return hasText(value) ? value : "-";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private int countLines(String code) {
        if (code == null || code.isEmpty()) {
            return 0;
        }
        return code.split("\\R", -1).length;
    }

    private void notify(String message, boolean success) {
        notifier.accept(message, success);
    }

    private String t(String key, Object... args) {
        return i18n.text(key, args);
    }

    private enum AnalysisFilter {
        ALL("source.filter.all", "source.empty"),
        PENDING("source.filter.pending", "source.empty.pending"),
        ANALYZED("source.filter.analyzed", "source.empty.analyzed"),
        ATTENTION("source.filter.attention", "source.empty.attention"),
        SKIPPED("source.filter.skipped", "source.empty.skipped");

        private final String labelKey;
        private final String emptyKey;

        AnalysisFilter(String labelKey, String emptyKey) {
            this.labelKey = labelKey;
            this.emptyKey = emptyKey;
        }

        private String labelKey() {
            return labelKey;
        }

        private String emptyKey() {
            return emptyKey;
        }
    }
}
