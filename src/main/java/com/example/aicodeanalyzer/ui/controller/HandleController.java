package com.example.aicodeanalyzer.ui.controller;

import com.example.aicodeanalyzer.i18n.I18n;
import com.example.aicodeanalyzer.crawler.CrawlRequest;
import com.example.aicodeanalyzer.crawler.CrawlResult;
import com.example.aicodeanalyzer.model.HandleAccount;
import com.example.aicodeanalyzer.model.Platform;
import com.example.aicodeanalyzer.service.CrawlService;
import com.example.aicodeanalyzer.service.HandleAccountService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Controls screens for adding, editing, validating, and deactivating handles.
 * Điều khiển màn hình thêm, sửa, kiểm tra và tạm dừng theo dõi handle.
 */
public class HandleController {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final HandleAccountService handleAccountService;
    private final CrawlService crawlService;
    private final BiConsumer<String, Boolean> notifier;
    private final I18n i18n;
    private final ObservableList<HandleAccount> handles = FXCollections.observableArrayList();
    private final ObservableList<PlatformOption> platforms = FXCollections.observableArrayList();
    private final Map<Long, PlatformOption> platformById = new HashMap<>();

    private ComboBox<PlatformOption> platformComboBox;
    private TextField handleField;
    private TextArea notesArea;
    private TableView<HandleAccount> tableView;

    public HandleController() {
        this(new HandleAccountService(), null, HandleController::showAlertNotification, I18n.createDefault());
    }

    public HandleController(HandleAccountService handleAccountService, BiConsumer<String, Boolean> notifier) {
        this(handleAccountService, null, notifier, I18n.createDefault());
    }

    public HandleController(HandleAccountService handleAccountService, BiConsumer<String, Boolean> notifier, I18n i18n) {
        this(handleAccountService, null, notifier, i18n);
    }

    public HandleController(
            HandleAccountService handleAccountService,
            CrawlService crawlService,
            BiConsumer<String, Boolean> notifier,
            I18n i18n
    ) {
        this.handleAccountService = handleAccountService;
        this.crawlService = crawlService;
        this.notifier = notifier;
        this.i18n = i18n;
    }

    public Node createView() {
        initializeControls();

        Button addButton = actionButton(t("handle.add"), true, this::addHandle);
        Button updateButton = actionButton(t("action.update"), false, this::updateHandle);
        Button deleteButton = actionButton(t("action.delete"), false, this::deleteSelectedHandle);
        Button refreshButton = actionButton(t("action.refresh"), false, () -> refresh(true));
        Button clearButton = actionButton(t("action.clear"), false, this::clearForm);

        FlowPane actions = actionFlow(addButton, updateButton, deleteButton, refreshButton, clearButton);

        VBox formCard = card(t("handle.form.card"), buildForm(actions));
        VBox tableCard = card(t("handle.table.card"), tableView);
        tableView.setPrefHeight(390);

        TilePane content = responsiveSplit(formCard, tableCard);

        VBox layout = new VBox(
                sectionNote(t("handle.note.title"), t("handle.note.detail")),
                content
        );
        layout.setPadding(new Insets(24));
        layout.setSpacing(18);
        layout.getStyleClass().add("screen");

        refresh(false);
        return layout;
    }

    public Node createWorkspacePanel() {
        initializeControls();

        Button addButton = actionButton(t("handle.add"), true, this::addHandle);
        Button updateButton = actionButton(t("action.update"), false, this::updateHandle);
        Button deleteButton = actionButton(t("action.delete"), false, this::deleteSelectedHandle);
        Button refreshButton = actionButton(t("action.refresh"), false, () -> refresh(true));
        Button clearButton = actionButton(t("action.clear"), false, this::clearForm);

        FlowPane actions = actionFlow(addButton, updateButton, deleteButton, refreshButton, clearButton);

        VBox formCard = card(t("workspace.quickAdd.title"), buildForm(actions));
        VBox tableCard = card(t("workspace.pipeline.title"), tableView);
        tableView.setPrefHeight(480);

        VBox content = new VBox(formCard, tableCard);
        content.setSpacing(16);
        content.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        VBox layout = new VBox(content);
        layout.setSpacing(0);
        layout.setMaxWidth(Double.MAX_VALUE);
        layout.getStyleClass().add("workspace-panel");

        refresh(false);
        return layout;
    }

