package com.projectpilot.ui.pages.admin;

import com.projectpilot.admin.AdminService;
import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.data.db.DbStore;
import com.projectpilot.data.db.auth.GlobalRole;
import com.projectpilot.data.db.auth.UserAdminService;
import com.projectpilot.model.Member;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.ProjectRole;
import com.projectpilot.model.enums.TaskStatus;
import com.projectpilot.ui.dialogs.CreateTeamDialog;
import com.projectpilot.ui.dialogs.EditUserDialog;
import com.projectpilot.ui.pages.admin.widgets.*;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import javafx.util.StringConverter;
import java.util.function.Supplier;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.scene.paint.Color;
import java.util.function.Function;
import javafx.collections.transformation.FilteredList;
import javafx.scene.shape.Circle;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class AdminPage extends BorderPane {

    private static final DateTimeFormatter LAST_ONLINE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter AUDIT_FMT = DateTimeFormatter.ofPattern("MMM dd HH:mm");
    private static final DateTimeFormatter INSTANT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final long ONLINE_WINDOW_MS = 5 * 60 * 1000L;
    private static final long IDLE_WINDOW_MS = 60 * 60 * 1000L;

    private final AdminService admin;
    private final InMemoryStore store;
    private final AppState appState;

    private final ObservableList<UserAdminService.UserRow> items = FXCollections.observableArrayList();
    private final ObservableList<UserHealthRow> healthItems = FXCollections.observableArrayList();
    private final ObservableList<AuditEntry> auditItems = FXCollections.observableArrayList();

    // UI refs
    private final Label status = new Label();

    // Alerts
    private final FlowPane alertsFlow = new FlowPane();

    // Integrity panel
    private final FlowPane integrityFlow = new FlowPane();
    private final Label integritySummary = new Label();

    // Drawer (user detail panel)
    private final ObjectProperty<UserAdminService.UserRow> selectedUser = new SimpleObjectProperty<>();
    private VBox userDrawer;
    private Label drawerTitle;
    private Label drawerMeta;
    private Label drawerStatusChip;

    private TextField drawerDisplayName;
    private TextField drawerUsername;
    private TextField drawerEmail;
    private PasswordField drawerNewPassword;
    private ComboBox<GlobalRole> drawerGlobalRole;
    private ComboBox<ProjectRole> drawerProjectRole;
    private CheckBox drawerActive;

    private Label drawerProjectsCount;
    private Label drawerTasksCount;
    private Label drawerProjectHint;

    // Scrolling / focus
    private ScrollPane scrollPane;
    private Node createUserBox;
    private TextField createUserUsernameField;
    private TableView<UserAdminService.UserRow> usersTable;

    // Backup + perf tracking
    private Instant lastBackupAt;
    private String lastBackupName;

    private double avgReloadMs = 0.0;
    private int reloadErrors = 0;

    // --- NEW dashboard widgets ---
    private MetricTile kpiTotalUsers;
    private MetricTile kpiActiveUsers;
    private MetricTile kpiProjects;
    private MetricTile kpiTasks;
    private MetricTile kpiStorage;
    private MetricTile kpiBackup;
    private MetricTile kpiLan;
    private MetricTile kpiReload;
    private MetricTile kpiErrors;

    private DonutChartView roleDonut;
    private MiniBarChartView presenceBars;
    private MiniBarChartView taskStatusBars;
    private MetricTile activity14d;

    private Timeline presenceTicker;

    public AdminPage(AdminService admin, InMemoryStore store, AppState appState) {
        this.admin = Objects.requireNonNull(admin);
        this.store = Objects.requireNonNull(store);
        this.appState = Objects.requireNonNull(appState);

        setPadding(new Insets(16));

        var title = new Label("Admin");
        title.getStyleClass().add("pp-h1");

        // Build sections
        var dashboard = buildAdminDashboard();          // NEW (premium header)
        var alerts = buildAlertsBanner();
        var quickActions = buildQuickActionsRow();
        var createBox = buildCreateUserBox();
        var teamBox = buildCreateTeamBox();
        var integrity = buildIntegrityPanel();
        var healthTable = buildUserHealthTable();
        var permissionMatrix = buildPermissionMatrix();
        var table = buildUsersTable();
        var auditPanel = buildAuditPanel();


        installWidgetDetails(kpiTotalUsers, "Total users", this::buildTotalUsersDetail);
        installWidgetDetails(kpiActiveUsers, "Active users", this::buildActiveUsersDetail);
        installWidgetDetails(kpiProjects,   "Projects",     this::buildProjectsDetail);
        installWidgetDetails(kpiTasks,      "Tasks",        this::buildTasksDetail);

        installWidgetDetails(kpiStorage, "Storage", this::buildStorageDetail);
        installWidgetDetails(kpiBackup,  "Backup",  this::buildBackupDetail);
        installWidgetDetails(kpiLan,     "LAN",     this::buildLanDetail);
        installWidgetDetails(kpiReload,  "Reload performance", this::buildReloadPerfDetail);
        installWidgetDetails(kpiErrors,  "Reload errors",      this::buildReloadErrorsDetail);

        installWidgetDetails(roleDonut,      "Roles", this::buildRolesDetail);
        installWidgetDetails(presenceBars,   "Presence", this::buildPresenceDetail);
        installWidgetDetails(taskStatusBars, "Task status", this::buildTaskStatusDetail);
        installWidgetDetails(activity14d,    "Online activity", this::buildActivityDetail);

        var top = new VBox(10, title, status);
        setTop(top);

        var content = new VBox(
                14,
                dashboard,
                alerts,
                quickActions,
                createBox,
                teamBox,
                integrity,
                healthTable,
                permissionMatrix,
                table,
                auditPanel
        );
        content.setFillWidth(true);

        ScrollPane scroll = new ScrollPane(content);
        scroll.getStyleClass().add("pp-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPadding(new Insets(0, 0, 8, 0));
        this.scrollPane = scroll;

        // Build the REAL drawer content (fields/buttons)
        this.userDrawer = (VBox) buildUserDrawer();

        // Wrap it in themed backboard shell
        Node drawerShell = buildUserDetailDrawer(this.userDrawer);

        // Scrim behind drawer (blocks clicks + dims background)
        Region scrim = new Region();
        scrim.getStyleClass().add("pp-scrim");
        scrim.setPickOnBounds(true);
        scrim.setOnMouseClicked(e -> {
            e.consume();
            closeDrawer();
        });

        // Mount as overlay: scroll -> scrim -> drawerShell
        StackPane stack = new StackPane(scroll, scrim, drawerShell);
        StackPane.setAlignment(drawerShell, Pos.TOP_RIGHT);
        StackPane.setMargin(drawerShell, new Insets(8));
        setCenter(stack);

        // Open/close bindings
        BooleanBinding drawerOpen = selectedUser.isNotNull();
        drawerShell.visibleProperty().bind(drawerOpen);
        drawerShell.managedProperty().bind(drawerOpen);
        scrim.visibleProperty().bind(drawerOpen);
        scrim.managedProperty().bind(drawerOpen);

        // Populate drawer when selection changes
        selectedUser.addListener((obs, o, n) -> {
            if (n != null) populateDrawer(n);
        });

        // Update LAN metrics on state changes
        appState.hostingProperty().addListener((obs, o, n) -> refreshAdminDashboard());
        appState.hostConnectionsProperty().addListener((obs, o, n) -> refreshAdminDashboard());
        appState.hostPortProperty().addListener((obs, o, n) -> refreshAdminDashboard());
        appState.clientOnlineProperty().addListener((obs, o, n) -> refreshAdminDashboard());
        appState.clientStatusProperty().addListener((obs, o, n) -> refreshAdminDashboard());

        // Presence ticker (keeps Online/Idle/Offline + mini charts feeling live)
        startPresenceTicker();

        // Stop ticker when page removed
        sceneProperty().addListener((obs, old, sc) -> {
            if (sc == null && presenceTicker != null) presenceTicker.stop();
            if (sc != null && presenceTicker != null) presenceTicker.play();
        });

        reload();
    }
    private Node buildTotalUsersDetail(Dialog<Void> dlg) {
        VBox root = new VBox(12);
        root.setPadding(new Insets(2)); // modal body already has padding

        Label sub = new Label("Login accounts");
        sub.getStyleClass().add("muted");

        TextField search = new TextField();
        search.setPromptText("Search by name, username, email…");
        search.getStyleClass().add("pp-search");

        ToggleGroup tg = new ToggleGroup();
        ToggleButton all = new ToggleButton("All");
        ToggleButton active = new ToggleButton("Active");
        ToggleButton disabled = new ToggleButton("Disabled");
        all.setToggleGroup(tg);
        active.setToggleGroup(tg);
        disabled.setToggleGroup(tg);
        all.setSelected(true);

        HBox filters = new HBox(8, all, active, disabled);
        filters.getStyleClass().add("pp-segment");
        filters.setAlignment(Pos.CENTER_LEFT);

        FilteredList<UserAdminService.UserRow> filtered =
                new FilteredList<>(items, u -> true);

        Runnable applyFilter = () -> {
            String q = search.getText() == null ? "" : search.getText().trim().toLowerCase();
            Toggle sel = tg.getSelectedToggle();

            filtered.setPredicate(u -> {
                if (u == null) return false;

                boolean matchesQuery = q.isBlank()
                        || safe(u.name()).toLowerCase().contains(q)
                        || safe(u.username()).toLowerCase().contains(q)
                        || safe(u.email()).toLowerCase().contains(q);

                boolean matchesStatus = true;
                if (sel == active) matchesStatus = u.active();
                else if (sel == disabled) matchesStatus = !u.active();

                return matchesQuery && matchesStatus;
            });
        };

        search.textProperty().addListener((o, a, b) -> applyFilter.run());
        tg.selectedToggleProperty().addListener((o, a, b) -> applyFilter.run());
        applyFilter.run();

        Label count = new Label();
        count.getStyleClass().add("pill");
        count.textProperty().bind(Bindings.size(filtered).asString().concat(" users"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button openSelected = new Button("Open selected");
        openSelected.getStyleClass().add("subtle");



        ListView<UserAdminService.UserRow> list = new ListView<>(filtered);
        openSelected.disableProperty().bind(
                Bindings.isNull(list.getSelectionModel().selectedItemProperty())
        );

        list.getStyleClass().add("pp-list");
        VBox.setVgrow(list, Priority.ALWAYS);

        list.setCellFactory(lv -> new ListCell<>() {
            private final Circle dot = new Circle(5);
            private final Label name = new Label();
            private final Label meta = new Label();
            private final Label role = new Label();
            private final Label state = new Label();

            private final VBox left = new VBox(2, name, meta);
            private final Region grow = new Region();
            private final HBox row = new HBox(12, dot, left, grow, role, state);

            {
                row.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(grow, Priority.ALWAYS);

                name.getStyleClass().add("list-title");
                meta.getStyleClass().add("muted");

                role.getStyleClass().addAll("pp-chip", "chip-role");
                state.getStyleClass().addAll("pp-chip", "chip-state");
            }

            @Override
            protected void updateItem(UserAdminService.UserRow u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) {
                    setGraphic(null);
                    return;
                }

                String display = !safe(u.name()).isBlank() ? safe(u.name()) : ("@" + safe(u.username()));
                name.setText(display);

                String metaText = "@" + safe(u.username());
                if (!safe(u.email()).isBlank()) metaText += " • " + safe(u.email());
                meta.setText(metaText);

                role.setText(u.globalRole() == null ? "-" : u.globalRole().name());
                state.setText(u.active() ? "active" : "disabled");
                state.getStyleClass().setAll("pp-chip", "chip-state", u.active() ? "chip-ok" : "chip-bad");

                StatusInfo si = statusFor(u); // Online/Idle/Offline
                dot.getStyleClass().setAll("presence-dot",
                        si.styleClass().equals("status-online") ? "dot-online"
                                : si.styleClass().equals("status-idle") ? "dot-idle"
                                : "dot-offline"
                );

                setGraphic(row);
            }
        });

        // double click -> open drawer & close modal
        list.setOnMouseClicked(ev -> {
            if (ev.getButton() != MouseButton.PRIMARY) return;
            if (ev.getClickCount() != 2) return;
            var u = list.getSelectionModel().getSelectedItem();
            if (u == null) return;
            openDrawer(u);
            dlg.close();
            ev.consume();
        });

        openSelected.setOnAction(ev -> {
            var u = list.getSelectionModel().getSelectedItem();
            if (u == null) return;
            openDrawer(u);
            dlg.close();
        });

        HBox topRow = new HBox(10, sub, spacer, count, openSelected);
        topRow.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(topRow, search, filters, list);
        return root;
    }
    private void themeDialog(Dialog<?> dlg) {
        DialogPane pane = dlg.getDialogPane();

        // inherit app stylesheets
        if (getScene() != null) {
            pane.getStylesheets().setAll(getScene().getStylesheets());
        }

        // ensure theme variables apply + scope modal styling
        if (!pane.getStyleClass().contains("pp-root")) pane.getStyleClass().add("pp-root");
        if (!pane.getStyleClass().contains("pp-modal")) pane.getStyleClass().add("pp-modal");
    }



    private static String safe(String s) { return s == null ? "" : s.trim(); }


    private void installWidgetDetails(Node node, String title, Function<Dialog<Void>, Node> contentFactory) {
        if (node == null) return;

        node.setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            if (e.getClickCount() != 1) return;

            Dialog<Void> dlg = new Dialog<>();
            dlg.setTitle(title);

            // attach to window (so it behaves like part of the app)
            if (getScene() != null && getScene().getWindow() != null) {
                dlg.initOwner(getScene().getWindow());
                dlg.initModality(Modality.WINDOW_MODAL);
            }

            dlg.initStyle(StageStyle.TRANSPARENT);

            DialogPane pane = dlg.getDialogPane();
            pane.getButtonTypes().clear(); // we use our own close button
            themeDialog(dlg);

            Node inner = contentFactory.apply(dlg);
            Node shell = wrapDialogContent(dlg, title, inner);
            pane.setContent(shell);

            // required for rounded transparent dialogs
            pane.sceneProperty().addListener((obs, old, sc) -> {
                if (sc != null) sc.setFill(Color.TRANSPARENT);
            });

            dlg.showAndWait();
            e.consume();
        });
    }



    private Node wrapDialogContent(Dialog<?> dlg, String title, Node inner) {
        Label h = new Label(title);
        h.getStyleClass().add("pp-h2");

        Button close = new Button("✕");
        close.getStyleClass().add("pp-icon-btn");
        close.setOnAction(ev -> dlg.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(10, h, spacer, close);
        header.getStyleClass().add("pp-modal-header");
        header.setAlignment(Pos.CENTER_LEFT);

        VBox body = new VBox(12, header, inner);
        body.getStyleClass().add("pp-modal-body");
        body.setPadding(new Insets(14));
        body.setPrefWidth(720);

        if (inner instanceof Region r) VBox.setVgrow(r, Priority.ALWAYS);

        return body;
    }


    private Node buildActiveUsersDetail() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));

        Label h = new Label("Active within window");
        h.getStyleClass().add("section-title");

        List<UserAdminService.UserRow> active = items.stream()
                .filter(u -> statusFor(u).label().equals("Online") || statusFor(u).label().equals("Idle"))
                .toList();

        Label summary = new Label("Count: " + active.size());
        summary.getStyleClass().add("muted");

        ListView<UserAdminService.UserRow> list = new ListView<>(FXCollections.observableArrayList(active));
        list.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(UserAdminService.UserRow u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) { setText(null); return; }
                StatusInfo si = statusFor(u);
                setText("@" + u.username() + " • " + si.label() + " • " + formatLastOnline(u.lastOnlineAt()));
            }
        });

        box.getChildren().addAll(h, summary, list);
        VBox.setVgrow(list, Priority.ALWAYS);
        return box;
    }

    private Node buildProjectsDetail() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));

        Label h = new Label("Projects snapshot");
        h.getStyleClass().add("section-title");

        List<Project> projects = new ArrayList<>();
        projects.addAll(store.getProjects());
        projects.addAll(store.getHistoryProjects());

        ListView<Project> list = new ListView<>(FXCollections.observableArrayList(projects));
        list.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Project p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) { setText(null); return; }
                int tasks = p.getTasks() == null ? 0 : p.getTasks().size();
                int members = p.getMembers() == null ? 0 : p.getMembers().size();
                setText(p.getName() + " • tasks " + tasks + " • members " + members);
            }
        });

        box.getChildren().addAll(h, list);
        VBox.setVgrow(list, Priority.ALWAYS);
        return box;
    }

    private Node buildTasksDetail() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));

        Label h = new Label("Tasks summary");
        h.getStyleClass().add("section-title");

        Map<TaskStatus, Integer> counts = new EnumMap<>(TaskStatus.class);
        for (Project p : store.getProjects()) {
            for (Task t : p.getTasks()) {
                if (t == null) continue;
                counts.merge(t.getStatus(), 1, Integer::sum);
            }
        }

        VBox stats = new VBox(6);
        for (TaskStatus st : TaskStatus.values()) {
            stats.getChildren().add(new Label(st.name() + ": " + counts.getOrDefault(st, 0)));
        }
        stats.getStyleClass().add("pp-card");

        box.getChildren().addAll(h, stats);
        return box;
    }

    private Node buildStorageDetail() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));

        Label h = new Label("Storage");
        h.getStyleClass().add("section-title");

        StorageInfo si = storageInfo();
        Label a = new Label("Mode: " + si.sub());
        Label b = new Label("Size: " + si.value());
        a.getStyleClass().add("muted");
        b.getStyleClass().add("muted");

        box.getChildren().addAll(h, a, b);
        return box;
    }

    private Node buildBackupDetail() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));

        Label h = new Label("Backup");
        h.getStyleClass().add("section-title");

        Label a = new Label(lastBackupAt == null ? "No backups yet." :
                ("Last backup: " + INSTANT_FMT.format(lastBackupAt.atZone(ZoneId.systemDefault()))));
        Label b = new Label(lastBackupName == null ? "" : ("Name: " + lastBackupName));
        a.getStyleClass().add("muted");
        b.getStyleClass().add("muted");

        box.getChildren().addAll(h, a, b);
        return box;
    }

    private Node buildLanDetail() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));

        Label h = new Label("LAN");
        h.getStyleClass().add("section-title");

        LanInfo li = lanInfo();
        Label a = new Label("Mode: " + li.value());
        Label b = new Label("Info: " + li.sub());
        a.getStyleClass().add("muted");
        b.getStyleClass().add("muted");

        box.getChildren().addAll(h, a, b);
        return box;
    }

    private Node buildReloadPerfDetail() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));

        Label h = new Label("Reload performance");
        h.getStyleClass().add("section-title");

        Label a = new Label("Avg reload: " + (avgReloadMs <= 0 ? "-" : String.format("%.0f ms", avgReloadMs)));
        Label b = new Label("Errors: " + reloadErrors);
        a.getStyleClass().add("muted");
        b.getStyleClass().add("muted");

        box.getChildren().addAll(h, a, b);
        return box;
    }

    private Node buildReloadErrorsDetail() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));

        Label h = new Label("Reload errors");
        h.getStyleClass().add("section-title");

        Label a = new Label("Count since start: " + reloadErrors);
        a.getStyleClass().add("muted");
        box.getChildren().addAll(h, a);
        return box;
    }

    private Node buildRolesDetail() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));

        Label h = new Label("Role breakdown");
        h.getStyleClass().add("section-title");

        long admins = items.stream().filter(u -> u.globalRole() == GlobalRole.ADMIN).count();
        long users  = items.stream().filter(u -> u.globalRole() == GlobalRole.USER).count();

        box.getChildren().addAll(h,
                new Label("ADMIN: " + admins),
                new Label("USER: " + users)
        );
        return box;
    }

    private Node buildPresenceDetail() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));

        Label h = new Label("Presence");
        h.getStyleClass().add("section-title");

        long online = items.stream().filter(u -> statusFor(u).label().equals("Online")).count();
        long idle   = items.stream().filter(u -> statusFor(u).label().equals("Idle")).count();
        long off    = items.size() - online - idle;

        box.getChildren().addAll(h,
                new Label("Online: " + online),
                new Label("Idle: " + idle),
                new Label("Offline: " + off)
        );
        return box;
    }

    private Node buildTaskStatusDetail() {
        return buildTasksDetail(); // reuse for now
    }

    private Node buildActivityDetail() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));

        Label h = new Label("Online activity (14d)");
        h.getStyleClass().add("section-title");

        Label a = new Label("Based on auth_users.last_online_at binning.");
        a.getStyleClass().add("muted");

        box.getChildren().addAll(h, a);
        return box;
    }


    // --------------------------------------------------------------------------------------------
    // NEW: ADMIN DASHBOARD (premium header)
    // --------------------------------------------------------------------------------------------

    private Node buildAdminDashboard() {
        Label title = new Label("Admin overview");
        title.getStyleClass().add("section-title");

        // KPI tiles
        kpiTotalUsers = new MetricTile("Total users");
        kpiActiveUsers = new MetricTile("Active users");
        kpiProjects   = new MetricTile("Projects");
        kpiTasks      = new MetricTile("Tasks");

        kpiStorage = new MetricTile("Storage");
        kpiBackup  = new MetricTile("Last backup");
        kpiLan     = new MetricTile("LAN status");
        kpiReload  = new MetricTile("Avg reload time");

        // Banner tile (only when reloadErrors > 0)
        kpiErrors  = new MetricTile("Reload errors");

        GridPane kpiGrid = new GridPane();
        kpiGrid.setHgap(12);
        kpiGrid.setVgap(12);
        kpiGrid.setMaxWidth(Double.MAX_VALUE);

        // Row 0
        kpiGrid.add(kpiTotalUsers, 0, 0);
        kpiGrid.add(kpiActiveUsers, 1, 0);
        kpiGrid.add(kpiProjects,   2, 0);
        kpiGrid.add(kpiTasks,      3, 0);

        // Row 1
        kpiGrid.add(kpiStorage, 0, 1);
        kpiGrid.add(kpiBackup,  1, 1);
        kpiGrid.add(kpiLan,     2, 1);
        kpiGrid.add(kpiReload,  3, 1);

        // Row 2 banner (spans all columns)
        kpiGrid.add(kpiErrors, 0, 2);
        GridPane.setColumnSpan(kpiErrors, 4);

        // Stable 4-column layout
        kpiGrid.getColumnConstraints().clear();
        for (int i = 0; i < 4; i++) {
            ColumnConstraints c = new ColumnConstraints();
            c.setHgrow(Priority.ALWAYS);
            c.setFillWidth(true);
            c.setPercentWidth(25);
            kpiGrid.getColumnConstraints().add(c);
        }

        // --- Analytics row (CREATE FIRST, THEN install hover) ---
        roleDonut = new DonutChartView();
        roleDonut.setCenterText("Roles", "");

        presenceBars = new MiniBarChartView();
        taskStatusBars = new MiniBarChartView();

        activity14d = new MetricTile("Last online activity (14d)");
        activity14d.setSubText("events/day based on auth_users.last_online_at");

        HBox analytics = new HBox(12, roleDonut, presenceBars, taskStatusBars, activity14d);
        analytics.setAlignment(Pos.TOP_LEFT);

        // Make all 4 cards participate in width distribution
        HBox.setHgrow(roleDonut, Priority.ALWAYS);
        HBox.setHgrow(presenceBars, Priority.ALWAYS);
        HBox.setHgrow(taskStatusBars, Priority.ALWAYS);
        HBox.setHgrow(activity14d, Priority.ALWAYS);

        // Keep the row visually consistent
        roleDonut.setPrefHeight(200);
        presenceBars.setPrefHeight(200);
        taskStatusBars.setPrefHeight(200);
        activity14d.setPrefHeight(200);

        roleDonut.setMinWidth(220);
        presenceBars.setMinWidth(220);
        taskStatusBars.setMinWidth(220);
        activity14d.setMinWidth(220);

        // NOW install hover (everything exists)
        installHoverLift(
                kpiTotalUsers, kpiActiveUsers, kpiProjects, kpiTasks,
                kpiStorage, kpiBackup, kpiLan, kpiReload, kpiErrors,
                roleDonut, presenceBars, taskStatusBars, activity14d
        );

        VBox box = new VBox(10, title, kpiGrid, analytics);
        box.setPadding(new Insets(12));
        box.getStyleClass().add("pp-card");

        refreshAdminDashboard();
        return box;
    }

    private static void installHoverLift(Node... nodes) {
        if (nodes == null) return;
        for (Node n : nodes) {
            if (n instanceof Region r) {
                HoverLift.install(r);
            }
        }
    }






    private void refreshAdminDashboard() {
        // Snapshot from current lists (no DB hit)
        AdminMetrics.Snapshot s = AdminMetrics.compute(items, store);

        kpiTotalUsers.setValueText(Integer.toString(s.totalUsers()));
        kpiTotalUsers.setSubText("login accounts");
        kpiTotalUsers.clearSeries();

        kpiActiveUsers.setValueText(Integer.toString(s.activeUsers()));
        kpiActiveUsers.setSubText("active accounts");
        kpiActiveUsers.clearSeries();

        kpiProjects.setValueText(Integer.toString(s.totalProjects()));
        kpiProjects.setSubText(s.activeProjects() + " active, " + s.doneProjects() + " done");
        kpiProjects.clearSeries();

        kpiTasks.setValueText(Integer.toString(s.totalTasks()));
        kpiTasks.setSubText(s.overdueTasks() + " overdue • " + s.unassignedTasks() + " unassigned");
        kpiTasks.clearSeries();

        StorageInfo storageInfo = storageInfo();
        kpiStorage.setValueText(storageInfo.value());
        kpiStorage.setSubText(storageInfo.sub());
        kpiStorage.clearSeries();

        if (lastBackupAt == null) {
            kpiBackup.setValueText("Not configured");
            kpiBackup.setSubText("no backups yet");
        } else {
            kpiBackup.setValueText(INSTANT_FMT.format(lastBackupAt.atZone(ZoneId.systemDefault())));
            kpiBackup.setSubText(lastBackupName == null ? "backup" : lastBackupName);
        }
        kpiBackup.clearSeries();

        LanInfo lanInfo = lanInfo();
        kpiLan.setValueText(lanInfo.value());
        kpiLan.setSubText(lanInfo.sub());
        kpiLan.clearSeries();

        // Perf tiles
        if (avgReloadMs <= 0.0) {
            kpiReload.setValueText("-");
            kpiReload.setSubText("no data");
        } else {
            kpiReload.setValueText(String.format("%.0f ms", avgReloadMs));
            kpiReload.setSubText("moving avg");
        }
        kpiReload.clearSeries();

        kpiErrors.setValueText(Integer.toString(reloadErrors));
        kpiErrors.setSubText("since start");
        kpiErrors.clearSeries();

        // Role donut
        Map<String, Integer> roleMap = new LinkedHashMap<>();
        roleMap.put("ADMIN", s.adminCount());
        roleMap.put("USER", s.userCount());
        roleDonut.setCenterText("Roles", s.totalUsers() + " users");
        roleDonut.setData(roleMap);

        // Presence bars (Online / Idle / Offline)
        var p = s.presence();
        presenceBars.setBars(List.of(
                new MiniBarChartView.Bar("Online", p.online()),
                new MiniBarChartView.Bar("Idle", p.idle()),
                new MiniBarChartView.Bar("Offline", p.offline())
        ));

        // Task status bars
        int todo = s.taskStatusCounts().getOrDefault(TaskStatus.TODO, 0);
        int prog = s.taskStatusCounts().getOrDefault(TaskStatus.IN_PROGRESS, 0);
        int blocked = s.taskStatusCounts().getOrDefault(TaskStatus.BLOCKED, 0);
        int done = s.taskStatusCounts().getOrDefault(TaskStatus.DONE, 0);

        taskStatusBars.setBars(List.of(
                new MiniBarChartView.Bar("ToDo", todo),
                new MiniBarChartView.Bar("Prog", prog),
                new MiniBarChartView.Bar("Block", blocked),
                new MiniBarChartView.Bar("Done", done)
        ));

        // Activity sparkline (14d)
        int sum14 = AdminMetrics.sum(s.lastOnlineBins14d());
        int today = AdminMetrics.last(s.lastOnlineBins14d());

        activity14d.setValueText(today + " today");
        activity14d.setSubText(sum14 + " events / 14d");
        activity14d.setSeries(Arrays.stream(s.lastOnlineBins14d()).boxed().toList());
    }

    private void startPresenceTicker() {
        presenceTicker = new Timeline(new KeyFrame(Duration.seconds(20), e -> {
            // Recompute presence status from lastOnlineAt without reloading DB
            refreshAdminDashboard();
            // If you want the UserHealth statuses to “age” live, rebuild health rows:
            refreshHealthRows();
        }));
        presenceTicker.setCycleCount(Timeline.INDEFINITE);
        presenceTicker.play();
    }

    // --------------------------------------------------------------------------------------------
    // UI BUILDERS
    // --------------------------------------------------------------------------------------------

    private Node buildUserDetailDrawer(Node innerDrawer) {

        ScrollPane body = new ScrollPane(innerDrawer);
        body.setFitToWidth(true);
        body.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        body.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        body.getStyleClass().add("pp-scroll");
        body.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        Region backboard = new Region();
        backboard.getStyleClass().add("pp-drawer-backboard");

        Region accent = new Region();
        accent.getStyleClass().add("pp-drawer-accent");

        VBox front = new VBox(10, accent, body);
        front.setPadding(new Insets(12));
        front.setFillWidth(true);
        front.setMaxWidth(Double.MAX_VALUE);
        front.setMaxHeight(Double.MAX_VALUE);

        StackPane shell = new StackPane(backboard, front);
        shell.getStyleClass().add("pp-drawer-shell");
        shell.setPrefWidth(390);
        shell.setMinWidth(360);
        shell.setMaxWidth(460);

        // stops click-through to table
        shell.setPickOnBounds(true);

        // Make backboard fill the shell
        backboard.prefWidthProperty().bind(shell.widthProperty());
        backboard.prefHeightProperty().bind(shell.heightProperty());

        return shell;
    }

    private Node buildIntegrityPanel() {
        Label title = new Label("Data integrity checks");
        title.getStyleClass().add("section-title");

        integrityFlow.getStyleClass().add("admin-integrity");
        integrityFlow.setHgap(10);
        integrityFlow.setVgap(10);
        integrityFlow.setPrefWrapLength(900);

        integritySummary.getStyleClass().add("muted");

        Button scan = new Button("Scan DB");
        scan.getStyleClass().add("subtle");
        scan.setOnAction(e -> {
            runIntegrityScan();
            addAudit("Scan DB", "Integrity scan executed");
            status.setText("✅ Integrity scan completed.");
        });

        HBox header = new HBox(10, title, scan, new Region(), integritySummary);
        HBox.setHgrow(header.getChildren().get(3), Priority.ALWAYS);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(10, header, integrityFlow);
        box.setPadding(new Insets(12));
        box.getStyleClass().add("pp-card");

        runIntegrityScan();
        return box;
    }

    private Node buildAlertsBanner() {
        Label title = new Label("Alerts");
        title.getStyleClass().add("section-title");

        alertsFlow.getStyleClass().add("admin-alerts");
        alertsFlow.setHgap(8);
        alertsFlow.setVgap(8);
        alertsFlow.setPrefWrapLength(700);

        VBox box = new VBox(10, title, alertsFlow);
        box.setPadding(new Insets(12));
        box.getStyleClass().add("pp-card");
        updateAlerts();
        return box;
    }

    private Node buildQuickActionsRow() {
        Label title = new Label("Quick actions");
        title.getStyleClass().add("section-title");

        Button createUser = new Button("Create user");
        createUser.getStyleClass().add("primary");
        createUser.setOnAction(e -> {
            scrollToNode(createUserBox);
            if (createUserUsernameField != null) createUserUsernameField.requestFocus();
        });

        Button resetPassword = new Button("Reset password");
        resetPassword.getStyleClass().add("subtle");
        resetPassword.setOnAction(e -> showResetPasswordDialog());

        Button exportUsers = new Button("Export users");
        exportUsers.getStyleClass().add("ghost");
        exportUsers.setOnAction(e -> exportUsers());

        Button deactivate = new Button("Deactivate");
        deactivate.getStyleClass().add("danger-outline");
        deactivate.setOnAction(e -> deactivateSelectedUser());

        Button backup = new Button("Backup DB");
        backup.getStyleClass().add("ghost");
        backup.setOnAction(e -> backupDatabase());

        Button scan = new Button("Scan DB");
        scan.getStyleClass().add("subtle");
        scan.setOnAction(e -> {
            runIntegrityScan();
            updateAlerts();
            addAudit("Scan DB", "Triggered from quick actions");
            status.setText("✅ Integrity scan completed.");
        });

        HBox actions = new HBox(10, createUser, resetPassword, exportUsers, deactivate, backup, scan);
        actions.getStyleClass().add("admin-quick-actions");
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(10, title, actions);
        box.setPadding(new Insets(12));
        box.getStyleClass().add("pp-card");
        return box;
    }

    private Node buildCreateUserBox() {
        TextField name = new TextField();
        name.setPromptText("Display name (optional)");

        TextField username = new TextField();
        username.setPromptText("Username");

        TextField email = new TextField();
        email.setPromptText("Email");

        PasswordField password = new PasswordField();
        password.setPromptText("Password");

        ComboBox<GlobalRole> globalRole = new ComboBox<>();
        globalRole.getItems().setAll(GlobalRole.USER, GlobalRole.ADMIN);
        globalRole.setValue(GlobalRole.USER);

        ComboBox<ProjectRole> projectRole = new ComboBox<>();
        projectRole.getItems().setAll(ProjectRole.LEADER, ProjectRole.MEMBER, ProjectRole.VIEWER);
        projectRole.setValue(ProjectRole.MEMBER);

        Label projectRoleLbl = new Label("Project role");
        var showProjectRole = Bindings.createBooleanBinding(
                () -> globalRole.getValue() == GlobalRole.USER,
                globalRole.valueProperty()
        );

        projectRoleLbl.visibleProperty().bind(showProjectRole);
        projectRoleLbl.managedProperty().bind(projectRoleLbl.visibleProperty());
        projectRole.visibleProperty().bind(showProjectRole);
        projectRole.managedProperty().bind(projectRole.visibleProperty());

        Label hint = new Label();
        hint.getStyleClass().add("muted");
        hint.textProperty().bind(Bindings.createStringBinding(() -> {
            Project p = appState.getSelectedProject();
            if (globalRole.getValue() != GlobalRole.USER) return "";
            if (p == null) return "No project selected — user will be created, but not added to a project.";
            return "User will be added to current project: " + p.getName();
        }, globalRole.valueProperty(), appState.selectedProjectProperty()));
        hint.visibleProperty().bind(showProjectRole);
        hint.managedProperty().bind(hint.visibleProperty());

        Button create = new Button("Create user");
        create.setOnAction(e -> {
            final String uname = username.getText() == null ? "" : username.getText().trim();
            final String disp = name.getText() == null ? "" : name.getText().trim();
            final String em = email.getText() == null ? "" : email.getText().trim();

            try {
                if (uname.isBlank()) throw new IllegalArgumentException("Username is required.");
                if (em.isBlank() || !em.contains("@")) throw new IllegalArgumentException("Valid email is required.");
                if (password.getText() == null || password.getText().isBlank()) {
                    throw new IllegalArgumentException("Password is required.");
                }

                admin.createUserWithEmailAndUsername(disp, uname, em, password.getText(), globalRole.getValue());
                reload();

                if (globalRole.getValue() == GlobalRole.USER) {
                    Project p = appState.getSelectedProject();
                    if (p != null) {
                        var created = items.stream()
                                .filter(r -> r.username() != null && r.username().equalsIgnoreCase(uname))
                                .findFirst()
                                .orElse(null);

                        if (created != null) {
                            boolean already = p.getMembers().stream().anyMatch(m -> created.id().equals(m.getId()));
                            if (!already) {
                                String displayName = !disp.isBlank() ? disp : uname;

                                store.addMember(p, new Member(created.id(), displayName, projectRole.getValue()));
                                admin.upsertProjectRole(p.getId(), created.id(), projectRole.getValue());
                            }
                        }
                    }
                }

                name.clear();
                username.clear();
                email.clear();
                password.clear();
                globalRole.setValue(GlobalRole.USER);
                projectRole.setValue(ProjectRole.MEMBER);

                status.setText("✅ User created.");
                addAudit("Create user", "Username: " + uname);
            } catch (Exception ex) {
                status.setText("❌ " + ex.getMessage());
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        int r = 0;
        grid.addRow(r++, new Label("Name"), name);
        grid.addRow(r++, new Label("Username"), username);
        grid.addRow(r++, new Label("Email"), email);
        grid.addRow(r++, new Label("Password"), password);
        grid.addRow(r++, new Label("Global role"), globalRole);
        grid.addRow(r++, projectRoleLbl, projectRole);
        grid.add(hint, 1, r++);

        ColumnConstraints c1 = new ColumnConstraints();
        c1.setMinWidth(90);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(javafx.scene.layout.Priority.ALWAYS);
        grid.getColumnConstraints().setAll(c1, c2);

        HBox actions = new HBox(10, create);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(10, new Label("Create login user"), grid, actions);
        box.setPadding(new Insets(12));
        box.getStyleClass().add("pp-card");
        this.createUserUsernameField = username;
        this.createUserBox = box;
        return box;
    }

    private void installWidgetDetails(Node node, String title, Supplier<Node> contentFactory) {
        if (node == null) return;

        node.setOnMouseClicked(e -> {
            if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) return;
            if (e.getClickCount() != 1) return;

            Dialog<Void> dlg = new Dialog<>();
            dlg.setTitle(title);

            DialogPane pane = dlg.getDialogPane();
            pane.getButtonTypes().add(ButtonType.CLOSE);
            pane.getStyleClass().add("pp-dialog"); // optional CSS hook

            Node content = contentFactory.get();
            if (content instanceof Region r) {
                r.setMaxWidth(Double.MAX_VALUE);
            }

            pane.setContent(content);

            // make it feel like a card
            pane.setPrefWidth(620);

            dlg.showAndWait();
            e.consume();
        });
    }


    private Node buildCreateTeamBox() {
        Label title = new Label("Create team");
        title.getStyleClass().add("section-title");

        Label hint = new Label("Use existing users to build teams you can assign to projects.");
        hint.getStyleClass().add("muted");

        Button create = new Button("Create team");
        create.getStyleClass().add("primary");

        create.setOnAction(e -> {
            try {
                var directory = admin.listDirectoryUsers();
                if (directory.isEmpty()) {
                    status.setText("No users available to create a team.");
                    return;
                }

                var dlg = new CreateTeamDialog(directory);
                var res = dlg.showAndWait();
                if (res.isEmpty()) return;

                var data = res.get();
                admin.createTeam(data.name(), data.leaderId(), data.members());
                status.setText("✅ Team created: " + data.name());
                addAudit("Create team", "Team: " + data.name());
            } catch (Exception ex) {
                status.setText("❌ " + ex.getMessage());
            }
        });

        HBox actions = new HBox(10, create);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(10, title, hint, actions);
        box.setPadding(new Insets(12));
        box.getStyleClass().add("pp-card");
        return box;
    }

    private Node buildPermissionMatrix() {
        Label title = new Label("Permission matrix");
        title.getStyleClass().add("section-title");

        GridPane grid = new GridPane();
        grid.getStyleClass().add("perm-grid");
        grid.setHgap(8);
        grid.setVgap(6);

        String[] headers = {
                "Role",
                "View",
                "Create tasks",
                "Edit own",
                "Edit any",
                "Manage members",
                "Project settings",
                "Manage users"
        };

        for (int c = 0; c < headers.length; c++) {
            grid.add(permHeader(headers[c]), c, 0);
        }

        int row = 1;
        addPermissionRow(grid, row++, "Viewer", new boolean[]{true, false, false, false, false, false, false});
        addPermissionRow(grid, row++, "Member", new boolean[]{true, true, true, false, false, false, false});
        addPermissionRow(grid, row++, "Leader", new boolean[]{true, true, true, true, true, true, false});
        addPermissionRow(grid, row++, "Admin", new boolean[]{true, true, true, true, true, true, true});

        VBox box = new VBox(10, title, grid);
        box.setPadding(new Insets(12));
        box.getStyleClass().add("pp-card");
        return box;
    }

    private Node buildUserHealthTable() {
        Label title = new Label("User health");
        title.getStyleClass().add("section-title");

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> reload());

        TableView<UserHealthRow> table = new TableView<>(healthItems);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        table.setRowFactory(tv -> {
            TableRow<UserHealthRow> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (row.isEmpty()) return;
                if (ev.getButton() != MouseButton.PRIMARY) return;
                if (ev.getClickCount() < 1) return;
                UserHealthRow hr = row.getItem();
                if (hr != null && hr.user != null) openDrawer(hr.user);
            });
            return row;
        });

        TableColumn<UserHealthRow, String> colUser = new TableColumn<>("User");
        colUser.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().displayName()));
        colUser.setCellFactory(tc -> new TableCell<>() {
            private final Label name = new Label();
            private final Label meta = new Label();
            private final VBox box = new VBox(2, name, meta);

            {
                name.getStyleClass().add("admin-user-name");
                meta.getStyleClass().add("admin-user-meta");
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                var row = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                name.setText(row.displayName());
                String uname = row.username();
                if (uname == null || uname.isBlank() || uname.equalsIgnoreCase(row.displayName())) {
                    meta.setText("");
                    meta.setManaged(false);
                    meta.setVisible(false);
                } else {
                    meta.setText("@" + uname);
                    meta.setManaged(true);
                    meta.setVisible(true);
                }
                setGraphic(box);
            }
        });

        TableColumn<UserHealthRow, String> colRole = new TableColumn<>("Role");
        colRole.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().role()));

        TableColumn<UserHealthRow, String> colLast = new TableColumn<>("Last online");
        colLast.setCellValueFactory(cd -> new ReadOnlyStringWrapper(formatLastOnline(cd.getValue().lastOnlineAt())));

        TableColumn<UserHealthRow, String> colProjects = new TableColumn<>("Projects");
        colProjects.setCellValueFactory(cd -> new ReadOnlyStringWrapper(Integer.toString(cd.getValue().projectCount())));

        TableColumn<UserHealthRow, String> colTasks = new TableColumn<>("Tasks");
        colTasks.setCellValueFactory(cd -> new ReadOnlyStringWrapper(Integer.toString(cd.getValue().taskCount())));

        TableColumn<UserHealthRow, String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().statusLabel()));
        colStatus.setCellFactory(tc -> new TableCell<>() {
            private final Label chip = new Label();
            { chip.getStyleClass().add("status-chip"); }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                var row = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                chip.setText(row.statusLabel());
                chip.getStyleClass().setAll("status-chip", row.statusClass());
                setGraphic(chip);
            }
        });

        table.getColumns().setAll(new TableColumn[]{colUser, colRole, colLast, colProjects, colTasks, colStatus});

        HBox header = new HBox(10, title, refresh);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(10, header, table);
        box.setPadding(new Insets(12));
        box.getStyleClass().add("pp-card");
        VBox.setVgrow(table, Priority.ALWAYS);
        return box;
    }

    private Node buildUsersTable() {
        TableView<UserAdminService.UserRow> table = new TableView<>(items);
        this.usersTable = table;
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) openDrawer(n);
        });

        TableColumn<UserAdminService.UserRow, String> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().id()));
        colId.setMinWidth(220);
        colId.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                setText(shortId(item));
                setTooltip(new Tooltip(item));
            }
        });

        TableColumn<UserAdminService.UserRow, String> colUser = new TableColumn<>("Username");
        colUser.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().username()));

        TableColumn<UserAdminService.UserRow, String> colName = new TableColumn<>("Name");
        colName.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().name()));

        TableColumn<UserAdminService.UserRow, String> colEmail = new TableColumn<>("Email");
        colEmail.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().email()));

        TableColumn<UserAdminService.UserRow, String> colLastOnline = new TableColumn<>("Last online");
        colLastOnline.setMinWidth(140);
        colLastOnline.setCellValueFactory(cd -> new ReadOnlyStringWrapper(formatLastOnline(cd.getValue().lastOnlineAt())));

        TableColumn<UserAdminService.UserRow, String> colRole = new TableColumn<>("Role");
        colRole.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().globalRole().name()));

        TableColumn<UserAdminService.UserRow, Boolean> colActive = new TableColumn<>("Active");
        colActive.setCellValueFactory(cd -> new ReadOnlyBooleanWrapper(cd.getValue().active()));
        colActive.setCellFactory(tc -> new TableCell<>() {
            private final CheckBox cb = new CheckBox();
            private boolean internal;

            {
                cb.selectedProperty().addListener((obs, old, val) -> {
                    if (internal) return;
                    var row = getTableRow() == null ? null : getTableRow().getItem();
                    if (row == null) return;

                    try {
                        admin.setUserActive(row.id(), val);
                        status.setText("✅ Updated active for " + row.username());
                        addAudit("Set active", "User " + row.username() + " -> " + (val ? "active" : "inactive"));
                        reload();
                    } catch (Exception ex) {
                        status.setText("❌ " + ex.getMessage());
                        reload();
                    }
                });
            }

            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                internal = true;
                cb.setSelected(Boolean.TRUE.equals(item));
                internal = false;
                setGraphic(cb);
            }
        });

        TableColumn<UserAdminService.UserRow, Void> colActions = new TableColumn<>("Actions");
        colActions.setMinWidth(240);
        colActions.setCellFactory(tc -> new TableCell<>() {
            private final Button edit = new Button("Edit");
            private final Button delete = new Button("Delete");
            private final HBox box = new HBox(10, edit, delete);

            {
                box.setAlignment(Pos.CENTER_LEFT);

                edit.setOnAction(e -> {
                    var row = getTableRow() == null ? null : getTableRow().getItem();
                    if (row == null) return;

                    Project p = appState.getSelectedProject();
                    String projectName = (p == null) ? "" : p.getName();

                    final ProjectRole currentRole = resolveUserRoleInSelectedProject(row.id());

                    var dlg = new EditUserDialog(row, projectName, currentRole);
                    var res = dlg.showAndWait();
                    if (res.isEmpty()) return;

                    try {
                        var data = res.get();

                        admin.updateUser(
                                row.id(),
                                data.displayName(),
                                data.username(),
                                data.email(),
                                data.newPassword(),
                                data.globalRole(),
                                data.active()
                        );

                        ProjectRole roleFinal = (data.projectRole() == null) ? currentRole : data.projectRole();

                        if (p != null) {
                            admin.upsertProjectRole(p.getId(), row.id(), roleFinal);

                            boolean found = false;
                            for (Member m : p.getMembers()) {
                                if (row.id().equals(m.getId())) {
                                    String newName = (data.displayName() == null || data.displayName().isBlank())
                                            ? data.username()
                                            : data.displayName();

                                    m.nameProperty().set(newName);
                                    m.roleProperty().set(roleFinal);
                                    found = true;
                                    break;
                                }
                            }
                            if (!found && data.globalRole() == GlobalRole.USER) {
                                String newName = (data.displayName() == null || data.displayName().isBlank())
                                        ? data.username()
                                        : data.displayName();
                                store.addMember(p, new Member(row.id(), newName, roleFinal));
                            }
                        }

                        status.setText("✅ Updated " + data.username());
                        addAudit("Update user", "Updated " + data.username());
                        reload();
                    } catch (Exception ex) {
                        status.setText("❌ " + ex.getMessage());
                        reload();
                    }
                });

                delete.setOnAction(e -> {
                    var row = getTableRow() == null ? null : getTableRow().getItem();
                    if (row == null) return;

                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                    confirm.setTitle("Delete account");
                    confirm.setHeaderText("Delete login account: " + row.username() + "?");
                    confirm.setContentText("This removes the login account. Member + project data is kept.");
                    if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

                    try {
                        admin.deleteUser(row.id());
                        status.setText("✅ Deleted account for " + row.username());
                        addAudit("Delete user", "Deleted " + row.username());
                        closeDrawerIf(row.id());
                        reload();
                    } catch (Exception ex) {
                        status.setText("❌ " + ex.getMessage());
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        table.getColumns().setAll(new TableColumn[]{colId, colUser, colName, colEmail, colLastOnline, colRole, colActive, colActions});

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> reload());

        VBox box = new VBox(10, new HBox(10, new Label("Login users"), refresh), table);
        box.setPadding(new Insets(12));
        box.getStyleClass().add("pp-card");
        VBox.setVgrow(table, Priority.ALWAYS);
        return box;
    }

    private Node buildAuditPanel() {
        Label title = new Label("Audit trail");
        title.getStyleClass().add("section-title");

        ListView<AuditEntry> list = new ListView<>(auditItems);
        list.getStyleClass().add("audit-list");
        list.setPrefHeight(220);
        list.setCellFactory(lv -> new ListCell<>() {
            private final Label heading = new Label();
            private final Label detail = new Label();
            private final Label time = new Label();
            private final VBox box = new VBox(2, heading, detail, time);

            {
                heading.getStyleClass().add("audit-title");
                detail.getStyleClass().add("audit-detail");
                time.getStyleClass().add("audit-time");
                box.getStyleClass().add("audit-row");
            }

            @Override
            protected void updateItem(AuditEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                heading.setText(item.title());
                detail.setText(item.detail());
                time.setText(AUDIT_FMT.format(item.at().atZone(ZoneId.systemDefault())));
                setGraphic(box);
            }
        });

        VBox box = new VBox(10, title, list);
        box.setPadding(new Insets(12));
        box.getStyleClass().add("pp-card");
        return box;
    }

    private Node buildUserDrawer() {
        VBox drawer = new VBox(10);
        drawer.getStyleClass().add("admin-drawer");
        drawer.setPadding(new Insets(12));
        drawer.setPrefWidth(360);
        drawer.setMaxWidth(360);

        drawerTitle = new Label("User");
        drawerTitle.getStyleClass().add("section-title");

        drawerMeta = new Label();
        drawerMeta.getStyleClass().add("muted");

        drawerStatusChip = new Label();
        drawerStatusChip.getStyleClass().add("status-chip");

        Button close = new Button("Close");
        close.getStyleClass().add("ghost");
        close.setOnAction(e -> closeDrawer());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(10, drawerTitle, spacer, close);
        header.setAlignment(Pos.CENTER_LEFT);

        drawerDisplayName = new TextField();
        drawerDisplayName.setPromptText("Display name");

        drawerUsername = new TextField();
        drawerUsername.setPromptText("Username");

        drawerEmail = new TextField();
        drawerEmail.setPromptText("Email");

        drawerNewPassword = new PasswordField();
        drawerNewPassword.setPromptText("New password (leave blank to keep)");

        drawerGlobalRole = new ComboBox<>();
        drawerGlobalRole.getItems().setAll(GlobalRole.USER, GlobalRole.ADMIN);

        drawerProjectRole = new ComboBox<>();
        drawerProjectRole.getItems().setAll(ProjectRole.LEADER, ProjectRole.MEMBER, ProjectRole.VIEWER);

        drawerActive = new CheckBox("Active");

        drawerProjectsCount = new Label("0");
        drawerTasksCount = new Label("0");
        drawerProjectHint = new Label();
        drawerProjectHint.getStyleClass().add("muted");

        Label projRoleLbl = new Label("Project role");
        BooleanBinding showProjRole = Bindings.createBooleanBinding(() -> {
            UserAdminService.UserRow u = selectedUser.get();
            if (u == null) return false;
            if (drawerGlobalRole.getValue() != GlobalRole.USER) return false;
            return appState.getSelectedProject() != null;
        }, selectedUser, drawerGlobalRole.valueProperty(), appState.selectedProjectProperty());

        projRoleLbl.visibleProperty().bind(showProjRole);
        projRoleLbl.managedProperty().bind(projRoleLbl.visibleProperty());
        drawerProjectRole.visibleProperty().bind(showProjRole);
        drawerProjectRole.managedProperty().bind(drawerProjectRole.visibleProperty());
        drawerProjectHint.visibleProperty().bind(showProjRole);
        drawerProjectHint.managedProperty().bind(drawerProjectHint.visibleProperty());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        int r = 0;
        grid.addRow(r++, new Label("Name"), drawerDisplayName);
        grid.addRow(r++, new Label("Username"), drawerUsername);
        grid.addRow(r++, new Label("Email"), drawerEmail);
        grid.addRow(r++, new Label("Global role"), drawerGlobalRole);
        grid.addRow(r++, projRoleLbl, drawerProjectRole);
        grid.add(drawerProjectHint, 1, r++);
        grid.addRow(r++, new Label("New password"), drawerNewPassword);
        grid.addRow(r++, new Label(""), drawerActive);

        ColumnConstraints c1 = new ColumnConstraints();
        c1.setMinWidth(95);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().setAll(c1, c2);

        HBox stats = new HBox(12,
                pill("Projects", drawerProjectsCount),
                pill("Tasks", drawerTasksCount),
                drawerStatusChip
        );
        stats.setAlignment(Pos.CENTER_LEFT);

        Button save = new Button("Save");
        save.getStyleClass().add("primary");
        save.setOnAction(e -> saveDrawerChanges());

        Button resetPw = new Button("Reset password");
        resetPw.getStyleClass().add("subtle");
        resetPw.setOnAction(e -> {
            if (drawerNewPassword.getText() != null && !drawerNewPassword.getText().isBlank()) {
                saveDrawerChanges();
            } else {
                showResetPasswordDialog();
            }
        });

        Button deactivate = new Button("Deactivate");
        deactivate.getStyleClass().add("danger-outline");
        deactivate.setOnAction(e -> {
            UserAdminService.UserRow u = selectedUser.get();
            if (u == null) return;
            try {
                admin.setUserActive(u.id(), false);
                addAudit("Deactivate user", "User: " + u.username());
                status.setText("✅ Deactivated " + u.username());
                reload();
            } catch (Exception ex) {
                status.setText("❌ " + ex.getMessage());
            }
        });

        HBox actions = new HBox(10, save, resetPw, deactivate);
        actions.setAlignment(Pos.CENTER_LEFT);

        Separator sep = new Separator();

        VBox metaBox = new VBox(4, drawerMeta, stats);

        drawer.getChildren().setAll(header, metaBox, sep, grid, actions);
        return drawer;
    }

    private Node pill(String label, Label value) {
        Label l = new Label(label + ": ");
        l.getStyleClass().add("muted");
        value.getStyleClass().add("pill-value");
        HBox box = new HBox(4, l, value);
        box.getStyleClass().add("pill");
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private Label permHeader(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("perm-header");
        return label;
    }

    private Label permRole(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("perm-role");
        return label;
    }

    private Label permCell(boolean allowed) {
        Label label = new Label(allowed ? "Yes" : "No");
        label.getStyleClass().add(allowed ? "perm-yes" : "perm-no");
        return label;
    }

    private void addPermissionRow(GridPane grid, int row, String role, boolean[] values) {
        grid.add(permRole(role), 0, row);
        for (int i = 0; i < values.length; i++) grid.add(permCell(values[i]), i + 1, row);
    }

    // --------------------------------------------------------------------------------------------
    // ACTIONS
    // --------------------------------------------------------------------------------------------

    private void openDrawer(UserAdminService.UserRow user) {
        if (user == null) return;
        selectedUser.set(user);
    }

    private void closeDrawer() {
        selectedUser.set(null);
        if (usersTable != null) usersTable.getSelectionModel().clearSelection();
    }

    private void closeDrawerIf(String userId) {
        UserAdminService.UserRow u = selectedUser.get();
        if (u != null && Objects.equals(u.id(), userId)) closeDrawer();
    }

    private void populateDrawer(UserAdminService.UserRow u) {
        drawerTitle.setText(u.username() == null ? "User" : ("@" + u.username()));
        drawerMeta.setText("ID: " + shortId(u.id()) + "  •  " + (u.email() == null ? "" : u.email()));

        StatusInfo si = statusFor(u);
        drawerStatusChip.setText(si.label());
        drawerStatusChip.getStyleClass().setAll("status-chip", si.styleClass());

        drawerDisplayName.setText(u.name() == null ? "" : u.name());
        drawerUsername.setText(u.username() == null ? "" : u.username());
        drawerEmail.setText(u.email() == null ? "" : u.email());
        drawerNewPassword.clear();
        drawerGlobalRole.setValue(u.globalRole());
        drawerActive.setSelected(u.active());

        Project p = appState.getSelectedProject();
        if (p != null) {
            drawerProjectHint.setText("Applies to project: " + p.getName());
            ProjectRole pr = resolveUserRoleInSelectedProject(u.id());
            drawerProjectRole.setValue(pr);
        } else {
            drawerProjectHint.setText("No project selected.");
            drawerProjectRole.setValue(ProjectRole.MEMBER);
        }

        var counts = computeUserCounts(u.id());
        drawerProjectsCount.setText(Integer.toString(counts.projects));
        drawerTasksCount.setText(Integer.toString(counts.tasks));
    }

    private void saveDrawerChanges() {
        UserAdminService.UserRow u = selectedUser.get();
        if (u == null) return;

        String newName = drawerDisplayName.getText() == null ? "" : drawerDisplayName.getText().trim();
        String newUsername = drawerUsername.getText() == null ? "" : drawerUsername.getText().trim();
        String newEmail = drawerEmail.getText() == null ? "" : drawerEmail.getText().trim();
        GlobalRole newGlobal = drawerGlobalRole.getValue() == null ? u.globalRole() : drawerGlobalRole.getValue();
        boolean newActive = drawerActive.isSelected();

        String pw = drawerNewPassword.getText();
        String newPassword = (pw == null || pw.isBlank()) ? null : pw;

        try {
            if (newUsername.isBlank()) throw new IllegalArgumentException("Username is required.");
            if (newEmail.isBlank() || !newEmail.contains("@")) throw new IllegalArgumentException("Valid email is required.");

            admin.updateUser(
                    u.id(),
                    newName,
                    newUsername,
                    newEmail,
                    newPassword,
                    newGlobal,
                    newActive
            );

            Project p = appState.getSelectedProject();
            if (p != null && newGlobal == GlobalRole.USER) {
                ProjectRole roleFinal = drawerProjectRole.getValue() == null ? ProjectRole.MEMBER : drawerProjectRole.getValue();
                admin.upsertProjectRole(p.getId(), u.id(), roleFinal);

                boolean found = false;
                for (Member m : p.getMembers()) {
                    if (u.id().equals(m.getId())) {
                        String nm = newName.isBlank() ? newUsername : newName;
                        m.nameProperty().set(nm);
                        m.roleProperty().set(roleFinal);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    String nm = newName.isBlank() ? newUsername : newName;
                    store.addMember(p, new Member(u.id(), nm, roleFinal));
                }
            }

            status.setText("✅ Saved user changes.");
            addAudit("Update user", "Updated " + newUsername);
            reload();

            UserAdminService.UserRow refreshed = items.stream()
                    .filter(x -> Objects.equals(x.id(), u.id()))
                    .findFirst()
                    .orElse(u);
            selectedUser.set(refreshed);

        } catch (Exception ex) {
            status.setText("❌ " + ex.getMessage());
        }
    }

    private void showResetPasswordDialog() {
        if (items.isEmpty()) {
            status.setText("No users available to reset.");
            return;
        }

        Dialog<ResetPasswordData> dialog = new Dialog<>();
        dialog.setTitle("Reset password");

        ButtonType resetType = new ButtonType("Reset", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(resetType, ButtonType.CANCEL);

        ComboBox<UserAdminService.UserRow> userBox = new ComboBox<>();
        userBox.getItems().setAll(items);
        userBox.setMaxWidth(Double.MAX_VALUE);

        UserAdminService.UserRow pre = selectedUser.get();
        userBox.setValue(pre != null ? pre : items.get(0));

        userBox.setConverter(new StringConverter<>() {
            @Override public String toString(UserAdminService.UserRow object) { return userLabel(object); }
            @Override public UserAdminService.UserRow fromString(String string) { return userBox.getValue(); }
        });

        PasswordField pw = new PasswordField();
        pw.setPromptText("New password");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.addRow(0, new Label("User"), userBox);
        grid.addRow(1, new Label("Password"), pw);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setMinWidth(90);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().setAll(c1, c2);

        dialog.getDialogPane().setContent(grid);
        Node okBtn = dialog.getDialogPane().lookupButton(resetType);
        okBtn.disableProperty().bind(pw.textProperty().isEmpty());

        dialog.setResultConverter(btn -> btn != resetType ? null : new ResetPasswordData(userBox.getValue(), pw.getText()));

        var res = dialog.showAndWait();
        if (res.isEmpty()) return;

        ResetPasswordData data = res.get();
        if (data.user == null) return;

        try {
            var row = data.user;
            admin.updateUser(
                    row.id(),
                    row.name(),
                    row.username(),
                    row.email(),
                    data.password,
                    row.globalRole(),
                    row.active()
            );
            status.setText("✅ Password reset for " + row.username());
            addAudit("Reset password", "User: " + row.username());
            reload();
        } catch (Exception ex) {
            status.setText("❌ " + ex.getMessage());
        }
    }

    private void exportUsers() {
        if (items.isEmpty()) {
            status.setText("No users to export.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export users");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        chooser.setInitialFileName("projectpilot-users.csv");

        File file = chooser.showSaveDialog(getScene() == null ? null : getScene().getWindow());
        if (file == null) return;

        StringBuilder sb = new StringBuilder();
        sb.append("id,username,name,email,role,active,last_online\n");
        for (UserAdminService.UserRow row : items) {
            sb.append(escapeCsv(row.id())).append(',')
                    .append(escapeCsv(row.username())).append(',')
                    .append(escapeCsv(row.name())).append(',')
                    .append(escapeCsv(row.email())).append(',')
                    .append(escapeCsv(row.globalRole() == null ? "" : row.globalRole().name())).append(',')
                    .append(row.active()).append(',')
                    .append(escapeCsv(formatLastOnline(row.lastOnlineAt())))
                    .append("\n");
        }

        try {
            Files.writeString(file.toPath(), sb.toString());
            status.setText("✅ Exported users to " + file.getName());
            addAudit("Export users", file.getName());
        } catch (Exception ex) {
            status.setText("❌ " + ex.getMessage());
        }
    }

    private void deactivateSelectedUser() {
        UserAdminService.UserRow row = null;

        if (usersTable != null) row = usersTable.getSelectionModel().getSelectedItem();
        if (row == null) row = selectedUser.get();
        if (row == null) { status.setText("Select a user to deactivate."); return; }
        if (!row.active()) { status.setText("User is already inactive."); return; }

        try {
            admin.setUserActive(row.id(), false);
            status.setText("✅ Deactivated " + row.username());
            addAudit("Deactivate user", "User: " + row.username());
            reload();
        } catch (Exception ex) {
            status.setText("❌ " + ex.getMessage());
        }
    }

    private void backupDatabase() {
        if (!(store instanceof DbStore dbStore)) {
            status.setText("❌ Backup is available only for DbStore.");
            return;
        }
        Path dbFile = dbStore.manager().dbFile();
        if (dbFile == null || !Files.exists(dbFile)) {
            status.setText("❌ DB file not found for backup.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Backup database");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite DB", "*.db", "*.sqlite", "*.sqlite3"));
        chooser.setInitialFileName("projectpilot-backup-" + LocalDate.now() + ".db");

        File dest = chooser.showSaveDialog(getScene() == null ? null : getScene().getWindow());
        if (dest == null) return;

        try {
            Files.copy(dbFile, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            lastBackupAt = Instant.now();
            lastBackupName = dest.getName();
            status.setText("✅ Backup created: " + dest.getName());
            addAudit("Backup DB", dest.getName());
            refreshAdminDashboard();
        } catch (Exception ex) {
            status.setText("❌ " + ex.getMessage());
        }
    }

    // --------------------------------------------------------------------------------------------
    // ALERTS + INTEGRITY
    // --------------------------------------------------------------------------------------------

    private void updateAlerts() {
        alertsFlow.getChildren().clear();

        int dupUsers = countDuplicateUsers();
        int orphanTasks = countOrphanTasks();
        int unassigned = countUnassignedTasks();
        int overdue = countOverdueTasks();

        addAlertChip("Duplicate users", dupUsers, dupUsers > 0 ? "alert-danger" : "alert-muted");
        addAlertChip("Orphan tasks", orphanTasks, orphanTasks > 0 ? "alert-danger" : "alert-muted");
        addAlertChip("Unassigned tasks", unassigned, unassigned > 0 ? "alert-warn" : "alert-muted");
        addAlertChip("Overdue spike", overdue, overdue >= 5 ? "alert-danger" : overdue > 0 ? "alert-warn" : "alert-muted");

        if (dupUsers == 0 && orphanTasks == 0 && unassigned == 0 && overdue == 0) {
            Label ok = new Label("All clear");
            ok.getStyleClass().addAll("admin-alert-chip", "alert-ok");
            alertsFlow.getChildren().add(ok);
        }
    }

    private void addAlertChip(String label, int count, String styleClass) {
        Label chip = new Label(label + ": " + count);
        chip.getStyleClass().addAll("admin-alert-chip", styleClass);
        alertsFlow.getChildren().add(chip);
    }

    private void runIntegrityScan() {
        integrityFlow.getChildren().clear();

        int dupUsers = countDuplicateUsers();
        int orphanTasks = countOrphanTasks();
        int unassigned = countUnassignedTasks();
        int overdue = countOverdueTasks();

        int invalidMembers = countInvalidMembers();
        int blankTaskTitles = countBlankTaskTitles();
        int doneWithFutureDue = countDoneWithFutureDueDates();

        addIntegrityCard("Duplicate users", dupUsers, dupUsers > 0 ? "card-danger" : "card-muted");
        addIntegrityCard("Orphan tasks", orphanTasks, orphanTasks > 0 ? "card-danger" : "card-muted");
        addIntegrityCard("Unassigned tasks", unassigned, unassigned > 0 ? "card-warn" : "card-muted");
        addIntegrityCard("Overdue tasks", overdue, overdue > 0 ? "card-warn" : "card-muted");

        addIntegrityCard("Invalid members", invalidMembers, invalidMembers > 0 ? "card-warn" : "card-muted");
        addIntegrityCard("Blank task titles", blankTaskTitles, blankTaskTitles > 0 ? "card-warn" : "card-muted");
        addIntegrityCard("Done + future due", doneWithFutureDue, "card-muted");

        int issues = dupUsers + orphanTasks + unassigned + overdue + invalidMembers + blankTaskTitles;
        integritySummary.setText(issues == 0 ? "No major issues detected" : ("Issues detected: " + issues));

        // Run server/db validator asynchronously and display results as a card
        Thread validatorThread = new Thread(() -> {
            try {
                java.util.List<String> issuesList = admin.runDataValidator();
                Platform.runLater(() -> {
                    String style = (issuesList == null || issuesList.isEmpty()) ? "card-muted" : "card-danger";
                    int c = issuesList == null ? 0 : issuesList.size();
                    addIntegrityCard("Server validation", c, style);
                    Node card = integrityFlow.getChildren().get(integrityFlow.getChildren().size() - 1);
                    card.setOnMouseClicked(ev -> {
                        Alert dlg = new Alert(Alert.AlertType.INFORMATION);
                        dlg.initOwner(getScene() == null ? null : getScene().getWindow());
                        dlg.setTitle("Validation results");
                        dlg.setHeaderText(c == 0 ? "No issues found" : ("Validation issues (" + c + ")"));
                        String body = c == 0 ? "No issues detected." : String.join("\n", issuesList);
                        TextArea ta = new TextArea(body);
                        ta.setEditable(false);
                        ta.setWrapText(true);
                        ta.setPrefRowCount(Math.min(20, c + 2));
                        dlg.getDialogPane().setContent(ta);
                        dlg.showAndWait();
                    });
                });
            } catch (Exception e) {
                Platform.runLater(() -> addIntegrityCard("Server validation", 0, "card-warn"));
            }
        });
        validatorThread.setDaemon(true);
        validatorThread.start();
    }

    private void addIntegrityCard(String title, int count, String styleClass) {
        Label t = new Label(title);
        t.getStyleClass().add("integrity-title");
        Label v = new Label(Integer.toString(count));
        v.getStyleClass().add("integrity-value");

        VBox box = new VBox(4, t, v);
        box.getStyleClass().addAll("integrity-card", styleClass);
        box.setPadding(new Insets(10));
        box.setMinWidth(180);
        integrityFlow.getChildren().add(box);
    }

    private int countDuplicateUsers() {
        Map<String, Integer> usernames = new HashMap<>();
        Map<String, Integer> emails = new HashMap<>();
        for (UserAdminService.UserRow row : items) {
            String u = row.username() == null ? "" : row.username().trim().toLowerCase();
            if (!u.isBlank()) usernames.merge(u, 1, Integer::sum);
            String e = row.email() == null ? "" : row.email().trim().toLowerCase();
            if (!e.isBlank()) emails.merge(e, 1, Integer::sum);
        }
        int duplicates = 0;
        for (int c : usernames.values()) if (c > 1) duplicates += (c - 1);
        for (int c : emails.values()) if (c > 1) duplicates += (c - 1);
        return duplicates;
    }

    private int countOrphanTasks() {
        int count = 0;
        for (Project p : store.getProjects()) {
            Set<String> ids = new HashSet<>();
            for (Member m : p.getMembers()) if (m != null && m.getId() != null) ids.add(m.getId());
            for (Task t : p.getTasks()) {
                Member assignee = t == null ? null : t.getAssignee();
                if (assignee == null || assignee.getId() == null) continue;
                if (!ids.contains(assignee.getId())) count++;
            }
        }
        return count;
    }

    private int countUnassignedTasks() {
        int count = 0;
        for (Project p : store.getProjects()) {
            for (Task t : p.getTasks()) {
                if (t == null) continue;
                if (t.getAssignee() == null) count++;
            }
        }
        return count;
    }

    private int countOverdueTasks() {
        int count = 0;
        LocalDate today = LocalDate.now();
        for (Project p : store.getProjects()) {
            for (Task t : p.getTasks()) {
                if (t == null) continue;
                if (t.getDueDate() == null) continue;
                if (t.getStatus() == TaskStatus.DONE) continue;
                if (t.getDueDate().isBefore(today)) count++;
            }
        }
        return count;
    }

    private int countInvalidMembers() {
        int count = 0;
        for (Project p : store.getProjects()) {
            for (Member m : p.getMembers()) {
                if (m == null) { count++; continue; }
                String id = m.getId();
                if (id == null || id.trim().isBlank()) count++;
            }
        }
        return count;
    }

    private int countBlankTaskTitles() {
        int count = 0;
        for (Project p : store.getProjects()) {
            for (Task t : p.getTasks()) {
                if (t == null) continue;
                String label = safeTaskLabel(t);
                if (label == null || label.isBlank() || "Task".equals(label)) count++;
            }
        }
        return count;
    }

    private int countDoneWithFutureDueDates() {
        int count = 0;
        LocalDate today = LocalDate.now();
        for (Project p : store.getProjects()) {
            for (Task t : p.getTasks()) {
                if (t == null) continue;
                if (t.getStatus() != TaskStatus.DONE) continue;
                if (t.getDueDate() != null && t.getDueDate().isAfter(today)) count++;
            }
        }
        return count;
    }

    // --------------------------------------------------------------------------------------------
    // METRICS + RELOAD
    // --------------------------------------------------------------------------------------------

    private void refreshHealthRows() {
        Map<String, Integer> projectCounts = new HashMap<>();
        Map<String, Integer> taskCounts = new HashMap<>();

        List<Project> allProjects = new ArrayList<>();
        allProjects.addAll(store.getProjects());
        allProjects.addAll(store.getHistoryProjects());

        for (Project p : allProjects) {
            for (Member m : p.getMembers()) {
                if (m == null || m.getId() == null) continue;
                projectCounts.merge(m.getId(), 1, Integer::sum);
            }
            for (Task t : p.getTasks()) {
                Member assignee = t == null ? null : t.getAssignee();
                if (assignee == null || assignee.getId() == null) continue;
                taskCounts.merge(assignee.getId(), 1, Integer::sum);
            }
        }

        List<UserHealthRow> rows = new ArrayList<>();
        for (UserAdminService.UserRow row : items) {
            int pc = projectCounts.getOrDefault(row.id(), 0);
            int tc = taskCounts.getOrDefault(row.id(), 0);
            StatusInfo statusInfo = statusFor(row);
            rows.add(new UserHealthRow(row, pc, tc, statusInfo.label(), statusInfo.styleClass()));
        }
        healthItems.setAll(rows);
    }

    private void addAudit(String title, String detail) {
        if (title == null || title.isBlank()) return;
        String safeDetail = detail == null ? "" : detail;
        auditItems.add(0, new AuditEntry(title, safeDetail, Instant.now()));
        if (auditItems.size() > 40) auditItems.remove(auditItems.size() - 1);
    }

    private void reload() {
        long start = System.nanoTime();
        try {
            items.setAll(admin.listLoginUsers());
        } catch (Exception ex) {
            reloadErrors++;
            status.setText("❌ Failed to load users: " + ex.getMessage());
        } finally {
            long ms = Math.max(1, (System.nanoTime() - start) / 1_000_000);
            if (avgReloadMs <= 0.0) avgReloadMs = ms;
            else avgReloadMs = avgReloadMs * 0.75 + ms * 0.25;
        }

        refreshHealthRows();
        updateAlerts();
        runIntegrityScan();
        refreshAdminDashboard();

        UserAdminService.UserRow sel = selectedUser.get();
        if (sel != null) {
            UserAdminService.UserRow refreshed = items.stream()
                    .filter(x -> Objects.equals(x.id(), sel.id()))
                    .findFirst()
                    .orElse(sel);
            selectedUser.set(refreshed);
        }
    }

    // --------------------------------------------------------------------------------------------
    // HELPERS
    // --------------------------------------------------------------------------------------------

    private ProjectRole resolveUserRoleInSelectedProject(String userId) {
        Project p = appState.getSelectedProject();
        if (p == null || userId == null) return ProjectRole.MEMBER;
        try {
            Map<String, ProjectRole> roles = admin.rolesForUser(userId);
            ProjectRole r = roles.get(p.getId());
            return r == null ? ProjectRole.MEMBER : r;
        } catch (Exception e) { com.projectpilot.util.AppLog.warn("admin", "Failed to resolve user roles: " + (e == null ? "" : e.getMessage()));
            return ProjectRole.MEMBER;
        }
    }

    private static String safeTaskLabel(Task t) {
        if (t == null) return "";
        String[] methods = {"getTitle", "getName", "getSummary", "getDescription"};
        for (String m : methods) {
            try {
                var meth = t.getClass().getMethod(m);
                Object v = meth.invoke(t);
                if (v != null) {
                    String s = v.toString().trim();
                    if (!s.isBlank()) return s;
                }
            } catch (Exception e) { com.projectpilot.util.AppLog.warn("admin", "Failed reading task property via reflection: " + (e == null ? "" : e.getMessage())); }
        }
        try {
            var meth = t.getClass().getMethod("titleProperty");
            Object prop = meth.invoke(t);
            if (prop instanceof ObservableValue<?> ov) {
                Object v = ov.getValue();
                if (v != null) {
                    String s = v.toString().trim();
                    if (!s.isBlank()) return s;
                }
            }
        } catch (Exception e) { com.projectpilot.util.AppLog.warn("admin", "Failed reading task title via property reflection: " + (e == null ? "" : e.getMessage())); }
        return "Task";
    }

    private UserCounts computeUserCounts(String userId) {
        if (userId == null) return new UserCounts(0, 0);

        Set<String> projectIds = new HashSet<>();
        AtomicInteger tasks = new AtomicInteger(0);

        List<Project> allProjects = new ArrayList<>();
        allProjects.addAll(store.getProjects());
        allProjects.addAll(store.getHistoryProjects());

        for (Project p : allProjects) {
            for (Member m : p.getMembers()) {
                if (m != null && userId.equals(m.getId())) projectIds.add(p.getId());
            }
            for (Task t : p.getTasks()) {
                if (t == null) continue;
                Member a = t.getAssignee();
                if (a != null && userId.equals(a.getId())) tasks.incrementAndGet();
            }
        }
        return new UserCounts(projectIds.size(), tasks.get());
    }

    private void scrollToNode(Node node) {
        if (scrollPane == null || node == null) return;
        if (scrollPane.getContent() == null) return;
        Platform.runLater(() -> {
            double contentHeight = scrollPane.getContent().getBoundsInLocal().getHeight();
            double viewportHeight = scrollPane.getViewportBounds().getHeight();
            if (contentHeight <= viewportHeight) return;
            double y = node.getBoundsInParent().getMinY();
            double v = y / (contentHeight - viewportHeight);
            scrollPane.setVvalue(Math.max(0, Math.min(1, v)));
        });
    }

    private static StatusInfo statusFor(UserAdminService.UserRow row) {
        if (row == null) return new StatusInfo("Unknown", "status-offline");
        if (!row.active()) return new StatusInfo("Disabled", "status-disabled");
        Long lastOnline = row.lastOnlineAt();
        if (lastOnline == null || lastOnline <= 0) return new StatusInfo("Offline", "status-offline");
        long delta = Math.max(0L, System.currentTimeMillis() - lastOnline);
        if (delta <= ONLINE_WINDOW_MS) return new StatusInfo("Online", "status-online");
        if (delta <= IDLE_WINDOW_MS) return new StatusInfo("Idle", "status-idle");
        return new StatusInfo("Offline", "status-offline");
    }

    private StorageInfo storageInfo() {
        if (store instanceof DbStore dbStore) {
            Path dbFile = dbStore.manager().dbFile();
            if (dbFile == null) return new StorageInfo("Remote", "jdbc");
            try {
                long size = Files.exists(dbFile) ? Files.size(dbFile) : 0L;
                return new StorageInfo(formatBytes(size), "local db");
            } catch (Exception e) { com.projectpilot.util.AppLog.warn("admin", "Failed to read DB file size: " + (e == null ? "" : e.getMessage()));
                return new StorageInfo("Unknown", "local db");
            }
        }
        return new StorageInfo("Memory", "in-memory");
    }

    private LanInfo lanInfo() {
        if (appState.isHosting()) {
            String port = appState.getHostPort() > 0 ? Integer.toString(appState.getHostPort()) : "-";
            String sub = "port " + port + " | clients " + appState.getHostConnections();
            return new LanInfo("Hosting", sub);
        }
        if (appState.isClientOnline()) {
            String statusText = appState.getClientStatus();
            if (statusText == null || statusText.isBlank()) statusText = "connected";
            return new LanInfo("Client", statusText);
        }
        return new LanInfo("Local", "no LAN session");
    }

    private static String shortId(String id) {
        if (id == null) return "";
        String s = id.trim();
        if (s.length() <= 12) return s;
        return s.substring(0, 8) + "..." + s.substring(s.length() - 4);
    }

    private static String formatLastOnline(Long epochMillis) {
        if (epochMillis == null || epochMillis <= 0) return "Never";
        return Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .format(LAST_ONLINE_FMT);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        double gb = mb / 1024.0;
        return String.format("%.2f GB", gb);
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        String v = s;
        boolean needs = v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r");
        v = v.replace("\"", "\"\"");
        return needs ? ("\"" + v + "\"") : v;
    }

    private static String userLabel(UserAdminService.UserRow row) {
        if (row == null) return "";
        String name = row.name() == null ? "" : row.name().trim();
        String uname = row.username() == null ? "" : row.username().trim();
        if (!name.isBlank() && !uname.isBlank()) return name + " (@" + uname + ")";
        if (!uname.isBlank()) return "@" + uname;
        return name;
    }

    // --------------------------------------------------------------------------------------------
    // INNER TYPES
    // --------------------------------------------------------------------------------------------

    private static final class UserHealthRow {
        private final UserAdminService.UserRow user;
        private final int projectCount;
        private final int taskCount;
        private final String statusLabel;
        private final String statusClass;

        private UserHealthRow(UserAdminService.UserRow user, int projectCount, int taskCount, String statusLabel, String statusClass) {
            this.user = user;
            this.projectCount = projectCount;
            this.taskCount = taskCount;
            this.statusLabel = statusLabel == null ? "" : statusLabel;
            this.statusClass = statusClass == null ? "" : statusClass;
        }

        String displayName() {
            String name = user == null ? "" : safe(user.name());
            if (!name.isBlank()) return name;
            return user == null ? "" : safe(user.username());
        }

        String username() { return user == null ? "" : safe(user.username()); }
        String role() { return user == null || user.globalRole() == null ? "" : user.globalRole().name(); }
        Long lastOnlineAt() { return user == null ? null : user.lastOnlineAt(); }
        int projectCount() { return projectCount; }
        int taskCount() { return taskCount; }
        String statusLabel() { return statusLabel; }
        String statusClass() { return statusClass; }

        private static String safe(String value) { return value == null ? "" : value.trim(); }
    }
    private void installLiftOnHover(Region card) {
        var normal = new javafx.scene.effect.DropShadow(16, javafx.scene.paint.Color.rgb(0,0,0,0.18));
        normal.setOffsetY(6);

        var hover = new javafx.scene.effect.DropShadow(20, javafx.scene.paint.Color.rgb(0,0,0,0.28));
        hover.setOffsetY(10);

        card.setEffect(normal);

        var up = new javafx.animation.TranslateTransition(javafx.util.Duration.millis(120), card);
        up.setToY(-3);

        var down = new javafx.animation.TranslateTransition(javafx.util.Duration.millis(120), card);
        down.setToY(0);

        card.hoverProperty().addListener((obs, was, is) -> {
            if (is) {
                down.stop();
                up.playFromStart();
                card.setEffect(hover);
            } else {
                up.stop();
                down.playFromStart();
                card.setEffect(normal);
            }
        });
    }

    private Node buildSparklineCard(String title, String value, String subtitle, double[] series, Node hoverDetails) {

        Label t = new Label(title);
        t.getStyleClass().add("metric-title");

        Label v = new Label(value);
        v.getStyleClass().add("metric-value");

        Label sub = new Label(subtitle);
        sub.getStyleClass().add("metric-subtitle");

        javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(200, 56);
        canvas.getStyleClass().add("metric-sparkline");

        // draw whenever size changes
        javafx.beans.value.ChangeListener<Number> redraw = (o, a, b) -> drawSparkline(canvas, series);
        canvas.widthProperty().addListener(redraw);
        canvas.heightProperty().addListener(redraw);

        // let it grow with the card
        javafx.scene.layout.StackPane sparkWrap = new javafx.scene.layout.StackPane(canvas);
        sparkWrap.getStyleClass().add("metric-sparkline-wrap");
        sparkWrap.setMinHeight(56);

        // force canvas to match wrap size
        sparkWrap.widthProperty().addListener((o, oldW, newW) -> canvas.setWidth(newW.doubleValue()));
        sparkWrap.heightProperty().addListener((o, oldH, newH) -> canvas.setHeight(newH.doubleValue()));

        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(6, t, v, sub, sparkWrap);

        // optional hover overlay
        javafx.scene.layout.StackPane shell = new javafx.scene.layout.StackPane(content);
        shell.setPadding(new javafx.geometry.Insets(12));
        shell.getStyleClass().addAll("metric-tile", "pp-hover-card"); // pp-hover-card is optional; metric-tile exists

        if (hoverDetails != null) {
            hoverDetails.setVisible(false);
            hoverDetails.setManaged(false);
            hoverDetails.setMouseTransparent(true); // don't steal hover
            javafx.scene.layout.StackPane.setAlignment(hoverDetails, javafx.geometry.Pos.TOP_LEFT);
            shell.getChildren().add(hoverDetails);

            shell.hoverProperty().addListener((obs, was, is) -> hoverDetails.setVisible(is));
        }

        installLiftOnHover(shell);
        drawSparkline(canvas, series);

        return shell;
    }

    private void drawSparkline(javafx.scene.canvas.Canvas c, double[] series) {
        var g = c.getGraphicsContext2D();
        double w = c.getWidth(), h = c.getHeight();
        g.clearRect(0, 0, w, h);

        if (series == null || series.length < 2 || w <= 2 || h <= 2) return;

        double min = series[0], max = series[0];
        for (double x : series) { min = Math.min(min, x); max = Math.max(max, x); }
        double range = Math.max(1e-9, max - min);

        // Use your CSS color by sampling a default; simplest is set stroke in code to a theme-ish color.
        // If you want strictly CSS-driven, keep this and style the Canvas differently later.
        g.setStroke(javafx.scene.paint.Color.web("#4f8cff"));
        g.setLineWidth(2.0);

        double pad = 4;
        double xStep = (w - 2*pad) / (series.length - 1);

        double prevX = pad;
        double prevY = pad + (h - 2*pad) * (1 - ((series[0] - min) / range));

        for (int i = 1; i < series.length; i++) {
            double x = pad + i * xStep;
            double y = pad + (h - 2*pad) * (1 - ((series[i] - min) / range));
            g.strokeLine(prevX, prevY, x, y);
            prevX = x; prevY = y;
        }
    }

    private Node buildHoverDetails(String line1, String line2) {
        Label a = new Label(line1);
        a.getStyleClass().add("metric-title");

        Label b = new Label(line2);
        b.getStyleClass().add("metric-subtitle");

        VBox box = new VBox(4, a, b);
        box.setPadding(new Insets(10));
        box.setMaxWidth(220);

        box.setStyle("""
        -fx-background-color: rgba(10,15,28,0.92);
        -fx-background-radius: 12;
        -fx-border-radius: 12;
        -fx-border-color: rgba(255,255,255,0.10);
        -fx-border-width: 1;
    """);

        return box;
    }




    private record AuditEntry(String title, String detail, Instant at) {}
    private record StatusInfo(String label, String styleClass) {}
    private record StorageInfo(String value, String sub) {}
    private record LanInfo(String value, String sub) {}
    private record ResetPasswordData(UserAdminService.UserRow user, String password) {}
    private record UserCounts(int projects, int tasks) {}
}
