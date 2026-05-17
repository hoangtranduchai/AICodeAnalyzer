package com.example.aicodeanalyzer.ui.controller;

import com.example.aicodeanalyzer.i18n.I18n;
import com.example.aicodeanalyzer.model.DashboardSnapshot;
import com.example.aicodeanalyzer.model.DashboardSnapshot.HandleAlgorithmStat;
import com.example.aicodeanalyzer.model.DashboardSnapshot.HandleScoreStat;
import com.example.aicodeanalyzer.model.DashboardSnapshot.PlatformSubmissionStat;
import com.example.aicodeanalyzer.service.DashboardService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Controls dashboard metrics, charts, and recent crawl/analysis summaries.
 * Điều khiển số liệu, biểu đồ và tóm tắt crawl/phân tích gần đây trên dashboard.
 */
@SuppressWarnings("unchecked")
public class DashboardController {
    private static final DecimalFormat SCORE_FORMAT = new DecimalFormat("0.##");

    private final DashboardService dashboardService;
    private final BiConsumer<String, Boolean> notifier;
    private final I18n i18n;
    private final ObservableList<HandleScoreStat> topOverallHandles = FXCollections.observableArrayList();
    private final ObservableList<HandleScoreStat> topAiRiskHandles = FXCollections.observableArrayList();

    private Label totalHandlesValue;
    private Label totalSubmissionsValue;
    private Label pendingAnalysisValue;
    private Label analyzedSourcesValue;
    private Label sourceIssuesValue;
    private Label recentErrorsValue;
    private Label emptyStateLabel;
    private Label platformChartEmptyLabel;
    private Label algorithmChartEmptyLabel;
    private Label dashboardLoadingLabel;
    private Label topOverallDetailLabel;
    private Label topAiRiskDetailLabel;
    private Label topOverallTitleLabel;
    private Label topAiRiskTitleLabel;
    private BarChart<String, Number> platformSubmissionChart;
    private BarChart<String, Number> algorithmScoreChart;
    private Button refreshButton;
    private Spinner<Integer> topOverallLimitSpinner;
    private Spinner<Integer> topAiRiskLimitSpinner;

    public DashboardController(DashboardService dashboardService, BiConsumer<String, Boolean> notifier) {
        this(dashboardService, notifier, I18n.createDefault());
    }

    public DashboardController(DashboardService dashboardService, BiConsumer<String, Boolean> notifier, I18n i18n) {
        this.dashboardService = Objects.requireNonNull(dashboardService, "dashboardService must not be null");
        this.notifier = notifier == null ? (message, success) -> { } : notifier;
        this.i18n = Objects.requireNonNull(i18n, "i18n must not be null");
    }

    public Node createView() {
        return createView(true, true);
    }

    public Node createWorkspacePanel() {
        return createView(false, false);
    }

    public void refreshDashboard() {
        if (refreshButton != null && !refreshButton.isDisable()) {
            refresh(false);
        }
    }