    public void refreshHandles() {
        refresh(false);
    }

    private void initializeControls() {
        platformComboBox = new ComboBox<>(platforms);
        platformComboBox.setMaxWidth(Double.MAX_VALUE);
        platformComboBox.setPromptText(t("handle.platform.prompt"));

        handleField = new TextField();
        handleField.setPromptText(t("handle.handle.prompt"));

        notesArea = new TextArea();
        notesArea.setPromptText(t("handle.notes.prompt"));
        notesArea.setPrefRowCount(4);
        notesArea.setWrapText(true);

        tableView = buildTable();
        tableView.setItems(handles);
        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, selected) -> fillForm(selected));
    }

    private GridPane buildForm(FlowPane actions) {
        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);
        form.getStyleClass().add("form-grid");

        addRow(form, 0, t("table.platform"), platformComboBox);
        addRow(form, 1, t("table.handle"), handleField);
        addRow(form, 2, t("table.notes"), notesArea);
        form.add(actions, 1, 3);
        return form;
    }

    private TableView<HandleAccount> buildTable() {
        TableView<HandleAccount> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getStyleClass().add("full-text-table");
        table.setPlaceholder(emptyPlaceholder(t("handle.placeholder.title"), t("handle.placeholder.detail")));

        table.getColumns().setAll(List.of(
                column(t("table.platform"), handle -> platformName(handle.getPlatformId()), 150),
                column(t("table.handle"), HandleAccount::getHandle, 190),
                column(t("table.notes"), handle -> valueOrEmpty(handle.getNotes()), 380),
                column(t("table.permission"), handle -> valueOrEmpty(handle.getConsentStatus()), 150),
                column(t("table.lastCrawl"), handle -> handle.getLastCrawledAt() == null
                        ? "-"
                        : DATE_TIME_FORMATTER.format(handle.getLastCrawledAt()), 170),
                column(t("table.status"), handle -> handle.isActive() ? t("table.tracked") : t("table.paused"), 130),
                actionColumn()
        ));

        return table;
    }

    private void refresh(boolean showSuccessMessage) {
        try {
            loadPlatforms();
            handles.setAll(handleAccountService.findAllHandles());
            if (showSuccessMessage) {
                notify(t("handle.notify.refreshed"), true);
            }
        } catch (RuntimeException ex) {
            notify(t("handle.error.load", ex.getMessage()), false);
        }
    }

    private void addHandle() {
        try {
            PlatformOption selectedPlatform = requireSelectedPlatform();
            HandleAccount saved = handleAccountService.addHandle(
                    selectedPlatform.code(),
                    handleField.getText(),
                    notesArea.getText()
            );
            handles.add(saved);
            tableView.getSelectionModel().select(saved);
            notify(t("handle.notify.added"), true);
        } catch (RuntimeException ex) {
            notify(t("handle.error.add", ex.getMessage()), false);
        }
    }

    private void updateHandle() {
        HandleAccount selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            notify(t("handle.error.selectUpdate"), false);
            return;
        }

        try {
            PlatformOption selectedPlatform = requireSelectedPlatform();
            HandleAccount updated = handleAccountService.updateHandle(
                    selected.getHandleId(),
                    selectedPlatform.code(),
                    handleField.getText(),
                    notesArea.getText()
            );
            int index = handles.indexOf(selected);
            handles.set(index, updated);
            tableView.getSelectionModel().select(updated);
            notify(t("handle.notify.updated"), true);
        } catch (RuntimeException ex) {
            notify(t("handle.error.update", ex.getMessage()), false);
        }
    }

    private void deleteSelectedHandle() {
        HandleAccount selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            notify(t("handle.error.selectDelete"), false);
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(t("handle.confirm.title"));
        alert.setHeaderText(t("handle.confirm.header", selected.getHandle()));
        alert.setContentText(t("handle.confirm.content"));

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        try {
            handleAccountService.deleteHandle(selected.getHandleId());
            handles.remove(selected);
            clearForm();
            notify(t("handle.notify.deleted"), true);
        } catch (RuntimeException ex) {
            notify(t("handle.error.delete", ex.getMessage()), false);
        }
    }

    private void crawlSelectedHandle(HandleAccount selected, Button button) {
        if (selected == null) {
            notify(t("handle.error.selectCrawl"), false);
            return;
        }
        if (crawlService == null) {
            notify(t("handle.error.crawlUnavailable"), false);
            return;
        }

        button.setDisable(true);
        String originalText = button.getText();
        button.setText(t("handle.crawl.preparing"));

        Task<CrawlResult> task = new Task<>() {
            @Override
            protected CrawlResult call() {
                if (!crawlService.isVisibleBotBrowserReady()) {
                    updateMessage(t("handle.crawl.openingChrome"));
                    boolean ready = crawlService.ensureVisibleBotBrowserReady(java.time.Duration.ofSeconds(45));
                    if (!ready) {
                        throw new IllegalStateException(t("handle.error.chromeOpenFailed"));
                    }
                }
                updateMessage(t("handle.crawl.running"));
                return crawlService.crawlHandleResult(
                        selected.getHandleId(),
                        "MANUAL",
                        CrawlRequest.UNLIMITED_SUBMISSIONS
                );
            }
        };
        task.messageProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.isBlank()) {
                button.setText(newValue);
            }
        });
        task.setOnSucceeded(event -> {
            button.setDisable(false);
            button.setText(originalText);
            CrawlResult result = task.getValue();
            refresh(false);
            notify(t(
                    "handle.notify.crawled",
                    result.handle(),
                    result.newCount(),
                    result.updatedCount(),
                    result.failedCount()
            ), result.failedCount() == 0);
        });
        task.setOnFailed(event -> {
            button.setDisable(false);
            button.setText(originalText);
            Throwable exception = task.getException();
            String message = exception == null ? "Unknown error" : exception.getMessage();
            notify(t("handle.error.crawl", message), false);
        });
        Thread thread = new Thread(task, "crawl-single-handle-" + selected.getHandleId());
        thread.setDaemon(true);
        thread.start();
    }

    private void clearForm() {
        tableView.getSelectionModel().clearSelection();
        if (!platforms.isEmpty()) {
            platformComboBox.getSelectionModel().selectFirst();
        }
        handleField.clear();
        notesArea.clear();
    }

    private void fillForm(HandleAccount handleAccount) {
        if (handleAccount == null) {
            return;
        }

        platformComboBox.getSelectionModel().select(platformById.get(handleAccount.getPlatformId()));
        handleField.setText(handleAccount.getHandle());
        notesArea.setText(valueOrEmpty(handleAccount.getNotes()));
    }

    private void loadPlatforms() {
        List<Platform> loadedPlatforms = handleAccountService.findPlatforms();
        platforms.clear();
        platformById.clear();

        loadedPlatforms.stream()
                .filter(platform -> "CODEFORCES".equalsIgnoreCase(platform.getCode())
                        || "VJUDGE".equalsIgnoreCase(platform.getCode()))
                .map(platform -> new PlatformOption(platform.getPlatformId(), platform.getCode(), platform.getName()))
                .forEach(option -> {
                    platforms.add(option);
                    platformById.put(option.platformId(), option);
                });

        if (!platforms.isEmpty() && platformComboBox.getSelectionModel().isEmpty()) {
            platformComboBox.getSelectionModel().selectFirst();
        }
    }

    private PlatformOption requireSelectedPlatform() {
        PlatformOption selected = platformComboBox.getSelectionModel().getSelectedItem();
        if (selected == null) {
            throw new IllegalArgumentException(t("handle.error.choosePlatform"));
        }
        return selected;
    }

    private void addRow(GridPane form, int row, String label, Node control) {
        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("form-label");
        labelNode.setMinWidth(88);
        labelNode.setPrefWidth(104);
        labelNode.setMaxWidth(120);
        form.add(labelNode, 0, row);
        form.add(control, 1, row);
        GridPane.setHgrow(control, Priority.ALWAYS);
        if (control instanceof javafx.scene.layout.Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
    }

    private VBox card(String title, Node content) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("card-title");

        VBox card = new VBox(titleLabel, content);
        card.setSpacing(12);
        card.setPadding(new Insets(16));
        card.getStyleClass().add("card");
        card.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(content, Priority.ALWAYS);
        return card;
    }

    private TilePane responsiveSplit(Node... nodes) {
        TilePane tilePane = new TilePane();
        tilePane.setHgap(16);
        tilePane.setVgap(16);
        tilePane.setPrefColumns(2);
        tilePane.setPrefTileWidth(520);
        tilePane.setMaxWidth(Double.MAX_VALUE);
        tilePane.getStyleClass().add("responsive-split");
        tilePane.getChildren().addAll(nodes);
        return tilePane;
    }

    private FlowPane actionFlow(Node... nodes) {
        FlowPane flowPane = new FlowPane(nodes);
        flowPane.setHgap(10);
        flowPane.setVgap(10);
        flowPane.setAlignment(Pos.CENTER_LEFT);
        flowPane.getStyleClass().add("action-flow");
        return flowPane;
    }

    private VBox sectionNote(String title, String detail) {
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

    private Button actionButton(String text, boolean primary, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add(primary ? "primary-button" : "secondary-button");
        button.setOnAction(event -> action.run());
        return button;
    }

    private TableColumn<HandleAccount, String> column(
            String title,
            java.util.function.Function<HandleAccount, String> valueFactory,
            int width
    ) {
        TableColumn<HandleAccount, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new SimpleStringProperty(valueFactory.apply(data.getValue())));
        column.setPrefWidth(width);
        column.setCellFactory(ignored -> new TableCell<>() {
            private final Label label = new Label();

            {
                label.setWrapText(true);
                label.getStyleClass().add("table-wrap-text");
                label.maxWidthProperty().bind(column.widthProperty().subtract(16));
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
                setText(null);
                label.setText(item);
                setGraphic(label);
                setTooltip(item.length() > 24 ? new Tooltip(item) : null);
            }
        });
        return column;
    }

    private TableColumn<HandleAccount, Void> actionColumn() {
        TableColumn<HandleAccount, Void> column = new TableColumn<>(t("table.actions"));
        column.setPrefWidth(160);
        column.setCellFactory(ignored -> new TableCell<>() {
            private final Button crawlButton = new Button(t("handle.action.crawl"));

            {
                crawlButton.getStyleClass().add("secondary-button");
                crawlButton.setOnAction(event -> {
                    HandleAccount row = getTableView().getItems().get(getIndex());
                    crawlSelectedHandle(row, crawlButton);
                });
                crawlButton.setTooltip(new Tooltip(t("handle.action.crawl.tooltip")));
                crawlButton.setMaxWidth(Double.MAX_VALUE);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(empty ? null : crawlButton);
            }
        });
        return column;
    }

    private String platformName(Long platformId) {
        PlatformOption platform = platformById.get(platformId);
        return platform == null ? t("table.unknown") : platform.name();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private void notify(String message, boolean success) {
        notifier.accept(message, success);
    }

    private String t(String key, Object... args) {
        return i18n.text(key, args);
    }

    private static void showAlertNotification(String message, boolean success) {
        I18n i18n = I18n.createDefault();
        Alert alert = new Alert(success ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
        alert.setTitle(success ? i18n.text("generic.success") : i18n.text("generic.error"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private record PlatformOption(Long platformId, String code, String name) {
        @Override
        public String toString() {
            return name;
        }
    }
}
