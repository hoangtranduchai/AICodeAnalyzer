package com.example.aicodeanalyzer.ui.controller;

import com.example.aicodeanalyzer.i18n.I18n;
import com.example.aicodeanalyzer.scheduler.SchedulerConfig;
import com.example.aicodeanalyzer.scheduler.SchedulerManager;
import com.example.aicodeanalyzer.scheduler.SchedulerSettingsService;
import com.example.aicodeanalyzer.scheduler.SchedulerStatus;
import com.example.aicodeanalyzer.model.CrawlLog;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * JavaFX controller for ScheduledExecutorService scheduler settings.
 * Controller JavaFX cho phần cấu hình lịch chạy ScheduledExecutorService.
 */
public class SchedulerSettingsController {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SchedulerManager schedulerManager;
    private final SchedulerSettingsService schedulerSettingsService;
    private final BiConsumer<String, Boolean> notifier;
    private final I18n i18n;

    private ComboBox<String> dailyRunTimeComboBox;
    private CheckBox autoCrawlCheckBox;
    private Label statusLabel;
    private Label lastRunLabel;
    private Label latestResultLabel;

    public SchedulerSettingsController(
            SchedulerManager schedulerManager,
            SchedulerSettingsService schedulerSettingsService,
            BiConsumer<String, Boolean> notifier
    ) {
        this(schedulerManager, schedulerSettingsService, notifier, I18n.createDefault());
    }

    public SchedulerSettingsController(
            SchedulerManager schedulerManager,
            SchedulerSettingsService schedulerSettingsService,
            BiConsumer<String, Boolean> notifier,
            I18n i18n
    ) {
        this.schedulerManager = Objects.requireNonNull(schedulerManager, "schedulerManager must not be null");
        this.schedulerSettingsService = Objects.requireNonNull(
                schedulerSettingsService,
                "schedulerSettingsService must not be null"
        );
        this.notifier = notifier == null ? (message, success) -> { } : notifier;
        this.i18n = Objects.requireNonNull(i18n, "i18n must not be null");
    }

    public Node createView() {
        GridPane apiForm = formGrid();
        addFormRow(apiForm, 0, "AI Provider", readOnlyValue("Gemini REST"));
        addFormRow(apiForm, 1, "Model", readOnlyValue("gemini-2.5-flash"));
        addFormRow(apiForm, 2, "API Key", readOnlyValue(t("settings.api.keySource")));
        addFormRow(apiForm, 3, "Timeout", readOnlyValue(t("settings.timeoutSource")));

        GridPane scheduleForm = formGrid();
        SchedulerConfig persistedConfig = schedulerSettingsService.loadConfig();
        SchedulerStatus status = schedulerManager.status();
        dailyRunTimeComboBox = new ComboBox<>();
        dailyRunTimeComboBox.getItems().setAll(hourOptions());
        dailyRunTimeComboBox.setMaxWidth(Double.MAX_VALUE);
        dailyRunTimeComboBox.getSelectionModel().select(SchedulerConfig.parseTime(
                status.dailyRunTime() == null ? persistedConfig.dailyRunTimeText() : status.dailyRunTime().toString()
        ).format(DateTimeFormatter.ofPattern("HH:mm")));

        autoCrawlCheckBox = new CheckBox(t("settings.auto"));
        autoCrawlCheckBox.setSelected(status.autoCrawlEnabled() || persistedConfig.autoCrawlEnabled());

        statusLabel = readOnlyValue();
        lastRunLabel = readOnlyValue();
        latestResultLabel = readOnlyValue();

        addFormRow(scheduleForm, 0, t("settings.dailyRun"), dailyRunTimeComboBox);
        addFormRow(scheduleForm, 1, t("settings.automation"), autoCrawlCheckBox);
        addFormRow(scheduleForm, 2, t("settings.jobStatus"), statusLabel);
        addFormRow(scheduleForm, 3, t("settings.lastRun"), lastRunLabel);
        addFormRow(scheduleForm, 4, t("settings.latest"), latestResultLabel);
        refreshStatus();

        Button saveScheduleButton = new Button(t("action.saveSchedule"));
        saveScheduleButton.getStyleClass().add("primary-button");
        saveScheduleButton.setWrapText(true);
        saveScheduleButton.setAccessibleText(t("action.saveSchedule"));
        saveScheduleButton.setOnAction(event -> saveSchedule());

        Button refreshButton = new Button(t("action.refreshStatus"));
        refreshButton.getStyleClass().add("secondary-button");
        refreshButton.setWrapText(true);
        refreshButton.setAccessibleText(t("action.refreshStatus"));
        refreshButton.setOnAction(event -> refreshStatus());

        FlowPane actions = actionFlow(saveScheduleButton, refreshButton);

        VBox settings = new VBox(
            stateBanner(t("settings.banner.title"), t("settings.banner.detail")),
                split(card(t("settings.api"), apiForm), card(t("settings.scheduler"), scheduleForm)),
                card(t("settings.docs"), docsPanel()),
                card(t("settings.actions"), actions)
        );
        settings.setSpacing(16);

        VBox screen = new VBox(
            sectionHeader(t("settings.title"), t("settings.subtitle")),
                settings
        );
        screen.setPadding(new Insets(24));
        screen.setSpacing(18);
        screen.setMaxWidth(Double.MAX_VALUE);
        screen.getStyleClass().add("screen");
        return screen;
    }