    private Node createView(boolean includeHeader, boolean padded) {
        totalHandlesValue = metricValueLabel();
        totalSubmissionsValue = metricValueLabel();
        pendingAnalysisValue = metricValueLabel();
        analyzedSourcesValue = metricValueLabel();
        sourceIssuesValue = metricValueLabel();
        recentErrorsValue = metricValueLabel();

        refreshButton = new Button(t("action.refresh"));
        refreshButton.getStyleClass().add("primary-button");
        refreshButton.setWrapText(true);
        refreshButton.setTooltip(fastTooltip(t("action.refresh")));
        refreshButton.setAccessibleText(t("action.refresh"));
        refreshButton.setOnAction(event -> refresh(true));

        dashboardLoadingLabel = new Label(t("dashboard.loading"));
        dashboardLoadingLabel.getStyleClass().add("dashboard-loading-chip");
        dashboardLoadingLabel.setVisible(false);
        dashboardLoadingLabel.setManaged(false);
        dashboardLoadingLabel.setWrapText(true);
        dashboardLoadingLabel.setMinWidth(0);
        dashboardLoadingLabel.setMaxWidth(320);
        dashboardLoadingLabel.setMinHeight(Region.USE_PREF_SIZE);
        dashboardLoadingLabel.setAccessibleText(t("dashboard.loading"));

        FlowPane headerActions = new FlowPane(dashboardLoadingLabel, refreshButton);
        headerActions.getStyleClass().add("dashboard-header-actions");
        headerActions.setAlignment(Pos.TOP_RIGHT);
        headerActions.setHgap(10);
        headerActions.setVgap(8);

        VBox headerText = sectionHeader(t("dashboard.title"), t("dashboard.subtitle"));
        headerText.setMinWidth(0);
        headerText.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(headerText, Priority.ALWAYS);

        HBox header = new HBox(headerText, headerActions);
        header.getStyleClass().add("dashboard-header");
        header.setAlignment(Pos.TOP_LEFT);
        header.setSpacing(16);

        emptyStateLabel = new Label(t("dashboard.empty"));
        emptyStateLabel.getStyleClass().add("empty-state");
        emptyStateLabel.setWrapText(true);
        emptyStateLabel.setVisible(false);
        emptyStateLabel.setManaged(false);

        List<Node> metricCards = List.of(
                metricCard(t("dashboard.metric.handles"), totalHandlesValue, t("dashboard.metric.handles.hint")),
                metricCard(t("dashboard.metric.submissions"), totalSubmissionsValue, t("dashboard.metric.submissions.hint")),
                metricCard(t("dashboard.metric.pending"), pendingAnalysisValue, t("dashboard.metric.pending.hint")),
                metricCard(t("dashboard.metric.analyzed"), analyzedSourcesValue, t("dashboard.metric.analyzed.hint")),
                metricCard(t("dashboard.metric.sourceIssues"), sourceIssuesValue, t("dashboard.metric.sourceIssues.hint")),
                metricCard(t("dashboard.metric.errors"), recentErrorsValue, t("dashboard.metric.errors.hint"))
        );
        GridPane metrics = metricGrid(metricCards);

        List<Node> chartCards = List.of(
                card(t("dashboard.chart.platform"), buildPlatformChartPane()),
                card(t("dashboard.chart.algorithm"), buildAlgorithmChartPane())
        );
        GridPane charts = chartGrid(chartCards);

        topOverallTitleLabel = new Label();
        topAiRiskTitleLabel = new Label();
        topOverallLimitSpinner = topLimitSpinner();
        topAiRiskLimitSpinner = topLimitSpinner();
        updateTopTableTitles();

        VBox tables = new VBox(
                card(tableHeader(topOverallTitleLabel, topOverallLimitSpinner), buildTopOverallTable()),
                card(tableHeader(topAiRiskTitleLabel, topAiRiskLimitSpinner), buildTopAiRiskTable())
        );
        tables.getStyleClass().add("dashboard-table-stack");
        tables.setSpacing(16);
        tables.setMaxWidth(Double.MAX_VALUE);

        List<Node> screenChildren = new ArrayList<>();
        if (includeHeader) {
            screenChildren.add(header);
        }
        screenChildren.addAll(List.of(emptyStateLabel, metrics, charts, tables));

        VBox screen = new VBox(screenChildren.toArray(Node[]::new));
        screen.setPadding(padded ? new Insets(24) : Insets.EMPTY);
        screen.setSpacing(18);
        screen.setMaxWidth(Double.MAX_VALUE);
        screen.getStyleClass().addAll("screen", "dashboard-screen");
        bindResponsiveMetricGrid(screen, metrics, metricCards);
        bindResponsiveChartGrid(screen, charts, chartCards);

        refresh(false);
        return screen;
    }

