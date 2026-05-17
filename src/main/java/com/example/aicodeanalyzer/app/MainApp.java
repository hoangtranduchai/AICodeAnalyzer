package com.example.aicodeanalyzer.app;

import com.example.aicodeanalyzer.config.AiConfig;
import com.example.aicodeanalyzer.config.ConnectionTestResult;
import com.example.aicodeanalyzer.config.DatabaseConnectionFactory;
import com.example.aicodeanalyzer.model.AiAnalysisResult;
import com.example.aicodeanalyzer.model.HandleAccount;
import com.example.aicodeanalyzer.model.Platform;
import com.example.aicodeanalyzer.model.SkillScore;
import com.example.aicodeanalyzer.i18n.I18n;
import com.example.aicodeanalyzer.report.ReportExportResult;
import com.example.aicodeanalyzer.report.ReportRequest;
import com.example.aicodeanalyzer.scheduler.SchedulerManager;
import com.example.aicodeanalyzer.ui.controller.DashboardController;
import com.example.aicodeanalyzer.ui.controller.HandleController;
import com.example.aicodeanalyzer.ui.controller.MainShellController;
import com.example.aicodeanalyzer.ui.controller.SchedulerSettingsController;
import com.example.aicodeanalyzer.ui.controller.SourceCodeDetailController;
import com.example.aicodeanalyzer.util.SecretUtils;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.fxml.FXMLLoader;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.Desktop;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * JavaFX entry point. Builds the first functional desktop UI shell for the project.
 * Điểm vào JavaFX. Dựng khung giao diện desktop chính cho dự án.
 */
public class MainApp extends Application {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int ANALYSIS_SCREEN_LIMIT = 500;
    private static final int SKILL_SCREEN_LIMIT = 500;

    private final I18n i18n = I18n.createDefault();
    private final ApplicationContext applicationContext = new ApplicationContext();
    private final List<Button> navigationButtons = new ArrayList<>();
    private final Map<String, ViewFactory> viewFactoriesByScreen = new LinkedHashMap<>();
    private final SchedulerManager schedulerManager = applicationContext.schedulerManager();

    private BorderPane root;
    private StackPane contentStack;
    private VBox sidebar;
    private Button forceCrawlButton;
    private Button workspaceCrawlButton;
    private Button initializeBotButton;
    private Button checkChromeButton;
    private TextArea workflowConsoleArea;
    private HandleController workspaceHandleController;
    private Label brandLabel;
    private Label brandSubtitleLabel;
    private Label footerDbLabel;
    private Label footerAiLabel;
    private Label footerChromeLabel;
    private Label workspaceChromeLabel;
    private Label footerWorkflowLabel;
    private Label footerUnlimitedLabel;
    private HBox notificationBar;
    private Label notificationText;
    private StackPane loadingOverlay;
    private Label loadingLabel;
    private PauseTransition notificationPause;
    private PauseTransition crawlButtonPause;
    private final List<String> workflowConsoleLines = new ArrayList<>();
    private Button themeToggleButton;
    private Button languageToggleButton;
    private Button closeNotificationButton;
    private boolean darkTheme = true;
    private String currentScreenId = "dashboard";
    private String footerDbStatusKey = "footer.dbChecking";
    private String footerDbStyleClass = "info-pill";
    private String footerDbTooltip = "";
    private String chromeStatusKey = "footer.chromeChecking";
    private String chromeStatusStyleClass = "info-pill";
    private String chromeStatusTooltip = "";
    private boolean shutdownRequested;

    @Override
    public void start(Stage stage) {
        schedulerManager.start();
        schedulerManager.addWorkflowListener(this::appendWorkflowLog);
        applySavedSchedulerConfig();

        MainShellController shellController = loadMainShell();
        root = shellController.root();
        root.getStyleClass().addAll("app-root", "dark-theme");

        contentStack = new StackPane();
        contentStack.getStyleClass().add("content-stack");
        loadingOverlay = buildLoadingOverlay();

        sidebar = buildSidebar();
        shellController.setSidebar(wrapSidebar(sidebar));
        shellController.setContent(contentStack);

        showScreen("dashboard", buildWorkspaceView(), false);

        Scene scene = new Scene(root, 1280, 780);
        var stylesheet = MainApp.class.getResource("/css/app.css");
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }

        stage.setTitle(t("app.title"));
        stage.setMinWidth(520);
        stage.setMinHeight(560);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> shutdownAndExit());
        applyResponsiveSidebar(stage.getWidth());
        stage.widthProperty().addListener((observable, oldValue, newValue) -> applyResponsiveSidebar(newValue.doubleValue()));
        stage.setMaximized(true);
        stage.show();
    }

    private MainShellController loadMainShell() {
        try {
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("/fxml/main-shell.fxml"));
            loader.load();
            return loader.getController();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load main-shell.fxml.", ex);
        }
    }

    private Button buildForceCrawlButton() {
        forceCrawlButton = new Button(t("action.forceCrawl"));
        forceCrawlButton.getStyleClass().add("primary-button");
        forceCrawlButton.getStyleClass().add("sidebar-action-button");
        forceCrawlButton.setMaxWidth(Double.MAX_VALUE);
        forceCrawlButton.setWrapText(true);
        forceCrawlButton.setTooltip(new Tooltip(t("action.forceCrawl")));
        forceCrawlButton.setOnAction(event -> queueFullCrawlWorkflow());
        return forceCrawlButton;
    }

    private void queueFullCrawlWorkflow() {
        setCrawlButtonsBusy(t("workspace.bot.checking"));
        appendWorkflowLog(t("workspace.console.checkingChrome"));
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                return applicationContext.crawlService().isVisibleBotBrowserReady();
            }
        };
        task.setOnSucceeded(event -> {
            boolean ready = Boolean.TRUE.equals(task.getValue());
            if (!ready) {
                restoreCrawlButtonsState();
                appendWorkflowLog(t("workspace.console.chromeMissing"));
                initializeBotBrowserFromWorkspace(true);
                return;
            }
            appendWorkflowLog(t("workspace.console.chromeReady"));
            try {
                setCrawlButtonsBusy(t("sidebar.crawlQueueing"));
                appendWorkflowLog(t("workspace.console.queueing"));
                boolean queued = schedulerManager.triggerCrawlNow();
                applyChromeStatus(true);
                if (queued) {
                    showNotification(t("notification.forceCrawlQueued"), true);
                    appendWorkflowLog(t("workspace.console.queued"));
                } else {
                    showNotification(t("notification.workflowAlreadyRunning"), false);
                    appendWorkflowLog(t("workspace.console.alreadyRunning"));
                }
                scheduleCrawlButtonRestore();
            } catch (RuntimeException ex) {
                restoreCrawlButtonsState();
                appendWorkflowLog(t("workspace.console.queueFailed", SecretUtils.sanitizeMessage(ex.getMessage())));
                showNotification(t("sidebar.crawlError", ex.getMessage()), false);
            }
        });
        task.setOnFailed(event -> {
            restoreCrawlButtonsState();
            Throwable exception = task.getException();
            String message = exception == null ? "" : SecretUtils.sanitizeMessage(exception.getMessage());
            applyChromeStatus(false, message);
            appendWorkflowLog(t("workspace.console.chromeCheckFailed", message));
            showNotification(t("notification.chromeCheckFailed", message), false);
        });
        Thread thread = new Thread(task, "crawl-preflight-chrome-check");
        thread.setDaemon(true);
        thread.start();
    }

    private HBox buildModeActions() {
        themeToggleButton = new Button(t("theme.dark"));
        themeToggleButton.getStyleClass().add("secondary-button");
        themeToggleButton.getStyleClass().add("sidebar-mode-button");
        themeToggleButton.setMaxWidth(Double.MAX_VALUE);
        themeToggleButton.setWrapText(true);
        themeToggleButton.setTooltip(new Tooltip(t("theme.tooltip")));
        themeToggleButton.setOnAction(event -> toggleTheme());

        languageToggleButton = new Button(i18n.switchTargetText());
        languageToggleButton.getStyleClass().add("secondary-button");
        languageToggleButton.getStyleClass().add("sidebar-mode-button");
        languageToggleButton.setMaxWidth(Double.MAX_VALUE);
        languageToggleButton.setWrapText(true);
        languageToggleButton.setTooltip(new Tooltip(t("language.tooltip")));
        languageToggleButton.setOnAction(event -> toggleLanguage());

        HBox actions = new HBox(themeToggleButton, languageToggleButton);
        actions.setSpacing(8);
        actions.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(themeToggleButton, Priority.ALWAYS);
        HBox.setHgrow(languageToggleButton, Priority.ALWAYS);
        return actions;
    }

    @Override
    public void stop() {
        shutdownApplicationServices();
    }

    private void shutdownAndExit() {
        shutdownApplicationServices();
        javafx.application.Platform.exit();
        System.exit(0);
    }

    private void shutdownApplicationServices() {
        if (shutdownRequested) {
            return;
        }
        shutdownRequested = true;
        schedulerManager.removeWorkflowListener(this::appendWorkflowLog);
        schedulerManager.shutdown();
    }

    private VBox buildSidebar() {
        brandLabel = new Label(t("app.title"));
        brandLabel.getStyleClass().add("brand-label");
        brandLabel.setWrapText(true);

        brandSubtitleLabel = new Label(t("app.subtitle"));
        brandSubtitleLabel.getStyleClass().add("brand-subtitle");
        brandSubtitleLabel.setWrapText(true);
        brandSubtitleLabel.setMinHeight(Region.USE_PREF_SIZE);

        VBox brandBox = new VBox(brandLabel, brandSubtitleLabel);
        brandBox.getStyleClass().add("sidebar-brand");
        brandBox.setSpacing(2);
        brandBox.setPadding(new Insets(0, 0, 12, 0));

        VBox nav = new VBox();
        nav.getStyleClass().add("sidebar-nav");
        nav.setSpacing(6);
        nav.getChildren().addAll(
                navButton("dashboard", "nav.dashboard", () -> buildWorkspaceView()),
                navButton("analysis", "nav.analysis", () -> buildAnalysisWorkspaceView()),
                navButton("evaluation", "nav.evaluation", () -> buildEvaluationReportView()),
                navButton("settings", "nav.settings", () -> buildSettingsView())
        );
        if (!navigationButtons.isEmpty()) {
            navigationButtons.get(0).getStyleClass().add("nav-button-active");
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        footerDbLabel = statusPill(t("footer.dbChecking"), "info-pill");
        footerAiLabel = statusPill(t("footer.aiChecking"), "info-pill");
        footerChromeLabel = statusPill(t("footer.chromeChecking"), "info-pill");
        footerWorkflowLabel = statusPill(t("footer.workflow"), "info-pill");
        footerUnlimitedLabel = statusPill(t("footer.unlimited"), "status-pill-ok");
        VBox footer = new VBox(
                buildForceCrawlButton(),
                buildModeActions(),
                new Separator(),
                footerDbLabel,
                footerAiLabel,
                footerChromeLabel,
                footerWorkflowLabel,
                footerUnlimitedLabel
        );
        footer.getStyleClass().add("sidebar-footer");
        footer.setSpacing(8);

        VBox builtSidebar = new VBox(brandBox, new Separator(), nav, spacer, footer);
        builtSidebar.setPadding(new Insets(18, 14, 18, 14));
        builtSidebar.setSpacing(12);
        builtSidebar.getStyleClass().add("side-nav");
        refreshSidebarReadiness();
        return builtSidebar;
    }

    private ScrollPane wrapSidebar(VBox sidebarContent) {
        ScrollPane scrollPane = new ScrollPane(sidebarContent);
        scrollPane.getStyleClass().add("side-nav-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.viewportBoundsProperty().addListener((observable, oldBounds, bounds) -> {
            sidebarContent.setMinHeight(bounds.getHeight());
            sidebarContent.setMaxHeight(Double.MAX_VALUE);
        });
        return scrollPane;
    }

    private void refreshSidebarReadiness() {
        updateAiConfigStatus();
        refreshDbConfigStatusAsync();
        refreshChromeStatusAsync(false);
    }

    /*
     * EN: Sidebar readiness pills are intentionally lightweight and do not alter app logic.
     * VI: Các pill trạng thái sidebar chỉ hiển thị tình trạng, không đổi logic ứng dụng.
     */
    private void updateAiConfigStatus() {
        if (footerAiLabel == null) {
            return;
        }
        try {
            AiConfig config = AiConfig.load();
            if (config.mockMode()) {
                applySidebarStatus(
                        footerAiLabel,
                        "footer.aiMock",
                        "status-pill-warn",
                        t("footer.aiMock.tooltip")
                );
            } else if (!config.hasApiKey()) {
                applySidebarStatus(
                        footerAiLabel,
                        "footer.aiMissing",
                        "status-pill-warn",
                        t("footer.aiMissing.tooltip")
                );
            } else {
                applySidebarStatus(
                        footerAiLabel,
                        "footer.aiReady",
                        "success-pill",
                        t("footer.aiReady.tooltip", config.model())
                );
            }
        } catch (RuntimeException ex) {
            applySidebarStatus(
                    footerAiLabel,
                    "footer.aiError",
                    "status-pill-error",
                    SecretUtils.sanitizeMessage(ex.getMessage())
            );
        }
    }

    private void refreshDbConfigStatusAsync() {
        if (footerDbLabel == null) {
            return;
        }
        setDbStatus("footer.dbChecking", "info-pill", t("footer.dbChecking.tooltip"));
        Task<ConnectionTestResult> task = new Task<>() {
            @Override
            protected ConnectionTestResult call() {
                return DatabaseConnectionFactory.fromApplicationProperties().testConnection();
            }
        };
        task.setOnSucceeded(event -> {
            ConnectionTestResult result = task.getValue();
            String message = result == null ? "" : SecretUtils.sanitizeMessage(result.message());
            if (result != null && result.success()) {
                setDbStatus("footer.dbReady", "success-pill", message);
            } else {
                setDbStatus(
                        looksLikeMissingConfig(message) ? "footer.dbMissing" : "footer.dbError",
                        looksLikeMissingConfig(message) ? "status-pill-warn" : "status-pill-error",
                        message
                );
            }
        });
        task.setOnFailed(event -> {
            Throwable exception = task.getException();
            String message = exception == null ? "" : SecretUtils.sanitizeMessage(exception.getMessage());
            setDbStatus(
                    looksLikeMissingConfig(message) ? "footer.dbMissing" : "footer.dbError",
                    looksLikeMissingConfig(message) ? "status-pill-warn" : "status-pill-error",
                    message
            );
        });
        Thread thread = new Thread(task, "sidebar-db-readiness-check");
        thread.setDaemon(true);
        thread.start();
    }

    private void setDbStatus(String key, String styleClass, String tooltip) {
        footerDbStatusKey = key;
        footerDbStyleClass = styleClass;
        footerDbTooltip = tooltip == null ? "" : tooltip;
        applySidebarStatus(footerDbLabel, key, styleClass, footerDbTooltip);
    }

    private void refreshChromeStatusAsync(boolean notify) {
        if (footerChromeLabel == null && workspaceChromeLabel == null) {
            return;
        }
        applyChromeStatus("footer.chromeChecking", "info-pill", t("footer.chromeChecking.tooltip"));
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                return applicationContext.crawlService().isVisibleBotBrowserReady();
            }
        };
        task.setOnSucceeded(event -> {
            boolean ready = Boolean.TRUE.equals(task.getValue());
            applyChromeStatus(ready);
            if (ready) {
                appendWorkflowLog(t("workspace.console.chromeReady"));
            } else if (notify) {
                appendWorkflowLog(t("workspace.console.chromeStillMissing"));
            }
            if (notify) {
                showNotification(t(ready ? "notification.chromeReady" : "notification.chromeMissing"), ready);
            }
        });
        task.setOnFailed(event -> {
            Throwable exception = task.getException();
            String message = exception == null ? "" : SecretUtils.sanitizeMessage(exception.getMessage());
            applyChromeStatus(false, message);
            if (notify) {
                showNotification(t("notification.chromeCheckFailed", message), false);
            }
        });
        Thread thread = new Thread(task, "sidebar-chrome-readiness-check");
        thread.setDaemon(true);
        thread.start();
    }

    private void applyChromeStatus(boolean ready) {
        applyChromeStatus(
                ready ? "footer.chromeReady" : "footer.chromeMissing",
                ready ? "success-pill" : "status-pill-warn",
                t(ready ? "footer.chromeReady.tooltip" : "footer.chromeMissing.tooltip")
        );
    }

    private void applyChromeStatus(boolean ready, String detail) {
        applyChromeStatus(
                ready ? "footer.chromeReady" : "footer.chromeMissing",
                ready ? "success-pill" : "status-pill-warn",
                hasText(detail) ? detail : t(ready ? "footer.chromeReady.tooltip" : "footer.chromeMissing.tooltip")
        );
    }

    private void applyChromeStatus(String key, String styleClass, String tooltip) {
        chromeStatusKey = key;
        chromeStatusStyleClass = styleClass;
        chromeStatusTooltip = tooltip == null ? "" : tooltip;
        applySidebarStatus(footerChromeLabel, key, styleClass, chromeStatusTooltip);
        applySidebarStatus(workspaceChromeLabel, key, styleClass, chromeStatusTooltip);
    }

    private void applySidebarStatus(Label label, String key, String styleClass, String tooltip) {
        if (label == null) {
            return;
        }
        label.getStyleClass().removeAll(
                "success-pill",
                "status-pill-ok",
                "info-pill",
                "status-pill-warn",
                "status-pill-error"
        );
        label.getStyleClass().add(styleClass);
        label.setText(t(key));
        label.setTooltip(new Tooltip(hasText(tooltip) ? tooltip : t(key)));
    }

    private boolean looksLikeMissingConfig(String message) {
        if (!hasText(message)) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("required")
                || normalized.contains("not set")
                || normalized.contains("chưa cấu hình")
                || normalized.contains("application.properties")
                || normalized.contains("environment variable");
    }

    private void setCrawlButtonQueuedState() {
        setCrawlButtonsBusy(t("sidebar.crawlQueueing"));
        scheduleCrawlButtonRestore();
    }

    private void setCrawlButtonsBusy(String text) {
        if (crawlButtonPause != null) {
            crawlButtonPause.stop();
        }
        if (forceCrawlButton != null) {
            forceCrawlButton.setText(text);
            forceCrawlButton.setDisable(true);
        }
        if (workspaceCrawlButton != null) {
            workspaceCrawlButton.setText(text);
            workspaceCrawlButton.setDisable(true);
        }
    }

    private void scheduleCrawlButtonRestore() {
        crawlButtonPause = new PauseTransition(Duration.seconds(1.8));
        crawlButtonPause.setOnFinished(event -> restoreCrawlButtonsState());
        crawlButtonPause.play();
    }

    private void restoreCrawlButtonState() {
        restoreCrawlButtonsState();
    }

    private void restoreCrawlButtonsState() {
        if (forceCrawlButton == null) {
            // Workspace-only contexts still need to recover their button state.
        } else {
            forceCrawlButton.setDisable(false);
            forceCrawlButton.setText(t("action.forceCrawl"));
        }
        if (workspaceCrawlButton != null) {
            workspaceCrawlButton.setDisable(false);
            workspaceCrawlButton.setText(t("action.forceCrawl"));
        }
    }

    private void applyResponsiveSidebar(double stageWidth) {
        if (sidebar == null) {
            return;
        }
        sidebar.getStyleClass().removeAll("sidebar-mobile", "sidebar-compact", "sidebar-tablet", "sidebar-desktop");
        double width;
        if (stageWidth < 640) {
            width = 176;
            sidebar.getStyleClass().add("sidebar-mobile");
            sidebar.getStyleClass().add("sidebar-compact");
        } else if (stageWidth < 820) {
            width = 196;
            sidebar.getStyleClass().add("sidebar-compact");
        } else if (stageWidth < 1120) {
            width = 220;
            sidebar.getStyleClass().add("sidebar-tablet");
        } else {
            width = 252;
            sidebar.getStyleClass().add("sidebar-desktop");
        }
        sidebar.setMinWidth(width);
        sidebar.setPrefWidth(width);
        sidebar.setMaxWidth(width);
        double contentWidth = Math.max(132, width - 32);
        brandLabel.setMaxWidth(contentWidth);
        brandSubtitleLabel.setMaxWidth(contentWidth);
        for (Button button : navigationButtons) {
            button.setMaxWidth(Double.MAX_VALUE);
        }
        applyMobileSidebarState(stageWidth < 640);
    }

    private void applyMobileSidebarState(boolean mobile) {
        setManagedVisible(footerChromeLabel, !mobile);
        setManagedVisible(footerWorkflowLabel, !mobile);
        setManagedVisible(footerUnlimitedLabel, !mobile);
    }

    private void setManagedVisible(Node node, boolean visible) {
        if (node == null) {
            return;
        }
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private void toggleTheme() {
        darkTheme = !darkTheme;
        root.getStyleClass().removeAll("dark-theme", "light-theme");
        root.getStyleClass().add(darkTheme ? "dark-theme" : "light-theme");
        updateStaticTexts();
        showNotification(t("notification.themeChanged", t(darkTheme ? "theme.dark" : "theme.light")), true);
    }

    private void toggleLanguage() {
        i18n.toggle();
        updateStaticTexts();
        ViewFactory factory = viewFactoriesByScreen.get(currentScreenId);
        if (factory != null) {
            showScreen(currentScreenId, factory.create(), false);
            updateActiveNavigation();
        }
        showNotification(t("notification.languageChanged", i18n.language().displayName()), true);
    }

    /*
     * EN: Static shell text is refreshed here; screen-specific content is rebuilt by the view factory.
     * VI: Text tĩnh của khung app được cập nhật ở đây; nội dung từng màn hình được dựng lại bằng view factory.
     */
    private void updateStaticTexts() {
        if (forceCrawlButton != null) {
            forceCrawlButton.setText(t("action.forceCrawl"));
            forceCrawlButton.setTooltip(new Tooltip(t("action.forceCrawl")));
        }
        if (workspaceCrawlButton != null) {
            workspaceCrawlButton.setText(t("action.forceCrawl"));
            workspaceCrawlButton.setTooltip(new Tooltip(t("workspace.primaryAction.detail")));
        }
        if (initializeBotButton != null) {
            initializeBotButton.setText(t("action.initializeBot"));
            initializeBotButton.setTooltip(new Tooltip(t("workspace.bot.detail")));
        }
        if (checkChromeButton != null) {
            checkChromeButton.setText(t("action.checkChrome"));
            checkChromeButton.setTooltip(new Tooltip(t("footer.chromeMissing.tooltip")));
        }
        if (themeToggleButton != null) {
            themeToggleButton.setText(t(darkTheme ? "theme.dark" : "theme.light"));
            themeToggleButton.setTooltip(new Tooltip(t("theme.tooltip")));
        }
        if (languageToggleButton != null) {
            languageToggleButton.setText(i18n.switchTargetText());
            languageToggleButton.setTooltip(new Tooltip(t("language.tooltip")));
        }
        if (brandLabel != null) {
            brandLabel.setText(t("app.title"));
        }
        if (brandSubtitleLabel != null) {
            brandSubtitleLabel.setText(t("app.subtitle"));
        }
        if (footerDbLabel != null) {
            applySidebarStatus(footerDbLabel, footerDbStatusKey, footerDbStyleClass, footerDbTooltip);
        }
        updateAiConfigStatus();
        applyChromeStatus(chromeStatusKey, chromeStatusStyleClass, chromeStatusTooltip);
        if (footerWorkflowLabel != null) {
            footerWorkflowLabel.setText(t("footer.workflow"));
        }
        if (footerUnlimitedLabel != null) {
            footerUnlimitedLabel.setText(t("footer.unlimited"));
        }
        if (loadingLabel != null) {
            loadingLabel.setText(t("app.loading"));
        }
        if (closeNotificationButton != null) {
            closeNotificationButton.setTooltip(new Tooltip(t("notification.close")));
        }
        for (Button button : navigationButtons) {
            Object labelKey = button.getProperties().get("labelKey");
            if (labelKey instanceof String key) {
                button.setText(t(key));
            }
        }
    }

    private String t(String key, Object... args) {
        return i18n.text(key, args);
    }

    private Button navButton(String screenId, String labelKey, ViewFactory factory) {
        viewFactoriesByScreen.put(screenId, factory);
        Button button = new Button(t(labelKey));
        button.getProperties().put("screenId", screenId);
        button.getProperties().put("labelKey", labelKey);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setWrapText(true);
        button.setTextOverrun(OverrunStyle.CLIP);
        button.getStyleClass().add("nav-button");
        button.setOnAction(event -> navigateTo(screenId, factory.create()));
        navigationButtons.add(button);
        return button;
    }

    private void navigateTo(String screenId, Node view) {
        showScreen(screenId, view, true);
        updateActiveNavigation();
    }

    private void updateActiveNavigation() {
        for (Button button : navigationButtons) {
            button.getStyleClass().remove("nav-button-active");
            if (Objects.equals(button.getProperties().get("screenId"), currentScreenId)) {
                button.getStyleClass().add("nav-button-active");
            }
        }
    }

    private void showScreen(String screenId, Node view, boolean showTransition) {
        currentScreenId = screenId;

        ScrollPane wrappedView = wrapView(view);
        contentStack.getChildren().setAll(wrappedView, buildNotificationBar(), loadingOverlay);
        loadingOverlay.setVisible(false);

        if (showTransition) {
            showLoading();
            animateView(wrappedView);
        }
    }

    private ScrollPane wrapView(Node view) {
        ScrollPane scrollPane = new ScrollPane(view);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("screen-scroll");
        return scrollPane;
    }

    private HBox buildNotificationBar() {
        notificationText = new Label();
        notificationText.getStyleClass().add("notification-text");
        notificationText.setWrapText(true);
        notificationText.setMaxWidth(340);
        notificationText.setTextOverrun(OverrunStyle.ELLIPSIS);

        closeNotificationButton = new Button("x");
        closeNotificationButton.getStyleClass().add("icon-button");
        closeNotificationButton.setTooltip(new Tooltip(t("notification.close")));
        closeNotificationButton.setOnAction(event -> {
            notificationBar.setVisible(false);
            notificationBar.setManaged(false);
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        notificationBar = new HBox(notificationText, spacer, closeNotificationButton);
        notificationBar.setAlignment(Pos.CENTER_LEFT);
        notificationBar.setPadding(new Insets(9, 12, 9, 12));
        notificationBar.setMinWidth(280);
        notificationBar.setPrefWidth(420);
        notificationBar.setMaxWidth(440);
        notificationBar.setMinHeight(40);
        notificationBar.setPrefHeight(Region.USE_COMPUTED_SIZE);
        notificationBar.setMaxHeight(84);
        notificationBar.getStyleClass().add("notification-success");
        notificationBar.setVisible(false);
        notificationBar.setManaged(false);
        StackPane.setAlignment(notificationBar, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(notificationBar, new Insets(0, 18, 18, 0));
        return notificationBar;
    }

    private StackPane buildLoadingOverlay() {
        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setMaxSize(48, 48);

        loadingLabel = new Label(t("app.loading"));
        loadingLabel.getStyleClass().add("loading-label");

        VBox box = new VBox(indicator, loadingLabel);
        box.setAlignment(Pos.CENTER);
        box.setSpacing(12);
        box.getStyleClass().add("loading-box");

        StackPane overlay = new StackPane(box);
        overlay.getStyleClass().add("loading-overlay");
        overlay.setVisible(false);
        return overlay;
    }

    private void showLoading() {
        loadingOverlay.setVisible(true);
        PauseTransition pause = new PauseTransition(Duration.millis(450));
        pause.setOnFinished(event -> loadingOverlay.setVisible(false));
        pause.play();
    }

    private void animateView(Node view) {
        if (view == null) {
            return;
        }
        FadeTransition fade = new FadeTransition(Duration.millis(260), view);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);

        TranslateTransition slide = new TranslateTransition(Duration.millis(260), view);
        slide.setFromY(8);
        slide.setToY(0);

        new ParallelTransition(fade, slide).play();
    }

    private void showNotification(String message, boolean success) {
        notificationBar.getStyleClass().removeAll("notification-success", "notification-error");
        notificationBar.getStyleClass().add(success ? "notification-success" : "notification-error");
        notificationText.setText(SecretUtils.sanitizeMessage(message));
        notificationBar.setVisible(true);
        notificationBar.setManaged(true);

        if (notificationPause != null) {
            notificationPause.stop();
        }
        notificationPause = new PauseTransition(Duration.seconds(success ? 3 : 6));
        notificationPause.setOnFinished(event -> {
            notificationBar.setVisible(false);
            notificationBar.setManaged(false);
        });
        notificationPause.play();
    }

    private Node buildWorkspaceView() {
        try {
            DashboardController controller = new DashboardController(
                    applicationContext.dashboardService(),
                    (message, success) -> showNotification(message, success),
                    i18n
            );
            workspaceHandleController = new HandleController(
                    applicationContext.handleAccountService(),
                    applicationContext.crawlService(),
                    (message, success) -> showNotification(message, success),
                    i18n
            );
            return screen(
                    sectionHeader(t("workspace.title"), t("workspace.subtitle")),
                    buildWorkspaceActionCard(),
                    buildWorkspaceConsoleCard(),
                    workspaceHandleController.createWorkspacePanel(),
                    controller.createWorkspacePanel()
            );
        } catch (RuntimeException ex) {
            return screen(
                    sectionHeader(t("workspace.title"), t("workspace.subtitle")),
                    errorCard(t("error.dashboard"), ex)
            );
        }
    }

    private Node buildWorkspaceConsoleCard() {
        Label title = new Label(t("workspace.console.title"));
        title.getStyleClass().add("card-title");

        Label detail = new Label(t("workspace.console.detail"));
        detail.getStyleClass().add("state-detail");
        detail.setWrapText(true);

        workflowConsoleArea = new TextArea();
        workflowConsoleArea.setEditable(false);
        workflowConsoleArea.setWrapText(true);
        workflowConsoleArea.setPrefRowCount(12);
        workflowConsoleArea.setMinHeight(240);
        workflowConsoleArea.setMaxWidth(Double.MAX_VALUE);
        workflowConsoleArea.getStyleClass().addAll("workflow-console", "command-field");

        if (workflowConsoleLines.isEmpty()) {
            appendWorkflowLog(t("workspace.console.ready"));
        } else {
            workflowConsoleArea.setText(String.join(System.lineSeparator(), workflowConsoleLines));
            workflowConsoleArea.positionCaret(workflowConsoleArea.getText().length());
        }

        Button clearButton = new Button(t("action.clearConsole"));
        clearButton.getStyleClass().add("secondary-button");
        clearButton.setOnAction(event -> {
            workflowConsoleLines.clear();
            appendWorkflowLog(t("workspace.console.cleared"));
        });

        HBox header = new HBox(new VBox(title, detail), clearButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setSpacing(12);
        HBox.setHgrow(header.getChildren().get(0), Priority.ALWAYS);

        VBox card = new VBox(header, workflowConsoleArea);
        card.setSpacing(12);
        card.setPadding(new Insets(16));
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().addAll("card", "workflow-console-card");
        return card;
    }

    private void appendWorkflowLog(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        Runnable update = () -> {
            String line = message;
            if (!message.matches("^\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}.*")) {
                line = java.time.LocalDateTime.now()
                        .truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
                        .format(LOG_TIME_FORMATTER)
                        + " | " + message;
            }
            workflowConsoleLines.add(line);
            while (workflowConsoleLines.size() > 200) {
                workflowConsoleLines.remove(0);
            }
            if (workflowConsoleArea != null) {
                workflowConsoleArea.setText(String.join(System.lineSeparator(), workflowConsoleLines));
                workflowConsoleArea.positionCaret(workflowConsoleArea.getText().length());
            }
            if (line.contains("Finished MANUAL workflow") || line.contains("Finished SCHEDULED workflow")) {
                refreshWorkspaceHandleTable();
            }
        };
        if (javafx.application.Platform.isFxApplicationThread()) {
            update.run();
        } else {
            javafx.application.Platform.runLater(update);
        }
    }

    private void refreshWorkspaceHandleTable() {
        if (workspaceHandleController == null) {
            return;
        }
        try {
            workspaceHandleController.refreshHandles();
        } catch (RuntimeException ignored) {
            // The console should never fail because a table refresh hit a transient database error.
        }
    }

    private Node buildWorkspaceActionCard() {
        Label title = new Label(t("workspace.primaryAction.title"));
        title.getStyleClass().add("state-title");

        Label detail = new Label(t("workspace.primaryAction.detail"));
        detail.getStyleClass().add("state-detail");
        detail.setWrapText(true);

        VBox copy = new VBox(title, detail);
        copy.setSpacing(4);
        copy.setMinWidth(0);
        copy.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(copy, Priority.ALWAYS);

        workspaceCrawlButton = new Button(t("action.forceCrawl"));
        workspaceCrawlButton.getStyleClass().add("primary-button");
        workspaceCrawlButton.setWrapText(true);
        workspaceCrawlButton.setTooltip(new Tooltip(t("workspace.primaryAction.detail")));
        workspaceCrawlButton.setOnAction(event -> queueFullCrawlWorkflow());

        workspaceChromeLabel = statusPill(t(chromeStatusKey), chromeStatusStyleClass);
        workspaceChromeLabel.setTooltip(new Tooltip(hasText(chromeStatusTooltip) ? chromeStatusTooltip : t(chromeStatusKey)));

        initializeBotButton = new Button(t("action.initializeBot"));
        initializeBotButton.getStyleClass().add("secondary-button");
        initializeBotButton.setWrapText(true);
        initializeBotButton.setTooltip(new Tooltip(t("workspace.bot.detail")));
        initializeBotButton.setOnAction(event -> initializeBotBrowserFromWorkspace(false));

        checkChromeButton = new Button(t("action.checkChrome"));
        checkChromeButton.getStyleClass().add("secondary-button");
        checkChromeButton.setWrapText(true);
        checkChromeButton.setTooltip(new Tooltip(t("footer.chromeMissing.tooltip")));
        checkChromeButton.setOnAction(event -> refreshChromeStatusAsync(true));

        HBox primaryRow = new HBox(copy, workspaceCrawlButton);
        primaryRow.setSpacing(16);
        primaryRow.setAlignment(Pos.CENTER_LEFT);

        Label botTitle = new Label(t("workspace.bot.title"));
        botTitle.getStyleClass().add("state-title");

        Label botDetail = new Label(t("workspace.bot.detail"));
        botDetail.getStyleClass().add("state-detail");
        botDetail.setWrapText(true);

        VBox botCopy = new VBox(botTitle, botDetail);
        botCopy.setSpacing(4);
        botCopy.setMinWidth(0);
        HBox.setHgrow(botCopy, Priority.ALWAYS);

        HBox botActions = new HBox(workspaceChromeLabel, initializeBotButton, checkChromeButton);
        botActions.setSpacing(8);
        botActions.setAlignment(Pos.CENTER_LEFT);

        HBox botRow = new HBox(botCopy, botActions);
        botRow.setSpacing(16);
        botRow.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(primaryRow, new Separator(), botRow);
        box.setSpacing(14);
        box.setPadding(new Insets(16));
        box.setMaxWidth(Double.MAX_VALUE);
        box.getStyleClass().addAll("card", "workspace-action-card");
        refreshChromeStatusAsync(false);
        return box;
    }

    private VBox buildBotCommandBox() {
        Label title = new Label(t("workspace.command.title"));
        title.getStyleClass().add("state-title");

        Label detail = new Label(t("workspace.command.detail"));
        detail.getStyleClass().add("state-detail");
        detail.setWrapText(true);

        TextArea visibleCommand = commandArea(applicationContext.crawlService().botBrowserCommandText());
        Button copyVisible = new Button(t("action.copyVisibleCommand"));
        copyVisible.getStyleClass().add("secondary-button");
        copyVisible.setOnAction(event -> copyToClipboard(visibleCommand.getText(), "notification.commandCopied"));

        TextArea headlessCommand = commandArea(applicationContext.crawlService().botBrowserHeadlessCommandText());
        Button copyHeadless = new Button(t("action.copyHeadlessCommand"));
        copyHeadless.getStyleClass().add("secondary-button");
        copyHeadless.setOnAction(event -> copyToClipboard(headlessCommand.getText(), "notification.commandCopied"));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(labelFor(t("workspace.command.visible")), 0, 0);
        grid.add(visibleCommand, 1, 0);
        grid.add(copyVisible, 2, 0);
        grid.add(labelFor(t("workspace.command.headless")), 0, 1);
        grid.add(headlessCommand, 1, 1);
        grid.add(copyHeadless, 2, 1);
        GridPane.setHgrow(visibleCommand, Priority.ALWAYS);
        GridPane.setHgrow(headlessCommand, Priority.ALWAYS);

        VBox box = new VBox(title, detail, grid);
        box.setSpacing(8);
        box.getStyleClass().add("command-box");
        return box;
    }

    private Label labelFor(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        label.setWrapText(true);
        return label;
    }

    private TextArea commandArea(String text) {
        TextArea area = new TextArea(text);
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefRowCount(2);
        area.setMinHeight(58);
        area.getStyleClass().add("command-field");
        return area;
    }

    private void copyToClipboard(String text, String notificationKey) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text == null ? "" : text);
        Clipboard.getSystemClipboard().setContent(content);
        showNotification(t(notificationKey), true);
    }

    private void initializeBotBrowserFromWorkspace(boolean requestedByCrawl) {
        setChromeControlsDisabled(true);
        applyChromeStatus("footer.chromeOpening", "info-pill", t("footer.chromeOpening.tooltip"));
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() {
                return applicationContext.crawlService().initializeVisibleBotBrowser(java.time.Duration.ofSeconds(45));
            }
        };
        task.setOnSucceeded(event -> {
            setChromeControlsDisabled(false);
            boolean ready = Boolean.TRUE.equals(task.getValue());
            applyChromeStatus(ready);
            appendWorkflowLog(t(ready ? "workspace.console.chromeReady" : "workspace.console.chromeOpened"));
            if (ready && requestedByCrawl) {
                showNotification(t("notification.chromeReadyRunAgain"), true);
            } else if (ready) {
                showNotification(t("notification.chromeReady"), true);
            } else {
                showNotification(t("notification.chromeOpenedLogin"), false);
            }
        });
        task.setOnFailed(event -> {
            setChromeControlsDisabled(false);
            Throwable exception = task.getException();
            String message = exception == null ? "" : SecretUtils.sanitizeMessage(exception.getMessage());
            applyChromeStatus(false, message);
            showNotification(t("notification.chromeOpenFailed", message), false);
        });
        Thread thread = new Thread(task, "workspace-open-chrome-bot");
        thread.setDaemon(true);
        thread.start();
    }

    private void setChromeControlsDisabled(boolean disabled) {
        if (initializeBotButton != null) {
            initializeBotButton.setDisable(disabled);
            initializeBotButton.setText(disabled ? t("workspace.bot.opening") : t("action.initializeBot"));
        }
        if (checkChromeButton != null) {
            checkChromeButton.setDisable(disabled);
        }
    }

    private Node buildAnalysisWorkspaceView() {
        try {
            SourceCodeDetailController controller = new SourceCodeDetailController(
                    applicationContext.sourceCodeDetailService(),
                    applicationContext.analysisService(),
                    (message, success) -> showNotification(message, success),
                    i18n
            );

            List<AiAnalysisResult> analyses = loadAnalyses();
            ObservableList<AnalysisRow> rows = toAnalysisRows(analyses);
            TilePane metrics = metricGrid(
                    metricCard(t("analysis.metric.count"), String.valueOf(analyses.size()), t("analysis.metric.count.hint")),
                    metricCard(t("analysis.metric.quality"), averageScore(analyses, true), t("analysis.metric.quality.hint")),
                    metricCard(t("analysis.metric.risk"), averageScore(analyses, false), t("analysis.metric.risk.hint")),
                    metricCard(t("analysis.metric.model"), latestModelName(analyses), t("analysis.metric.model.hint"))
            );

            TableView<AnalysisRow> table = table(
                    rows,
                    column(t("table.submission"), AnalysisRow::submission, 140),
                    column(t("table.analyzer"), AnalysisRow::analyzer, 110),
                    column(t("table.structures"), AnalysisRow::structures, 210),
                    column(t("table.algorithms"), AnalysisRow::algorithms, 220),
                    column(t("table.risk"), AnalysisRow::risk, 100),
                    column(t("table.quality"), AnalysisRow::quality, 90)
            );
            table.setPlaceholder(emptyPlaceholder(
                    t("analysis.placeholder.title"),
                    t("analysis.placeholder.detail")
            ));
            table.setPrefHeight(260);

            return screen(
                    sectionHeader(t("screen.analysis.title"), t("screen.analysis.subtitle")),
                    metrics,
                    stateBanner(t("analysis.banner.title"), t("analysis.banner.detail"), false),
                    controller.createView(),
                    card(t("analysis.history"), table)
            );
        } catch (RuntimeException ex) {
            return screen(
                    sectionHeader(t("screen.analysis.title"), t("screen.analysis.subtitle")),
                    errorCard(t("error.analysis"), ex)
            );
        }
    }

    private Node buildEvaluationReportView() {
        ObservableList<SkillRow> rows = loadSkillRows();
        BarChart<String, Number> chart = skillChart(rows);
        TableView<SkillRow> table = table(
                rows,
                column("#", SkillRow::rank, 70),
                column(t("table.handle"), SkillRow::handle, 150),
                column(t("table.structures"), SkillRow::ds, 80),
                column(t("table.algorithms"), SkillRow::algorithm, 100),
                column(t("table.quality"), SkillRow::quality, 90),
                column(t("table.risk"), SkillRow::risk, 90),
                column(t("table.overall"), SkillRow::overall, 90),
                column(t("table.summary"), SkillRow::summary, 320)
        );
        table.setPlaceholder(emptyPlaceholder(
                t("dashboard.placeholder.score.title"),
                t("dashboard.placeholder.score.detail")
        ));
        table.setPrefHeight(360);

        GridPane filters = formGrid();
        LocalDate today = LocalDate.now();
        TextField fromField = new TextField(today.withDayOfMonth(1).format(DATE_FORMATTER));
        TextField toField = new TextField(today.format(DATE_FORMATTER));
        ComboBox<String> formatCombo = combo("PDF", "Excel");
        CheckBox openAfterExport = new CheckBox(t("evaluation.filter.openFile"));

        addFormRow(filters, 0, t("evaluation.filter.reportType"), combo(t("evaluation.type.handle"), t("evaluation.type.class"), t("evaluation.type.aiRisk"), t("evaluation.type.crawlLog")));
        addFormRow(filters, 1, t("evaluation.filter.platform"), combo(t("evaluation.all"), "Codeforces", "VJudge"));
        addFormRow(filters, 2, t("evaluation.filter.group"), combo(t("evaluation.group.all"), "K17-CS", "K17-ACM", "K18-CS"));
        addFormRow(filters, 3, t("evaluation.filter.from"), fromField);
        addFormRow(filters, 4, t("evaluation.filter.to"), toField);
        addFormRow(filters, 5, t("evaluation.filter.format"), formatCombo);
        addFormRow(filters, 6, t("evaluation.filter.openFile"), openAfterExport);

        Button previewButton = actionButton(t("action.preview"), true, t("notification.previewRefreshed"));
        Button exportButton = new Button(t("action.exportReport"));
        exportButton.getStyleClass().add("secondary-button");
        exportButton.setOnAction(event -> exportReport(fromField, toField, formatCombo, openAfterExport));
        Button openFolderButton = new Button(t("action.openFolder"));
        openFolderButton.getStyleClass().add("secondary-button");
        openFolderButton.setOnAction(event -> openReportsFolder());

        FlowPane actions = actionFlow(
                previewButton,
                exportButton,
                openFolderButton
        );
        filters.add(actions, 1, 7);

        TableView<ReportRow> preview = table(
                reportPreviewRows(),
                column(t("report.section"), ReportRow::section, 180),
                column(t("report.metric"), ReportRow::metric, 180),
                column(t("report.value"), ReportRow::value, 130),
                column(t("report.note"), ReportRow::note, 360)
        );
        preview.setPrefHeight(280);

        return screen(
                sectionHeader(t("evaluation.title"), t("evaluation.subtitle")),
                stateBanner(t("evaluation.banner.title"), t("evaluation.banner.detail"), false),
                split(card(t("evaluation.chart"), chart), card(t("evaluation.filters"), filters)),
                card(t("evaluation.table"), table),
                card(t("evaluation.preview"), preview)
        );
    }

    private Node buildSettingsView() {
        SchedulerSettingsController controller = new SchedulerSettingsController(
                schedulerManager,
                applicationContext.schedulerSettingsService(),
                (message, success) -> showNotification(message, success),
                i18n
        );
        return controller.createView();
    }

    private void applySavedSchedulerConfig() {
        try {
            var config = applicationContext.schedulerSettingsService().loadConfig();
            schedulerManager.configureDailyCrawl(config.dailyRunTime(), config.autoCrawlEnabled());
        } catch (RuntimeException ignored) {
            // EN: SQL Server/settings may not be configured yet; Settings can save them later.
            // VI: SQL Server/cài đặt có thể chưa sẵn sàng; màn hình Cài đặt sẽ lưu sau.
        }
    }

    private VBox screen(Node... nodes) {
        VBox box = new VBox(nodes);
        box.setPadding(new Insets(24));
        box.setSpacing(18);
        box.setMaxWidth(Double.MAX_VALUE);
        box.getStyleClass().add("screen");
        return box;
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

    private TilePane split(Node left, Node right) {
        TilePane box = new TilePane(left, right);
        box.setHgap(16);
        box.setVgap(16);
        box.setPrefColumns(2);
        box.setPrefTileWidth(520);
        box.setMaxWidth(Double.MAX_VALUE);
        box.getStyleClass().add("responsive-split");
        return box;
    }

    private TilePane metricGrid(Node... cards) {
        TilePane tilePane = new TilePane();
        tilePane.setHgap(14);
        tilePane.setVgap(14);
        tilePane.setPrefColumns(4);
        tilePane.setPrefTileWidth(210);
        tilePane.setMaxWidth(Double.MAX_VALUE);
        tilePane.getChildren().addAll(cards);
        return tilePane;
    }

    private VBox metricCard(String label, String value, String hint) {
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("metric-value");

        Label labelText = new Label(label);
        labelText.getStyleClass().add("metric-label");

        Label hintText = new Label(hint);
        hintText.getStyleClass().add("metric-hint");

        VBox box = new VBox(valueLabel, labelText, hintText);
        box.setMinWidth(180);
        box.setMaxWidth(Double.MAX_VALUE);
        box.setPadding(new Insets(16));
        box.setSpacing(6);
        box.getStyleClass().add("metric-card");
        return box;
    }

    @SafeVarargs
    private final <T> TableView<T> table(ObservableList<T> rows, TableColumn<T, String>... columns) {
        TableView<T> table = new TableView<>(rows);
        table.getColumns().addAll(columns);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(emptyPlaceholder(t("analysis.placeholder.title"), t("analysis.placeholder.detail")));
        return table;
    }

    private FlowPane actionFlow(Node... nodes) {
        FlowPane flowPane = new FlowPane(nodes);
        flowPane.setHgap(10);
        flowPane.setVgap(10);
        flowPane.setAlignment(Pos.CENTER_LEFT);
        flowPane.getStyleClass().add("action-flow");
        return flowPane;
    }

    private VBox stateBanner(String title, String detail, boolean error) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("state-title");

        Label detailLabel = new Label(detail);
        detailLabel.getStyleClass().add("state-detail");
        detailLabel.setWrapText(true);

        VBox box = new VBox(titleLabel, detailLabel);
        box.setSpacing(4);
        box.getStyleClass().addAll("state-banner", error ? "state-banner-error" : "state-banner-info");
        return box;
    }

    private VBox emptyPlaceholder(String title, String detail) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("state-title");

        Label detailLabel = new Label(detail);
        detailLabel.getStyleClass().add("state-detail");
        detailLabel.setWrapText(true);

        VBox box = new VBox(titleLabel, detailLabel);
        box.setAlignment(Pos.CENTER);
        box.setSpacing(4);
        box.setPadding(new Insets(16));
        return box;
    }

    private VBox errorCard(String title, RuntimeException exception) {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        TextArea details = new TextArea(message);
        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefRowCount(4);
        details.getStyleClass().add("error-detail");
        return card(title, new VBox(
                stateBanner(t("error.screen.title"), t("error.screen.detail"), true),
                details
        ));
    }

    private <T> TableColumn<T, String> column(String title, Function<T, String> valueFactory, int width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new SimpleStringProperty(valueFactory.apply(data.getValue())));
        column.setPrefWidth(width);
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                setText(item);
                setTextOverrun(OverrunStyle.ELLIPSIS);
                setTooltip(item.length() > 24 ? new Tooltip(item) : null);
            }
        });
        return column;
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
        labelNode.setMinWidth(118);
        labelNode.setPrefWidth(132);
        labelNode.setMaxWidth(152);
        grid.add(labelNode, 0, row);
        grid.add(control, 1, row);
        GridPane.setHgrow(control, Priority.ALWAYS);
        if (control instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
    }

    private ComboBox<String> combo(String first, String... rest) {
        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.getItems().add(first);
        comboBox.getItems().addAll(rest);
        comboBox.getSelectionModel().selectFirst();
        return comboBox;
    }

    private Button actionButton(String label, boolean primary, String successMessage) {
        Button button = new Button(label);
        button.getStyleClass().add(primary ? "primary-button" : "secondary-button");
        button.setOnAction(event -> {
            showLoading();
            showNotification(successMessage, true);
        });
        return button;
    }

    private Label label(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        return label;
    }

    private Label smallLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("small-label");
        return label;
    }

    private Label statusPill(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().addAll("status-pill", styleClass);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    private BarChart<String, Number> skillChart(ObservableList<SkillRow> rows) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis(0, 100, 20);
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setCategoryGap(10);
        chart.setBarGap(2);
        xAxis.setTickLabelRotation(-35);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        rows.stream()
                .limit(10)
                .forEach(row -> series.getData().add(new XYChart.Data<>(shortHandle(row.handle()), parseScore(row.overall()))));
        if (rows.isEmpty()) {
            series.getData().add(new XYChart.Data<>(t("analysis.placeholder.title"), 0));
        }
        chart.getData().add(series);
        chart.setMinHeight(280);
        chart.setPrefHeight(320);
        return chart;
    }

    private List<AiAnalysisResult> loadAnalyses() {
        try {
            return applicationContext.aiAnalysisResultRepository().findRecent(ANALYSIS_SCREEN_LIMIT);
        } catch (RuntimeException ex) {
            notifyIfReady(t("analysis.loadFailed", ex.getMessage()), false);
            return List.of();
        }
    }

    private ObservableList<AnalysisRow> toAnalysisRows(List<AiAnalysisResult> analyses) {
        ObservableList<AnalysisRow> rows = FXCollections.observableArrayList();
        for (AiAnalysisResult analysis : analyses) {
            rows.add(new AnalysisRow(
                    analysis.getSubmissionId() == null ? "-" : String.valueOf(analysis.getSubmissionId()),
                    emptyAsDash(analysis.getAnalyzerType()),
                    emptyAsDash(analysis.getDataStructures()),
                    emptyAsDash(analysis.getAlgorithms()),
                    emptyAsDash(analysis.getAiRiskLevel()),
                    scoreText(analysis.getCodeQualityScore())
            ));
        }
        return rows;
    }

    private ObservableList<SkillRow> loadSkillRows() {
        try {
            Map<Long, HandleAccount> handlesById = handlesById();
            Map<Long, Platform> platformsById = platformsById();
            ObservableList<SkillRow> rows = FXCollections.observableArrayList();
            int rank = 1;
            for (SkillScore score : applicationContext.skillScoreRepository().findRecent(SKILL_SCREEN_LIMIT)) {
                HandleAccount handle = handlesById.get(score.getHandleId());
                Platform platform = handle == null ? null : platformsById.get(handle.getPlatformId());
                rows.add(new SkillRow(
                        String.valueOf(rank++),
                        displayHandle(platform, handle),
                        scoreText(score.getDataStructureScore()),
                        scoreText(score.getAlgorithmScore()),
                        scoreText(score.getCodeQualityScore()),
                        scoreText(score.getAiUsageRiskScore()),
                        scoreText(score.getOverallScore()),
                        emptyAsDash(score.getSummary())
                ));
            }
            return rows;
        } catch (RuntimeException ex) {
            notifyIfReady(t("evaluation.loadFailed", ex.getMessage()), false);
            return FXCollections.observableArrayList();
        }
    }

    private ObservableList<ReportRow> reportPreviewRows() {
        try {
            var snapshot = applicationContext.dashboardService().loadDashboard();
            var summary = snapshot.summary();
            return FXCollections.observableArrayList(
                    new ReportRow(t("report.overview"), t("report.trackedHandles"), String.valueOf(summary.totalHandles()), t("report.note.sql")),
                    new ReportRow(t("report.overview"), t("report.submissions"), String.valueOf(summary.totalSubmissions()), t("report.note.total")),
                    new ReportRow(t("screen.analysis.title"), t("report.pendingAnalysis"), String.valueOf(summary.pendingAnalysisSources()), t("report.note.pending")),
                    new ReportRow(t("screen.analysis.title"), t("report.analyzedSources"), String.valueOf(summary.analyzedSourceCodes()), t("report.note.analyzed")),
                    new ReportRow(t("workspace.title"), t("report.crawlErrors"), String.valueOf(summary.recentCrawlErrors()), t("report.note.recent"))
            );
        } catch (RuntimeException ex) {
            notifyIfReady(t("report.previewFailed", ex.getMessage()), false);
            return FXCollections.observableArrayList();
        }
    }

    private void exportReport(
            TextField fromField,
            TextField toField,
            ComboBox<String> formatCombo,
            CheckBox openAfterExport
    ) {
        try {
            showLoading();
            LocalDate from = LocalDate.parse(fromField.getText().trim(), DATE_FORMATTER);
            LocalDate to = LocalDate.parse(toField.getText().trim(), DATE_FORMATTER);
            ReportRequest request = new ReportRequest(from, to, List.of(), openAfterExport.isSelected());
            String format = formatCombo.getValue();

            Task<ReportExportResult> task = new Task<>() {
                @Override
                protected ReportExportResult call() {
                    return "Excel".equalsIgnoreCase(format)
                            ? applicationContext.excelReportService().exportExcel(request, Path.of("reports"))
                            : applicationContext.reportService().exportPdf(request, Path.of("reports"));
                }
            };
            task.setOnSucceeded(event -> showNotification(task.getValue().message(), true));
            task.setOnFailed(event -> {
                Throwable exception = task.getException();
                showNotification(t("notification.reportExportFailed",
                        exception == null ? "Unknown error" : exception.getMessage()), false);
            });
            Thread thread = new Thread(task, "report-export");
            thread.setDaemon(true);
            thread.start();
        } catch (RuntimeException ex) {
            showNotification(t("notification.reportExportFailed", ex.getMessage()), false);
        }
    }

    private void openReportsFolder() {
        Path reportsDirectory = Path.of("reports").toAbsolutePath().normalize();
        try {
            Files.createDirectories(reportsDirectory);
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                showNotification(t("notification.reportsReady", reportsDirectory), true);
                return;
            }
            Desktop.getDesktop().open(reportsDirectory.toFile());
            showNotification(t("notification.reportsOpened", reportsDirectory), true);
        } catch (IOException | RuntimeException ex) {
            showNotification(t("notification.reportsOpenFailed", ex.getMessage()), false);
        }
    }

    private Map<Long, HandleAccount> handlesById() {
        Map<Long, HandleAccount> mapped = new HashMap<>();
        for (HandleAccount handle : applicationContext.handleAccountRepository().findAll()) {
            if (handle.getHandleId() != null) {
                mapped.put(handle.getHandleId(), handle);
            }
        }
        return mapped;
    }

    private Map<Long, Platform> platformsById() {
        Map<Long, Platform> mapped = new HashMap<>();
        for (Platform platform : applicationContext.platformRepository().findAll()) {
            if (platform.getPlatformId() != null) {
                mapped.put(platform.getPlatformId(), platform);
            }
        }
        return mapped;
    }

    private String displayHandle(Platform platform, HandleAccount handle) {
        if (handle == null) {
            return "-";
        }
        String platformCode = platform == null ? "?" : platform.getCode();
        return platformCode + "/" + handle.getHandle();
    }

    private String averageScore(List<AiAnalysisResult> analyses, boolean quality) {
        double total = 0;
        int count = 0;
        for (AiAnalysisResult analysis : analyses) {
            BigDecimal score = quality ? analysis.getCodeQualityScore() : analysis.getAiRiskScore();
            if (score != null) {
                total += score.doubleValue();
                count++;
            }
        }
        return count == 0 ? "-" : String.valueOf(Math.round(total / count));
    }

    private String latestModelName(List<AiAnalysisResult> analyses) {
        for (AiAnalysisResult analysis : analyses) {
            if (analysis.getModelName() != null && !analysis.getModelName().isBlank()) {
                return analysis.getModelName();
            }
        }
        return "-";
    }

    private String scoreText(BigDecimal value) {
        return value == null ? "-" : String.valueOf(Math.round(value.doubleValue()));
    }

    private int parseScore(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String emptyAsDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String shortHandle(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String handle = value.contains("/") ? value.substring(value.indexOf('/') + 1) : value;
        return handle.length() <= 14 ? handle : handle.substring(0, 11) + "...";
    }

    private void notifyIfReady(String message, boolean success) {
        if (notificationBar != null) {
            showNotification(message, success);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private interface ViewFactory {
        Node create();
    }

    private record AnalysisRow(String submission, String analyzer, String structures, String algorithms, String risk, String quality) {
    }

    private record SkillRow(String rank, String handle, String ds, String algorithm, String quality, String risk, String overall, String summary) {
    }

    private record ReportRow(String section, String metric, String value, String note) {
    }

    public static void main(String[] args) {
        launch(args);
    }
}
