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
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
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
    private final ObservableList<SourceCodeDetail> sourceCodes = FXCollections.observableArrayList();
    private final Set<Long> loadedSourceCodeIds = new HashSet<>();

    private ComboBox<SourceCodeDetail> sourceSelector;
    private Label emptyStateLabel;
    private Label platformLabel;
    private Label handleLabel;
    private Label problemLabel;
    private Label languageLabel;
    private Label verdictLabel;
    private Label submittedAtLabel;
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
        sourceSelector.setConverter(sourceCodeConverter());
        sourceSelector.valueProperty().addListener((observable, oldValue, newValue) -> applyDetail(newValue));

        Button refreshButton = new Button(t("action.refresh"));
        refreshButton.getStyleClass().add("secondary-button");
        refreshButton.setOnAction(event -> refresh(true));

        copyButton = new Button(t("source.button.copy"));
        copyButton.getStyleClass().add("secondary-button");
        copyButton.setOnAction(event -> copyCode());

        analyzeButton = new Button(t("source.button.analyze"));
        analyzeButton.getStyleClass().add("primary-button");
        analyzeButton.setOnAction(event -> analyzeSelectedSource());

        FlowPane toolbar = actionFlow(sourceSelector, refreshButton, copyButton, analyzeButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        sourceSelector.setPrefWidth(320);
        sourceSelector.setMinWidth(220);

        emptyStateLabel = new Label(t("source.empty"));
        emptyStateLabel.getStyleClass().add("empty-state");
        emptyStateLabel.setWrapText(true);
        emptyStateLabel.setVisible(false);
        emptyStateLabel.setManaged(false);

        VBox screen = new VBox(
            stateBanner(t("source.banner.title"), t("source.banner.detail")),
                toolbar,
                emptyStateLabel,
                card(t("source.metadata.card"), buildMetadataGrid()),
                buildSourceAndAnalysisSplit()
        );
        screen.setPadding(new Insets(0));
        screen.setSpacing(18);
        screen.setMaxWidth(Double.MAX_VALUE);
        screen.getStyleClass().add("screen");

        refresh(false);
        return screen;
    }

    private GridPane buildMetadataGrid() {
        platformLabel = readOnlyValue();
        handleLabel = readOnlyValue();
        problemLabel = readOnlyValue();
        languageLabel = readOnlyValue();
        verdictLabel = readOnlyValue();
        submittedAtLabel = readOnlyValue();
        sourceStatsLabel = readOnlyValue();

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.getStyleClass().add("form-grid");

        addReadOnlyRow(grid, 0, t("source.info.platform"), platformLabel);
        addReadOnlyRow(grid, 1, t("table.handle"), handleLabel);
        addReadOnlyRow(grid, 2, t("source.info.problem"), problemLabel);
        addReadOnlyRow(grid, 3, t("source.info.language"), languageLabel);
        addReadOnlyRow(grid, 4, "Verdict", verdictLabel);
        addReadOnlyRow(grid, 5, t("source.info.submittedAt"), submittedAtLabel);
        addReadOnlyRow(grid, 6, t("source.info.size"), sourceStatsLabel);
        return grid;
    }

    private SplitPane buildSourceAndAnalysisSplit() {
        sourceArea = new TextArea();
        sourceArea.getStyleClass().add("code-area");
        sourceArea.setEditable(false);
        sourceArea.setWrapText(false);
        sourceArea.setPrefRowCount(32);
        sourceArea.setPrefColumnCount(120);
        sourceArea.setPromptText(t("source.source.prompt"));
        sourceArea.setMinHeight(360);

        analysisArea = new TextArea();
        analysisArea.setEditable(false);
        analysisArea.setWrapText(true);
        analysisArea.setPrefRowCount(12);
        analysisArea.setPromptText(t("source.analysis.prompt"));
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
        VBox analysisCard = card(t("source.analysis.panel"), insightPanel);
        SplitPane splitPane = new SplitPane(sourceCard, analysisCard);
        splitPane.setDividerPositions(0.66);
        splitPane.setMinHeight(430);
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        return splitPane;
    }

    private void refresh(boolean showNotification) {
        try {
            List<SourceCodeDetail> loadedSourceCodes = sourceCodeDetailService.findRecentSourceCodes();
            loadedSourceCodeIds.clear();
            sourceCodes.setAll(loadedSourceCodes);
            if (sourceCodes.isEmpty()) {
                applyDetail(null);
            } else {
                sourceSelector.getSelectionModel().selectFirst();
            }
            if (showNotification) {
                notify(t("source.notify.refreshed"), true);
            }
        } catch (RuntimeException ex) {
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

        if (!hasDetail) {
            platformLabel.setText("-");
            handleLabel.setText("-");
            problemLabel.setText("-");
            languageLabel.setText("-");
            verdictLabel.setText("-");
            submittedAtLabel.setText("-");
            sourceStatsLabel.setText("-");
            sourceArea.clear();
            analysisArea.clear();
            updateAnalysisVisuals(null);
            return;
        }

        platformLabel.setText(valueOrDash(detail.platformName()));
        handleLabel.setText(valueOrDash(detail.handle()));
        problemLabel.setText(detail.problemDisplay());
        languageLabel.setText(valueOrDash(detail.language()));
        verdictLabel.setText(valueOrDash(detail.verdict()));
        submittedAtLabel.setText(detail.submittedAt() == null ? "-" : DATE_TIME_FORMATTER.format(detail.submittedAt()));
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

        sourceArea.setText(detail.codeContent() == null ? "" : detail.codeContent());
        sourceArea.positionCaret(0);
    }

    private boolean shouldLoadSourceContent(SourceCodeDetail detail) {
        return detail.sourceCodeId() != null
                && !loadedSourceCodeIds.contains(detail.sourceCodeId())
                && detail.codeContent() == null;
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
            loadedSourceCodeIds.add(sourceCodeId);
            replaceSourceCode(loadedDetail);
            if (currentDetail != null && Objects.equals(currentDetail.sourceCodeId(), sourceCodeId)) {
                sourceSelector.getSelectionModel().select(loadedDetail);
                applyDetail(loadedDetail);
            }
        });

        task.setOnFailed(event -> {
            loadedSourceCodeIds.add(sourceCodeId);
            if (currentDetail != null && Objects.equals(currentDetail.sourceCodeId(), sourceCodeId)) {
                sourceArea.setText("");
            }
            Throwable exception = task.getException();
            notify(exception == null ? t("source.error.load") : exception.getMessage(), false);
        });

        Thread thread = new Thread(task, "source-code-detail-load");
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

    private void replaceSourceCode(SourceCodeDetail updatedDetail) {
        for (int i = 0; i < sourceCodes.size(); i++) {
            SourceCodeDetail item = sourceCodes.get(i);
            if (Objects.equals(item.sourceCodeId(), updatedDetail.sourceCodeId())) {
                sourceCodes.set(i, updatedDetail);
                return;
            }
        }
        sourceCodes.add(0, updatedDetail);
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

    private VBox stateBanner(String title, String detail) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("state-title");

        Label detailLabel = new Label(detail);
        detailLabel.getStyleClass().add("state-detail");
        detailLabel.setWrapText(true);

        VBox box = new VBox(titleLabel, detailLabel);
        box.setSpacing(4);
        box.getStyleClass().addAll("state-banner", "state-banner-info");
        return box;
    }

    private void addReadOnlyRow(GridPane grid, int row, String label, Label valueLabel) {
        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("form-label");
        labelNode.setMinWidth(104);
        labelNode.setPrefWidth(124);
        labelNode.setMaxWidth(144);
        grid.add(labelNode, 0, row);
        grid.add(valueLabel, 1, row);
        GridPane.setHgrow(valueLabel, Priority.ALWAYS);
    }

    private Label readOnlyValue() {
        Label label = new Label("-");
        label.getStyleClass().add("read-only-value");
        label.setMaxWidth(Double.MAX_VALUE);
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
                return detail.displayLabel() + " #" + detail.sourceCodeId();
            }

            @Override
            public SourceCodeDetail fromString(String string) {
                return null;
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
}