    private void refresh(boolean showSuccessMessage) {
        int overallLimit = currentTopOverallLimit();
        int aiRiskLimit = currentTopAiRiskLimit();
        refreshButton.setDisable(true);
        dashboardLoadingLabel.setText(t("dashboard.loading"));
        dashboardLoadingLabel.setVisible(true);
        dashboardLoadingLabel.setManaged(true);

        Task<DashboardSnapshot> task = new Task<>() {
            @Override
            protected DashboardSnapshot call() {
                return dashboardService.loadDashboard(overallLimit, aiRiskLimit);
            }
        };

        task.setOnSucceeded(event -> {
            refreshButton.setDisable(false);
            dashboardLoadingLabel.setVisible(false);
            dashboardLoadingLabel.setManaged(false);
            applySnapshot(task.getValue());
            if (showSuccessMessage) {
                notify(t("dashboard.refreshed"), true);
            }
        });

        task.setOnFailed(event -> {
            refreshButton.setDisable(false);
            dashboardLoadingLabel.setVisible(false);
            dashboardLoadingLabel.setManaged(false);
            applySnapshot(new DashboardSnapshot(null, List.of(), List.of(), List.of(), List.of()));
            emptyStateLabel.setText(t("dashboard.loadFailed"));
            emptyStateLabel.setVisible(true);
            emptyStateLabel.setManaged(true);
            Throwable exception = task.getException();
            notify(exception == null ? t("dashboard.loadFailed") : exception.getMessage(), false);
        });

        Thread thread = new Thread(task, "dashboard-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    private void applySnapshot(DashboardSnapshot snapshot) {
        updateTopTableTitles();
        totalHandlesValue.setText(String.valueOf(snapshot.summary().totalHandles()));
        totalSubmissionsValue.setText(String.valueOf(snapshot.summary().totalSubmissions()));
        pendingAnalysisValue.setText(String.valueOf(snapshot.summary().pendingAnalysisSources()));
        analyzedSourcesValue.setText(String.valueOf(snapshot.summary().analyzedSourceCodes()));
        sourceIssuesValue.setText(String.valueOf(snapshot.summary().sourceIssueCount()));
        recentErrorsValue.setText(String.valueOf(snapshot.summary().recentCrawlErrors()));

        updatePlatformChart(snapshot.submissionsByPlatform());
        updateAlgorithmChart(snapshot.averageAlgorithmScores());
        topOverallHandles.setAll(snapshot.topOverallHandles());
        topAiRiskHandles.setAll(snapshot.topAiRiskHandles());

        emptyStateLabel.setText(t("dashboard.empty"));
        emptyStateLabel.setVisible(snapshot.hasNoData());
        emptyStateLabel.setManaged(snapshot.hasNoData());
    }

    private StackPane buildPlatformChartPane() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel(t("dashboard.axis.platform"));
        yAxis.setLabel(t("dashboard.metric.submissions"));

        platformSubmissionChart = new BarChart<>(xAxis, yAxis);
        platformSubmissionChart.setLegendVisible(false);
        platformSubmissionChart.setAnimated(false);
        platformSubmissionChart.setCategoryGap(18);
        platformSubmissionChart.setBarGap(4);
        platformSubmissionChart.setMinHeight(280);
        platformSubmissionChart.setMinWidth(0);
        platformSubmissionChart.setPrefHeight(320);

        platformChartEmptyLabel = emptyChartLabel(t("dashboard.chart.platform.empty"));
        return new StackPane(platformSubmissionChart, platformChartEmptyLabel);
    }

    private StackPane buildAlgorithmChartPane() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis(0, 100, 20);
        xAxis.setLabel(t("dashboard.axis.handle"));
        yAxis.setLabel(t("dashboard.axis.algorithm"));

        algorithmScoreChart = new BarChart<>(xAxis, yAxis);
        algorithmScoreChart.setLegendVisible(false);
        algorithmScoreChart.setAnimated(false);
        algorithmScoreChart.setCategoryGap(8);
        algorithmScoreChart.setBarGap(2);
        xAxis.setTickLabelRotation(-35);
        algorithmScoreChart.setMinHeight(280);
        algorithmScoreChart.setMinWidth(0);
        algorithmScoreChart.setPrefHeight(320);