    private VBox docsPanel() {
        Label install = new Label(t("settings.doc.1"));
        Label bot = new Label(t("settings.doc.2"));
        Label crawl = new Label(t("settings.doc.3"));
        Label theme = new Label(t("settings.doc.4"));
        for (Label label : java.util.List.of(install, bot, crawl, theme)) {
            label.getStyleClass().add("state-detail");
            label.setWrapText(true);
        }
        VBox docs = new VBox(install, bot, crawl, theme);
        docs.setSpacing(8);
        return docs;
    }

    private void saveSchedule() {
        try {
            LocalTime runTime = SchedulerConfig.parseTime(dailyRunTimeComboBox.getValue());
            SchedulerConfig config = new SchedulerConfig(autoCrawlCheckBox.isSelected(), runTime);
            schedulerSettingsService.saveConfig(config);
            schedulerManager.configureDailyCrawl(config.dailyRunTime(), config.autoCrawlEnabled());
            refreshStatus();
            notify(autoCrawlCheckBox.isSelected()
                    ? t("settings.savedOn", config.dailyRunTimeText())
                    : t("settings.savedOff"), true);
        } catch (RuntimeException ex) {
            notify(ex.getMessage(), false);
        }
    }

    private void refreshStatus() {
        SchedulerStatus status = schedulerManager.status();
        statusLabel.setText((status.started() ? t("settings.status.running") : t("settings.status.stopped"))
                + " / " + t("settings.auto") + " " + (status.autoCrawlEnabled() ? t("settings.status.enabled") : t("settings.status.disabled"))
                + " / " + t("settings.dailyRun") + " " + status.dailyRunTime());
        schedulerSettingsService.latestCrawlLog().ifPresentOrElse(this::showLatestLog, () -> {
            lastRunLabel.setText("-");
            latestResultLabel.setText(t("settings.noLog"));
        });
    }

    private void showLatestLog(CrawlLog crawlLog) {
        LocalDateTime runTime = crawlLog.getFinishedAt() == null ? crawlLog.getStartedAt() : crawlLog.getFinishedAt();
        lastRunLabel.setText(runTime == null ? "-" : DATE_TIME_FORMATTER.format(runTime));
        latestResultLabel.setText(crawlLog.getStatus()
                + " / handles=" + crawlLog.getTotalHandles()
                + " / new=" + crawlLog.getTotalNewSubmissions()
                + " / errors=" + crawlLog.getTotalErrors()
                + " / " + safeMessage(crawlLog.getMessage()));
    }

    private java.util.List<String> hourOptions() {
        java.util.List<String> options = new java.util.ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            options.add("%02d:00".formatted(hour));
        }
        return options;
    }

    private String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "-";
        }
        return message.length() > 160 ? message.substring(0, 157) + "..." : message;
    }

    private GridPane formGrid() {
        GridPane gridPane = new GridPane();
        gridPane.setHgap(12);
        gridPane.setVgap(12);
        gridPane.getStyleClass().add("form-grid");
        return gridPane;
    }

    private void addFormRow(GridPane grid, int row, String label, Node control) {
        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("form-label");
        labelNode.setMinWidth(122);
        labelNode.setPrefWidth(142);
        labelNode.setMaxWidth(160);
        grid.add(labelNode, 0, row);
        grid.add(control, 1, row);
        GridPane.setHgrow(control, Priority.ALWAYS);
        if (control instanceof javafx.scene.layout.Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
    }

    private Label readOnlyValue() {
        return readOnlyValue("-");
    }

    private Label readOnlyValue(String value) {
        Label label = new Label(value);
        label.getStyleClass().add("read-only-value");
        label.setMaxWidth(Double.MAX_VALUE);
        label.setWrapText(true);
        return label;
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

    private GridPane split(Node left, Node right) {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);
        grid.setMaxWidth(Double.MAX_VALUE);
        grid.getStyleClass().add("responsive-split");

        java.util.List<Node> nodes = java.util.List.of(left, right);
        Runnable updater = () -> {
            double width = grid.getWidth() <= 0 ? 1200 : grid.getWidth();
            int columns = width >= 760 ? 2 : 1;
            grid.getChildren().clear();
            grid.getColumnConstraints().clear();
            for (int column = 0; column < columns; column++) {
                ColumnConstraints constraints = new ColumnConstraints();
                constraints.setPercentWidth(100.0 / columns);
                constraints.setHgrow(Priority.ALWAYS);
                constraints.setFillWidth(true);
                grid.getColumnConstraints().add(constraints);
            }
            for (int index = 0; index < nodes.size(); index++) {
                Node node = nodes.get(index);
                if (node instanceof Region region) {
                    region.setMinWidth(0);
                    region.setMaxWidth(Double.MAX_VALUE);
                }
                GridPane.setHgrow(node, Priority.ALWAYS);
                GridPane.setFillWidth(node, true);
                grid.add(node, index % columns, index / columns);
            }
        };
        grid.widthProperty().addListener((observable, oldValue, newValue) -> updater.run());
        Platform.runLater(updater);
        return grid;
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

    private void notify(String message, boolean success) {
        notifier.accept(message, success);
    }

    private String t(String key, Object... args) {
        return i18n.text(key, args);
    }
}