        algorithmChartEmptyLabel = emptyChartLabel(t("dashboard.chart.algorithm.empty"));
        return new StackPane(algorithmScoreChart, algorithmChartEmptyLabel);
    }

    private void updatePlatformChart(List<PlatformSubmissionStat> stats) {
        platformSubmissionChart.getData().clear();
        boolean hasData = stats.stream().anyMatch(stat -> stat.submissionCount() > 0);
        platformChartEmptyLabel.setVisible(!hasData);
        platformChartEmptyLabel.setManaged(!hasData);
        if (!hasData) {
            return;
        }

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (PlatformSubmissionStat stat : stats) {
            XYChart.Data<String, Number> data = new XYChart.Data<>(stat.platformName(), stat.submissionCount());
            series.getData().add(data);
            installBarTooltip(data, stat.platformName() + "\n" + t("dashboard.metric.submissions")
                    + ": " + stat.submissionCount());
        }
        platformSubmissionChart.getData().setAll(series);
    }

    private void updateAlgorithmChart(List<HandleAlgorithmStat> stats) {
        algorithmScoreChart.getData().clear();
        algorithmChartEmptyLabel.setVisible(stats.isEmpty());
        algorithmChartEmptyLabel.setManaged(stats.isEmpty());
        if (stats.isEmpty()) {
            return;
        }

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        stats.forEach(stat -> {
            XYChart.Data<String, Number> data = new XYChart.Data<>(
                    stat.handle(),
                    stat.averageAlgorithmScore()
            );
            series.getData().add(data);
            installBarTooltip(data, stat.platformName() + "/" + stat.handle()
                    + "\n" + t("dashboard.axis.algorithm") + ": " + score(stat.averageAlgorithmScore()));
        });
        algorithmScoreChart.getData().setAll(series);
    }

    private void installBarTooltip(XYChart.Data<String, Number> data, String text) {
        Platform.runLater(() -> installBarTooltipWhenNodeIsReady(data, text, 0));
    }

    private void installBarTooltipWhenNodeIsReady(XYChart.Data<String, Number> data, String text, int attempt) {
        Node node = data.getNode();
        if (node == null) {
            if (attempt < 4) {
                Platform.runLater(() -> installBarTooltipWhenNodeIsReady(data, text, attempt + 1));
            }
            return;
        }
        Tooltip tooltip = new Tooltip(text);
        tooltip.setShowDelay(javafx.util.Duration.millis(35));
        tooltip.setHideDelay(javafx.util.Duration.millis(60));
        tooltip.setShowDuration(javafx.util.Duration.seconds(30));
        Tooltip.install(node, tooltip);
        node.getProperties().put("dashboardTooltipText", text);
    }

    private Node buildTopOverallTable() {
        TableView<HandleScoreStat> table = table(
                topOverallHandles,
                column(t("table.handle"), HandleScoreStat::handle, 180),
                column(t("table.platform"), HandleScoreStat::platformName, 140),
                column(t("table.overall"), row -> score(row.overallScore()), 100),
                column(t("table.algorithms"), row -> score(row.algorithmScore()), 120),
                column(t("table.structures"), row -> score(row.dataStructureScore()), 140)
        );
        table.getStyleClass().add("full-text-table");
        table.setPlaceholder(emptyPlaceholder(t("dashboard.placeholder.score.title"), t("dashboard.placeholder.score.detail")));
        table.setPrefHeight(360);

        topOverallDetailLabel = tableDetailLabel(t("dashboard.table.select"));
        table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected == null) {
                topOverallDetailLabel.setText(t("dashboard.table.select"));
                return;
            }
            topOverallDetailLabel.setText(t(
                    "dashboard.table.overallDetail",
                    selected.handle(),
                    selected.platformName(),
                    score(selected.overallScore()),
                    score(selected.algorithmScore()),
                    score(selected.dataStructureScore())
            ));
        });

        VBox content = new VBox(table, topOverallDetailLabel);
        content.setSpacing(10);
        VBox.setVgrow(table, Priority.ALWAYS);
        return content;
    }

    private Node buildTopAiRiskTable() {
        TableView<HandleScoreStat> table = table(
                topAiRiskHandles,
                column(t("table.handle"), HandleScoreStat::handle, 180),
                column(t("table.platform"), HandleScoreStat::platformName, 140),
                column(t("table.risk"), row -> score(row.aiUsageRiskScore()), 100),
                column(t("table.overall"), row -> score(row.overallScore()), 100),
                column(t("table.summary"), row -> valueOrEmpty(row.summary()), 620)
        );
        table.getStyleClass().add("full-text-table");
        table.setPlaceholder(emptyPlaceholder(t("dashboard.placeholder.risk.title"), t("dashboard.placeholder.risk.detail")));
        table.setPrefHeight(420);

        topAiRiskDetailLabel = tableDetailLabel(t("dashboard.table.select"));
        table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected == null) {
                topAiRiskDetailLabel.setText(t("dashboard.table.select"));
                return;
            }
            topAiRiskDetailLabel.setText(t(
                    "dashboard.table.aiRiskDetail",
                    selected.handle(),
                    selected.platformName(),
                    score(selected.aiUsageRiskScore()),
                    score(selected.overallScore()),
                    valueOrEmpty(selected.summary())
            ));
        });

        VBox content = new VBox(table, topAiRiskDetailLabel);
        content.setSpacing(10);
        VBox.setVgrow(table, Priority.ALWAYS);
        return content;
    }

    private VBox sectionHeader(String title, String subtitle) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("section-title");
        titleLabel.setWrapText(true);
        titleLabel.setMinHeight(Region.USE_PREF_SIZE);

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("muted-text");
        subtitleLabel.setWrapText(true);
        subtitleLabel.setMinHeight(Region.USE_PREF_SIZE);

        VBox box = new VBox(titleLabel, subtitleLabel);
        box.setSpacing(4);
        return box;
    }

    private VBox card(String title, Node content) {
        Label titleLabel = new Label(title);
        return card(titleLabel, content);
    }

    private HBox tableHeader(Label titleLabel, Spinner<Integer> spinner) {
        titleLabel.getStyleClass().add("card-title");
        titleLabel.setWrapText(true);
        titleLabel.setMinHeight(Region.USE_PREF_SIZE);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        Label topLimitLabel = new Label(t("dashboard.topLimit"));
        topLimitLabel.getStyleClass().add("small-label");
        topLimitLabel.setWrapText(true);
        HBox limitControl = new HBox(topLimitLabel, spinner);
        limitControl.getStyleClass().add("dashboard-top-limit");
        limitControl.setAlignment(Pos.CENTER_RIGHT);
        limitControl.setSpacing(8);

        HBox header = new HBox(titleLabel, limitControl);
        header.getStyleClass().add("dashboard-table-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setSpacing(12);
        header.setMaxWidth(Double.MAX_VALUE);
        return header;
    }

    private VBox card(Node header, Node content) {
        VBox box = new VBox(header, content);
        box.setSpacing(12);
        box.setPadding(new Insets(16));
        box.getStyleClass().add("card");
        box.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(content, Priority.ALWAYS);
        return box;
    }

    private GridPane metricGrid(List<Node> cards) {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        grid.setMaxWidth(Double.MAX_VALUE);
        grid.getStyleClass().add("metric-grid");
        updateResponsiveMetricGrid(1200, grid, cards);
        return grid;
    }

    private GridPane chartGrid(List<Node> cards) {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);
        grid.setMaxWidth(Double.MAX_VALUE);
        grid.getStyleClass().add("chart-grid");
        updateResponsiveChartGrid(1200, grid, cards);
        return grid;
    }

    private VBox metricCard(String label, Label valueLabel, String hint) {
        Label labelText = new Label(label);
        labelText.getStyleClass().add("metric-label");
        labelText.setWrapText(true);
        labelText.setMinHeight(Region.USE_PREF_SIZE);

        Label hintText = new Label(hint);
        hintText.getStyleClass().add("metric-hint");
        hintText.setWrapText(true);
        hintText.setMinHeight(Region.USE_PREF_SIZE);

        VBox box = new VBox(valueLabel, labelText, hintText);
        box.setMinWidth(0);
        box.setMaxWidth(Double.MAX_VALUE);
        box.setPadding(new Insets(16));
        box.setSpacing(6);
        box.getStyleClass().add("metric-card");
        return box;
    }

    private Label metricValueLabel() {
        Label label = new Label("0");
        label.getStyleClass().add("metric-value");
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    private VBox emptyPlaceholder(String title, String detail) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("state-title");
        titleLabel.setWrapText(true);
        titleLabel.setMinHeight(Region.USE_PREF_SIZE);

        Label detailLabel = new Label(detail);
        detailLabel.getStyleClass().add("state-detail");
        detailLabel.setWrapText(true);

        VBox box = new VBox(titleLabel, detailLabel);
        box.setAlignment(Pos.CENTER);
        box.setSpacing(4);
        box.setPadding(new Insets(16));
        return box;
    }

    @SafeVarargs
    private final <T> TableView<T> table(ObservableList<T> rows, TableColumn<T, String>... columns) {
        TableView<T> table = new TableView<>(rows);
        table.getColumns().setAll(List.of(columns));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setFixedCellSize(-1);
        table.setMinWidth(0);
        table.getStyleClass().add("full-text-table");
        return table;
    }

    private <T> TableColumn<T, String> column(String title, Function<T, String> valueFactory, int width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new SimpleStringProperty(valueFactory.apply(data.getValue())));
        column.setPrefWidth(width);
        column.setCellFactory(ignored -> new TableCell<>() {
            private final Label cellLabel = new Label();

            {
                cellLabel.getStyleClass().add("table-cell-wrap-label");
                cellLabel.setWrapText(true);
                cellLabel.maxWidthProperty().bind(widthProperty().subtract(18));
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }
                cellLabel.setText(item);
                setText(null);
                setGraphic(cellLabel);
                setTooltip(fastTooltip(item));
            }
        });
        return column;
    }

    private Label tableDetailLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("table-detail");
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private Tooltip fastTooltip(String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setShowDelay(javafx.util.Duration.millis(30));
        tooltip.setHideDelay(javafx.util.Duration.millis(50));
        tooltip.setShowDuration(javafx.util.Duration.seconds(30));
        return tooltip;
    }

    private Label emptyChartLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("empty-state");
        label.setWrapText(true);
        StackPane.setAlignment(label, Pos.CENTER);
        return label;
    }

    private void bindResponsiveMetricGrid(VBox screen, GridPane grid, List<Node> cards) {
        Runnable updater = () -> updateResponsiveMetricGrid(screen.getWidth(), grid, cards);
        screen.widthProperty().addListener((observable, oldValue, newValue) -> updater.run());
        Platform.runLater(updater);
    }

    private void bindResponsiveChartGrid(VBox screen, GridPane grid, List<Node> cards) {
        Runnable updater = () -> updateResponsiveChartGrid(screen.getWidth(), grid, cards);
        screen.widthProperty().addListener((observable, oldValue, newValue) -> updater.run());
        Platform.runLater(updater);
    }

    private void updateResponsiveMetricGrid(double screenWidth, GridPane grid, List<Node> cards) {
        double availableWidth = Math.max(180, screenWidth - 48);
        int columns;
        if (availableWidth >= 1100) {
            columns = Math.min(6, cards.size());
        } else if (availableWidth >= 760) {
            columns = 3;
        } else if (availableWidth >= 520) {
            columns = 2;
        } else {
            columns = 1;
        }

        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        for (int column = 0; column < columns; column++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(100.0 / columns);
            constraints.setHgrow(Priority.ALWAYS);
            constraints.setFillWidth(true);
            grid.getColumnConstraints().add(constraints);
        }

        for (int index = 0; index < cards.size(); index++) {
            Node card = cards.get(index);
            if (card instanceof Region region) {
                region.setMinWidth(0);
                region.setMaxWidth(Double.MAX_VALUE);
            }
            GridPane.setHgrow(card, Priority.ALWAYS);
            GridPane.setFillWidth(card, true);
            grid.add(card, index % columns, index / columns);
        }
    }

    private void updateResponsiveChartGrid(double screenWidth, GridPane grid, List<Node> cards) {
        double availableWidth = Math.max(180, screenWidth - 48);
        int columns = availableWidth >= 760 ? 2 : 1;

        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        for (int column = 0; column < columns; column++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(100.0 / columns);
            constraints.setHgrow(Priority.ALWAYS);
            constraints.setFillWidth(true);
            grid.getColumnConstraints().add(constraints);
        }

        for (int index = 0; index < cards.size(); index++) {
            Node card = cards.get(index);
            if (card instanceof Region region) {
                region.setMinWidth(0);
                region.setMaxWidth(Double.MAX_VALUE);
            }
            GridPane.setHgrow(card, Priority.ALWAYS);
            GridPane.setFillWidth(card, true);
            grid.add(card, index % columns, index / columns);
        }
    }

    private String score(BigDecimal value) {
        return value == null ? "-" : SCORE_FORMAT.format(value);
    }

    private String valueOrEmpty(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private Spinner<Integer> topLimitSpinner() {
        Spinner<Integer> spinner = new Spinner<>();
        spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                1,
                100,
                DashboardService.DEFAULT_TOP_LIMIT,
                1
        ));
        spinner.setEditable(true);
        spinner.getStyleClass().add("top-limit-spinner");
        spinner.setPrefWidth(92);
        spinner.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue != null && newValue != null && !oldValue.equals(newValue)) {
                refresh(false);
            }
        });
        spinner.getEditor().setOnAction(event -> {
            spinner.getValueFactory().setValue(currentTopLimit(spinner));
            refresh(false);
        });
        spinner.getEditor().focusedProperty().addListener((observable, oldValue, focused) -> {
            if (!focused) {
                spinner.getValueFactory().setValue(currentTopLimit(spinner));
            }
        });
        return spinner;
    }

    private int currentTopOverallLimit() {
        return currentTopLimit(topOverallLimitSpinner);
    }

    private int currentTopAiRiskLimit() {
        return currentTopLimit(topAiRiskLimitSpinner);
    }

    private int currentTopLimit(Spinner<Integer> spinner) {
        if (spinner == null || spinner.getValue() == null) {
            return DashboardService.DEFAULT_TOP_LIMIT;
        }
        if (spinner.isEditable() && spinner.getEditor() != null) {
            try {
                return clampTopLimit(Integer.parseInt(spinner.getEditor().getText().trim()));
            } catch (NumberFormatException ignored) {
                return clampTopLimit(spinner.getValue());
            }
        }
        return clampTopLimit(spinner.getValue());
    }

    private int clampTopLimit(int value) {
        return Math.max(1, Math.min(100, value));
    }

    private void updateTopTableTitles() {
        if (topOverallTitleLabel != null) {
            topOverallTitleLabel.setText(t("dashboard.table.topOverall", currentTopOverallLimit()));
        }
        if (topAiRiskTitleLabel != null) {
            topAiRiskTitleLabel.setText(t("dashboard.table.aiRisk", currentTopAiRiskLimit()));
        }
    }

    private void notify(String message, boolean success) {
        notifier.accept(message, success);
    }

    private String t(String key, Object... args) {
        return i18n.text(key, args);
    }
}
