
package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.core.PageId;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.ActivityItem;
import com.projectpilot.model.Member;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.Priority;
import com.projectpilot.model.enums.ProjectHealth;
import com.projectpilot.model.enums.ProjectRole;
import com.projectpilot.model.enums.TaskStatus;
import com.projectpilot.security.AccessPolicy;
import com.projectpilot.ui.components.EmptyStatePane;
import com.projectpilot.ui.dialogs.CreateProjectDialog;
import com.projectpilot.ui.dialogs.CreateTaskDialog;
import com.projectpilot.ui.dialogs.DialogTheme;
import com.projectpilot.util.ProjectViewStore;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class ProjectsPage extends BorderPane {

    private final InMemoryStore store;
    private final AppState appState;
    private final AccessPolicy policy = new AccessPolicy();

    private final Label header = new Label("Projects");
    private final Button newProjectBtn = new Button("New Project");

    private final TextField searchField = new TextField();
    private final ComboBox<ProjectView> viewBox = new ComboBox<>();
    private final Button saveView = new Button("Save View");
    private final Button deleteView = new Button("Delete View");

    private final ProjectViewStore viewStore = new ProjectViewStore();
    private final ObservableList<ProjectView> viewOptions = FXCollections.observableArrayList();

    private final FilteredList<Project> visibleProjects;
    private final EmptyStatePane emptyState = new EmptyStatePane(
            "No projects yet",
            "Create your first project to start tracking tasks and milestones."
    );

    private final TilePane projectGrid = new TilePane();
    private final ScrollPane projectScroll = new ScrollPane(projectGrid);
    private final StackPane cardsStack = new StackPane();
    private final StackPane skeletonPane = new StackPane();
    private FadeTransition skeletonPulse;

    private final ToggleGroup healthGroup = new ToggleGroup();
    private final ToggleButton filterAll = new ToggleButton();
    private final ToggleButton filterOnTrack = new ToggleButton();
    private final ToggleButton filterAtRisk = new ToggleButton();
    private final ToggleButton filterOverdue = new ToggleButton();
    private final ObjectProperty<HealthFilter> healthFilter = new SimpleObjectProperty<>(HealthFilter.ALL);
    private final ObjectProperty<ProjectScope> scopeFilter = new SimpleObjectProperty<>(ProjectScope.ALL);

    private final Label detailsTitle = new Label("Project Details");
    private final Label nameLabel = new Label("-");
    private final Label datesLabel = new Label("-");
    private final Label summaryLabel = new Label("-");
    private final Separator sep = new Separator();

    private final Label membersTitle = new Label("Members");
    private final ListView<Member> membersList = new ListView<>();

    private final Button markDoneBtn = new Button("Mark DONE");
    private final Button deleteBtn = new Button("Delete");

    private final InvalidationListener projectStatusListener = obs -> requestRefresh();

    private final Map<Project, ListChangeListener<?>> memberHooks = new IdentityHashMap<>();
    private final Map<Project, ListChangeListener<?>> taskListHooks = new IdentityHashMap<>();
    private final Map<Task, InvalidationListener> taskHooks = new IdentityHashMap<>();

    private final BooleanBinding canCreateProject;
    private final BooleanBinding canMarkDone;
    private final BooleanBinding canDelete;
    private final PauseTransition refreshDelay = new PauseTransition(Duration.millis(160));

    private final DateTimeFormatter shortDate = DateTimeFormatter.ofPattern("MMM d");

    public ProjectsPage(InMemoryStore store, AppState appState) {
        this.store = store;
        this.appState = appState;

        setPadding(new Insets(16));

        visibleProjects = new FilteredList<>(store.getProjects(), p -> policy.canViewProject(appState, p));

        canCreateProject = Bindings.createBooleanBinding(
                () -> policy.canCreateProject(appState),
                appState.sessionProperty()
        );

        canMarkDone = Bindings.createBooleanBinding(
                () -> policy.canMarkProjectDone(appState),
                appState.sessionProperty(),
                appState.currentProjectRoleProperty(),
                appState.selectedProjectProperty()
        );

        canDelete = Bindings.createBooleanBinding(
                () -> policy.canDeleteProject(appState),
                appState.sessionProperty()
        );

        hookProjectLists();
        refreshDelay.setOnFinished(e -> {
            refreshFilters();
            pickFirstIfNeeded();
            hideSkeleton();
        });

        header.getStyleClass().add("page-title");

        newProjectBtn.getStyleClass().add("primary");
        newProjectBtn.visibleProperty().bind(canCreateProject);
        newProjectBtn.managedProperty().bind(newProjectBtn.visibleProperty());
        newProjectBtn.setOnAction(e -> createProject());

        setupViewControls();
        setupHealthStrip();
        setupGrid();
        buildSkeletonPane();

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, javafx.scene.layout.Priority.ALWAYS);
        HBox headerRow = new HBox(12, header, headerSpacer, newProjectBtn);
        headerRow.getStyleClass().add("projects-header");

        Label searchLabel = new Label("Search");
        searchLabel.getStyleClass().add("filter-label");
        VBox searchGroup = new VBox(4, searchLabel, searchField);
        searchGroup.getStyleClass().add("filter-group");

        Label viewLabel = new Label("View");
        viewLabel.getStyleClass().add("filter-label");
        VBox viewGroup = new VBox(4, viewLabel, viewBox);
        viewGroup.getStyleClass().add("filter-group");

        searchField.setMaxWidth(Double.MAX_VALUE);
        viewBox.setMaxWidth(Double.MAX_VALUE);

        HBox filterRow = new HBox(10, searchGroup, viewGroup, saveView, deleteView);
        filterRow.getStyleClass().add("filter-row");
        HBox.setHgrow(searchGroup, javafx.scene.layout.Priority.ALWAYS);

        HBox healthStrip = new HBox(8, filterAll, filterOnTrack, filterAtRisk, filterOverdue);
        healthStrip.getStyleClass().add("health-strip");

        VBox top = new VBox(12, headerRow, filterRow, healthStrip);
        setTop(top);

        BooleanBinding noProjects = Bindings.isEmpty(visibleProjects);
        emptyState.visibleProperty().bind(noProjects);
        emptyState.managedProperty().bind(noProjects);
        projectScroll.visibleProperty().bind(noProjects.not());
        projectScroll.managedProperty().bind(noProjects.not());

        emptyState.visibleProperty().addListener((obs, ov, nv) -> {
            if (nv) {
                emptyState.setOpacity(0);
                FadeTransition ft = new FadeTransition(Duration.millis(220), emptyState);
                ft.setToValue(1.0);
                ft.play();
            }
        });

        cardsStack.getChildren().setAll(projectScroll, emptyState, skeletonPane);

        detailsTitle.getStyleClass().add("panel-title");

        Label nameKey = key("Name:");
        Label datesKey = key("Dates:");
        Label summaryKey = key("Summary:");
        nameLabel.getStyleClass().add("muted");
        datesLabel.getStyleClass().add("muted");
        summaryLabel.getStyleClass().add("muted");

        membersTitle.getStyleClass().add("muted");

        membersList.getStyleClass().add("card");
        membersList.setPrefHeight(220);
        membersList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Member m, boolean empty) {
                super.updateItem(m, empty);
                if (empty || m == null) { setText(null); return; }
                String role = (m.getRole() == null) ? "-" : m.getRole().toString();
                setText(m.getName() + " - " + role);
            }
        });

        markDoneBtn.getStyleClass().add("primary");
        markDoneBtn.visibleProperty().bind(canMarkDone);
        markDoneBtn.managedProperty().bind(markDoneBtn.visibleProperty());
        markDoneBtn.setOnAction(e -> markSelectedDone());

        deleteBtn.getStyleClass().add("secondary");
        deleteBtn.visibleProperty().bind(canDelete);
        deleteBtn.managedProperty().bind(deleteBtn.visibleProperty());
        deleteBtn.setOnAction(e -> deleteSelected());

        HBox actions = new HBox(10, markDoneBtn, deleteBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox rightCard = new VBox(12,
                detailsTitle,
                nameKey, nameLabel,
                datesKey, datesLabel,
                summaryKey, summaryLabel,
                sep,
                membersTitle,
                membersList,
                actions
        );
        rightCard.getStyleClass().add("card");
        rightCard.getStyleClass().add("project-details-card");
        VBox.setVgrow(membersList, javafx.scene.layout.Priority.ALWAYS);

        HBox root = new HBox(14, cardsStack, rightCard);
        HBox.setHgrow(cardsStack, javafx.scene.layout.Priority.ALWAYS);
        setCenter(root);

        appState.selectedProjectProperty().addListener((obs, o, n) -> {
            refreshDetails(n);
            updateCardSelection();
        });

        appState.sessionProperty().addListener((obs, o, n) -> requestRefresh());
        scopeFilter.addListener((obs, o, n) -> requestRefreshQuiet());

        store.getActivity().addListener((ListChangeListener<ActivityItem>) c -> requestRefresh());

        reloadViews("all");
        refreshFilters();
        pickFirstIfNeeded();
    }

    private void setupViewControls() {
        searchField.setPromptText("Search projects...");
        searchField.setPrefWidth(320);
        searchField.textProperty().addListener((obs, ov, nv) -> requestRefreshQuiet());

        viewBox.setItems(viewOptions);
        viewBox.setPrefWidth(220);
        viewBox.setCellFactory(cb -> new ListCell<>() {
            @Override protected void updateItem(ProjectView item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.name());
            }
        });
        viewBox.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(ProjectView item, boolean empty) {
                super.updateItem(item, empty);
                setText(item == null ? "View" : item.name());
            }
        });
        viewBox.valueProperty().addListener((obs, ov, nv) -> {
            if (nv != null) applyView(nv);
        });

        saveView.setOnAction(e -> saveCurrentView());
        deleteView.setOnAction(e -> deleteCurrentView());
        deleteView.disableProperty().bind(Bindings.createBooleanBinding(
                () -> viewBox.getValue() == null || viewBox.getValue().builtIn(),
                viewBox.valueProperty()
        ));

        saveView.getStyleClass().add("subtle");
        deleteView.getStyleClass().add("ghost");
    }

    private void setupHealthStrip() {
        configureHealthToggle(filterAll, HealthFilter.ALL);
        configureHealthToggle(filterOnTrack, HealthFilter.ON_TRACK);
        configureHealthToggle(filterAtRisk, HealthFilter.AT_RISK);
        configureHealthToggle(filterOverdue, HealthFilter.OVERDUE);

        filterAll.setSelected(true);
        healthFilter.set(HealthFilter.ALL);
    }

    private void configureHealthToggle(ToggleButton button, HealthFilter filter) {
        button.getStyleClass().add("health-chip");
        button.setToggleGroup(healthGroup);
        button.setUserData(filter);
        button.setOnAction(e -> {
            if (!button.isSelected()) {
                button.setSelected(true);
                return;
            }
            healthFilter.set(filter);
            refreshFilters();
            pickFirstIfNeeded();
        });
    }

    private void setupGrid() {
        projectGrid.getStyleClass().add("project-grid");
        projectGrid.setHgap(12);
        projectGrid.setVgap(12);
        projectGrid.setPrefTileWidth(320);
        projectGrid.setTileAlignment(Pos.TOP_LEFT);

        projectScroll.setFitToWidth(true);
        projectScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        projectScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        projectScroll.getStyleClass().add("project-scroll");
    }

    private void buildSkeletonPane() {
        TilePane skeletonGrid = new TilePane();
        skeletonGrid.getStyleClass().add("skeleton-grid");
        skeletonGrid.setHgap(12);
        skeletonGrid.setVgap(12);
        skeletonGrid.setPrefTileWidth(320);
        skeletonGrid.setTileAlignment(Pos.TOP_LEFT);

        for (int i = 0; i < 6; i++) {
            VBox card = new VBox(10);
            card.getStyleClass().add("skeleton-card");
            card.setPadding(new Insets(12));

            Region title = new Region();
            title.getStyleClass().add("skeleton-line");
            title.setPrefWidth(180);
            title.setPrefHeight(12);

            Region ring = new Region();
            ring.getStyleClass().add("skeleton-circle");
            ring.setPrefSize(44, 44);

            Region meta1 = new Region();
            meta1.getStyleClass().add("skeleton-line");
            meta1.setPrefWidth(140);
            meta1.setPrefHeight(10);

            Region meta2 = new Region();
            meta2.getStyleClass().add("skeleton-line");
            meta2.setPrefWidth(120);
            meta2.setPrefHeight(10);

            HBox metric = new HBox(10, ring, new VBox(6, meta1, meta2));

            Region activity1 = new Region();
            activity1.getStyleClass().add("skeleton-line");
            activity1.setPrefWidth(200);
            activity1.setPrefHeight(9);

            Region activity2 = new Region();
            activity2.getStyleClass().add("skeleton-line");
            activity2.setPrefWidth(160);
            activity2.setPrefHeight(9);

            HBox chips = new HBox(6);
            for (int j = 0; j < 4; j++) {
                Region chip = new Region();
                chip.getStyleClass().add("skeleton-chip");
                chip.setPrefSize(28, 18);
                chips.getChildren().add(chip);
            }

            card.getChildren().addAll(title, metric, activity1, activity2, chips);
            skeletonGrid.getChildren().add(card);
        }

        skeletonPane.getChildren().setAll(skeletonGrid);
        skeletonPane.getStyleClass().add("skeleton-pane");
        skeletonPane.setVisible(false);
        skeletonPane.setManaged(false);
        skeletonPane.setMouseTransparent(true);

        skeletonPulse = new FadeTransition(Duration.millis(900), skeletonPane);
        skeletonPulse.setFromValue(0.6);
        skeletonPulse.setToValue(1.0);
        skeletonPulse.setCycleCount(FadeTransition.INDEFINITE);
        skeletonPulse.setAutoReverse(true);
    }

    private void hookProjectLists() {
        store.getProjects().forEach(this::hookProject);
        store.getProjects().addListener((ListChangeListener<Project>) c -> {
            while (c.next()) {
                if (c.wasAdded()) c.getAddedSubList().forEach(this::hookProject);
                if (c.wasRemoved()) c.getRemoved().forEach(this::unhookProject);
            }
            requestRefresh();
        });
    }

    private void refreshFilters() {
        String query = normalizeName(searchField.getText());

        visibleProjects.setPredicate(p ->
                policy.canViewProject(appState, p)
                        && matchesScope(p)
                        && matchesSearch(p, query)
                        && matchesHealthFilter(p)
        );

        updateHealthCounts(query);
        refreshCards();

        Project sel = appState.getSelectedProject();
        if (sel != null && !visibleProjects.contains(sel)) {
            appState.setSelectedProject(null);
            refreshDetails(null);
        }
    }

    private void pickFirstIfNeeded() {
        Project selected = appState.getSelectedProject();
        if (selected != null && visibleProjects.contains(selected)) {
            refreshDetails(selected);
            return;
        }

        if (!visibleProjects.isEmpty()) {
            Project p = visibleProjects.get(0);
            appState.setSelectedProject(p);
            refreshDetails(p);
        } else {
            appState.setSelectedProject(null);
            refreshDetails(null);
        }
    }

    private void refreshCards() {
        projectGrid.getChildren().clear();
        for (Project p : visibleProjects) {
            projectGrid.getChildren().add(buildProjectCard(p));
        }
        updateCardSelection();
    }

    private void updateCardSelection() {
        Project selected = appState.getSelectedProject();
        for (javafx.scene.Node node : projectGrid.getChildren()) {
            if (!(node instanceof VBox card)) continue;
            card.getStyleClass().remove("project-card-selected");
            if (selected != null && selected == card.getUserData()) {
                card.getStyleClass().add("project-card-selected");
            }
        }
    }

    private VBox buildProjectCard(Project p) {
        VBox card = new VBox(10);
        card.getStyleClass().add("project-card");
        card.setPadding(new Insets(12));
        card.setMinHeight(200);
        card.setUserData(p);

        Label title = new Label(p.getName());
        title.getStyleClass().add("project-card-title");

        Priority priority = projectPriority(p);
        Label prioBadge = new Label(priorityLabel(priority));
        prioBadge.getStyleClass().add("priority-badge");
        prioBadge.getStyleClass().add(priorityClass(priority));

        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, javafx.scene.layout.Priority.ALWAYS);
        HBox titleRow = new HBox(8, title, titleSpacer, prioBadge);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        int totalTasks = p.getTasks().size();
        long doneTasks = p.getTasks().stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
        double progress = totalTasks == 0 ? 0.0 : (double) doneTasks / totalTasks;

        ProgressIndicator progressRing = new ProgressIndicator(progress);
        progressRing.setPrefSize(46, 46);
        progressRing.getStyleClass().add("project-progress");

        String pctText = totalTasks == 0 ? "0%" : Math.round(progress * 100) + "%";
        Label progressLabel = new Label(pctText);
        progressLabel.getStyleClass().add("project-progress-label");

        StackPane progressWrap = new StackPane(progressRing, progressLabel);
        progressWrap.getStyleClass().add("project-progress-wrap");

        Label progressMeta = new Label(doneTasks + "/" + totalTasks + " done");
        progressMeta.getStyleClass().add("project-card-meta");

        int overdue = countOverdue(p);
        Label overdueBadge = new Label(overdue == 0 ? "No overdue" : overdue + " overdue");
        overdueBadge.getStyleClass().add("project-chip");
        overdueBadge.getStyleClass().add(overdue == 0 ? "chip-muted" : "chip-danger");

        LocalDate nextDue = nextDueDate(p);
        String nextText = nextDue == null ? "No upcoming due dates" : "Next due " + shortDate.format(nextDue);
        Label nextDueLabel = new Label(nextText);
        nextDueLabel.getStyleClass().add("project-card-sub");

        VBox meta = new VBox(4, progressMeta, overdueBadge, nextDueLabel);
        HBox metricRow = new HBox(10, progressWrap, meta);
        metricRow.setAlignment(Pos.CENTER_LEFT);

        VBox activityPanel = buildActivityPanel(p);
        HBox membersRow = buildMembersRow(p);
        HBox actionsRow = buildActionsRow(p);

        card.getChildren().addAll(titleRow, metricRow, activityPanel, membersRow, actionsRow);

        card.setOnMouseClicked(e -> {
            appState.setSelectedProject(p);
            refreshDetails(p);
            updateCardSelection();
        });

        return card;
    }

    private VBox buildActivityPanel(Project p) {
        VBox box = new VBox(6);
        box.getStyleClass().add("project-activity");

        Label title = new Label("Latest updates");
        title.getStyleClass().add("project-activity-title");
        box.getChildren().add(title);

        List<ActivityItem> items = recentActivity(p, 3);
        if (items.isEmpty()) {
            Label empty = new Label("No updates yet");
            empty.getStyleClass().add("project-card-sub");
            box.getChildren().add(empty);
            return box;
        }

        for (int i = 0; i < items.size(); i++) {
            ActivityItem item = items.get(i);
            HBox row = new HBox(8);
            row.getStyleClass().add("project-activity-row");

            VBox marker = new VBox(2);
            marker.getStyleClass().add("activity-marker");
            Region dot = new Region();
            dot.getStyleClass().add("activity-dot");
            Region line = new Region();
            line.getStyleClass().add("activity-line");
            if (i == items.size() - 1) {
                line.getStyleClass().add("activity-line-end");
            }
            marker.getChildren().addAll(dot, line);

            Label message = new Label(activityMessage(item));
            message.getStyleClass().add("activity-text");
            Label time = new Label(formatActivityTime(item.getTime()));
            time.getStyleClass().add("activity-time");

            VBox content = new VBox(2, message, time);
            row.getChildren().addAll(marker, content);
            box.getChildren().add(row);
        }

        return box;
    }

    private List<ActivityItem> recentActivity(Project p, int limit) {
        List<ActivityItem> out = new ArrayList<>();
        if (p == null) return out;
        String pid = p.getId();
        if (pid == null) return out;
        for (ActivityItem item : store.getActivity()) {
            if (item == null) continue;
            if (!pid.equals(item.getProjectId())) continue;
            out.add(item);
            if (out.size() >= limit) break;
        }
        return out;
    }

    private String activityMessage(ActivityItem item) {
        if (item == null) return "Update";
        String msg = safe(item.getMessage());
        if (!msg.isBlank()) return msg;
        String type = safe(item.getEntityType());
        String action = safe(item.getAction());
        String text = (type + " " + action).trim();
        return text.isEmpty() ? "Update" : text;
    }

    private String formatActivityTime(LocalDateTime time) {
        if (time == null) return "-";
        LocalDateTime now = LocalDateTime.now();
        long minutes = java.time.Duration.between(time, now).toMinutes();
        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        if (days < 7) return days + "d ago";
        return time.format(DateTimeFormatter.ofPattern("MMM d"));
    }

    private HBox buildMembersRow(Project p) {
        HBox row = new HBox(6);
        row.getStyleClass().add("project-members");

        List<Member> members = p.getMembers();
        if (members == null || members.isEmpty()) {
            Label empty = new Label("No members");
            empty.getStyleClass().add("project-card-sub");
            row.getChildren().add(empty);
            return row;
        }

        int max = 4;
        int count = 0;
        for (Member m : members) {
            if (m == null) continue;
            if (count >= max) break;
            Label avatar = new Label(initials(m.getName()));
            avatar.getStyleClass().add("member-avatar");
            avatar.getStyleClass().add(avatarClass(m.getName()));
            row.getChildren().add(avatar);
            count++;
        }

        int remaining = members.size() - count;
        if (remaining > 0) {
            Label more = new Label("+" + remaining);
            more.getStyleClass().addAll("member-avatar", "avatar-more");
            row.getChildren().add(more);
        }

        return row;
    }

    private HBox buildActionsRow(Project p) {
        Button openBtn = new Button("Open");
        Button addTaskBtn = new Button("Add task");
        Button assignBtn = new Button("Assign");
        Button exportBtn = new Button("Export");

        openBtn.getStyleClass().addAll("subtle", "sm");
        addTaskBtn.getStyleClass().addAll("ghost", "sm");
        assignBtn.getStyleClass().addAll("ghost", "sm");
        exportBtn.getStyleClass().addAll("ghost", "sm");

        openBtn.setOnAction(e -> {
            appState.setSelectedProject(p);
            appState.setCurrentPage(PageId.PROJECT_OVERVIEW);
        });

        addTaskBtn.setOnAction(e -> {
            appState.setSelectedProject(p);
            if (!policy.canCreateTasks(appState)) {
                alertInfo("Access denied", "Only project leaders can create tasks.");
                return;
            }
            if (p.getMembers() == null || p.getMembers().isEmpty()) {
                alertInfo("No project members yet", "Add project members before creating tasks.");
                return;
            }
            CreateTaskDialog d = new CreateTaskDialog(p, appState);
            d.showAndWait().ifPresent(t -> {
                if (isDuplicateTaskTitle(p, t.getTitle())) {
                    alertInfo("Duplicate task", "A task with that title already exists in this project.");
                    return;
                }
                store.addTask(p, t);
                requestRefresh();
            });
        });

        assignBtn.setOnAction(e -> {
            appState.setSelectedProject(p);
            appState.setCurrentPage(PageId.TEAM);
        });

        exportBtn.setOnAction(e -> {
            appState.setSelectedProject(p);
            appState.setCurrentPage(PageId.EXPORT_REPORT);
        });

        HBox actions = new HBox(6, openBtn, addTaskBtn, assignBtn, exportBtn);
        actions.getStyleClass().add("project-actions");
        actions.setAlignment(Pos.CENTER_LEFT);
        return actions;
    }

    private void refreshDetails(Project p) {
        if (p == null) {
            nameLabel.setText("-");
            datesLabel.setText("-");
            summaryLabel.setText("-");
            membersList.setItems(null);
            return;
        }

        nameLabel.setText(p.getName());
        datesLabel.setText(safeDate(p.getStartDate()) + " -> " + safeDate(p.getEndDate()));

        long tasks = p.getTasks().size();
        long phases = p.getPhases().size();
        long milestones = p.getMilestones().size();
        summaryLabel.setText("Tasks: " + tasks + " | Phases: " + phases + " | Milestones: " + milestones);

        membersList.setItems(p.getMembers());
    }

    private void createProject() {
        if (!policy.canCreateProject(appState)) return;

        CreateProjectDialog d = new CreateProjectDialog();
        d.showAndWait().ifPresent(p -> {
            p.setStatus(Project.ProjectStatus.ACTIVE);

            if (isDuplicateProjectName(p.getName())) {
                alertInfo("Duplicate project", "A project with that name already exists.");
                return;
            }

            store.createProject(p);

            ensureCreatorIsMember(p);

            appState.setSelectedProject(p);
            refreshFilters();
        });
    }

    private void ensureCreatorIsMember(Project p) {
        if (p == null) return;
        if (p.getMembers() != null && !p.getMembers().isEmpty()) return;

        var s = appState.getSession();
        if (s == null) return;

        String id = s.id();
        String name = (s.displayName() != null && !s.displayName().isBlank()) ? s.displayName() : s.username();
        if (name == null || name.isBlank()) name = "System";

        Member creator = new Member(id, name, ProjectRole.LEADER);
        store.addMember(p, creator);
    }

    private void markSelectedDone() {
        if (!policy.canMarkProjectDone(appState)) return;

        Project p = appState.getSelectedProject();
        if (p == null) return;

        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("ProjectPilot");
        a.setHeaderText("Mark project as DONE?");
        a.setContentText("This will move it to History.");
        var res = a.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.OK) return;

        store.markProjectDone(p);

        refreshFilters();
        pickFirstIfNeeded();
    }

    private void deleteSelected() {
        if (!policy.canDeleteProject(appState)) return;

        Project p = appState.getSelectedProject();
        if (p == null) return;

        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("ProjectPilot");
        a.setHeaderText("Delete project?");
        a.setContentText("Hard delete: permanently removes this project and related DB data.");
        var res = a.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.OK) return;

        store.deleteProject(p);

        refreshFilters();
        pickFirstIfNeeded();
    }

    private void hookProject(Project p) {
        if (p == null) return;

        p.statusProperty().addListener(projectStatusListener);

        if (!memberHooks.containsKey(p)) {
            ListChangeListener<?> l = c -> requestRefresh();
            try {
                p.getMembers().addListener((ListChangeListener) l);
                memberHooks.put(p, l);
            } catch (Exception e) { com.projectpilot.util.AppLog.warn("projects", "Failed to attach member listener: " + (e == null ? "" : e.getMessage())); }
        }

        if (!taskListHooks.containsKey(p)) {
            ListChangeListener<Task> l = c -> {
                while (c.next()) {
                    if (c.wasAdded()) c.getAddedSubList().forEach(this::hookTask);
                    if (c.wasRemoved()) c.getRemoved().forEach(this::unhookTask);
                }
                requestRefresh();
            };
            try {
                p.getTasks().addListener(l);
                taskListHooks.put(p, l);
            } catch (Exception e) { com.projectpilot.util.AppLog.warn("projects", "Failed to attach task list listener: " + (e == null ? "" : e.getMessage())); }

            for (Task t : p.getTasks()) hookTask(t);
        }
    }

    private void unhookProject(Project p) {
        if (p == null) return;

        p.statusProperty().removeListener(projectStatusListener);

        ListChangeListener<?> l = memberHooks.remove(p);
        if (l != null) {
            try { p.getMembers().removeListener((ListChangeListener) l); } catch (Exception e) { com.projectpilot.util.AppLog.warn("projects", "Failed to remove member listener: " + (e == null ? "" : e.getMessage())); }
        }

        ListChangeListener<?> tl = taskListHooks.remove(p);
        if (tl != null) {
            try { p.getTasks().removeListener((ListChangeListener) tl); } catch (Exception e) { com.projectpilot.util.AppLog.warn("projects", "Failed to remove task list listener: " + (e == null ? "" : e.getMessage())); }
        }

        for (Task t : p.getTasks()) unhookTask(t);
    }

    private void hookTask(Task t) {
        if (t == null || taskHooks.containsKey(t)) return;
        InvalidationListener l = obs -> requestRefresh();
        t.statusProperty().addListener(l);
        t.dueDateProperty().addListener(l);
        t.priorityProperty().addListener(l);
        taskHooks.put(t, l);
    }

    private void unhookTask(Task t) {
        if (t == null) return;
        InvalidationListener l = taskHooks.remove(t);
        if (l == null) return;
        try { t.statusProperty().removeListener(l); } catch (Exception e) { com.projectpilot.util.AppLog.warn("projects", "Failed to remove task status listener: " + (e == null ? "" : e.getMessage())); }
        try { t.dueDateProperty().removeListener(l); } catch (Exception e) { com.projectpilot.util.AppLog.warn("projects", "Failed to remove task dueDate listener: " + (e == null ? "" : e.getMessage())); }
        try { t.priorityProperty().removeListener(l); } catch (Exception e) { com.projectpilot.util.AppLog.warn("projects", "Failed to remove task priority listener: " + (e == null ? "" : e.getMessage())); }
    }

    private void reloadViews(String selectId) {
        String toSelect = selectId;
        if (toSelect == null && viewBox.getValue() != null) {
            toSelect = viewBox.getValue().id();
        }

        viewOptions.setAll(buildBuiltInViews());

        String userId = currentUserId();
        for (ProjectViewStore.ProjectViewData data : viewStore.load(userId)) {
            viewOptions.add(new ProjectView(data.id(), data.name(), data, false));
        }

        if (toSelect != null) {
            for (ProjectView view : viewOptions) {
                if (toSelect.equals(view.id())) {
                    viewBox.getSelectionModel().select(view);
                    return;
                }
            }
        }

        if (!viewOptions.isEmpty()) {
            viewBox.getSelectionModel().select(0);
        }
    }

    private List<ProjectView> buildBuiltInViews() {
        List<ProjectView> builtIn = new ArrayList<>();
        builtIn.add(new ProjectView(
                "all",
                "All projects",
                new ProjectViewStore.ProjectViewData(
                        "all",
                        "All projects",
                        "",
                        ProjectScope.ALL.name(),
                        HealthFilter.ALL.name()
                ),
                true
        ));
        builtIn.add(new ProjectView(
                "mine",
                "My Projects",
                new ProjectViewStore.ProjectViewData(
                        "mine",
                        "My Projects",
                        "",
                        ProjectScope.MY_PROJECTS.name(),
                        HealthFilter.ALL.name()
                ),
                true
        ));
        builtIn.add(new ProjectView(
                "due-week",
                "Due This Week",
                new ProjectViewStore.ProjectViewData(
                        "due-week",
                        "Due This Week",
                        "",
                        ProjectScope.DUE_WEEK.name(),
                        HealthFilter.ALL.name()
                ),
                true
        ));
        builtIn.add(new ProjectView(
                "high-risk",
                "High Risk",
                new ProjectViewStore.ProjectViewData(
                        "high-risk",
                        "High Risk",
                        "",
                        ProjectScope.HIGH_RISK.name(),
                        HealthFilter.ALL.name()
                ),
                true
        ));
        return builtIn;
    }

    private void applyView(ProjectView view) {
        if (view == null || view.data() == null) return;
        ProjectViewStore.ProjectViewData data = view.data();

        searchField.setText(data.query() == null ? "" : data.query());

        ProjectScope scope = safeEnum(ProjectScope.class, data.scope(), ProjectScope.ALL);
        scopeFilter.set(scope);

        HealthFilter hf = safeEnum(HealthFilter.class, data.health(), HealthFilter.ALL);
        setHealthFilter(hf);

        requestRefreshQuiet();
    }

    private void saveCurrentView() {
        TextInputDialog d = new TextInputDialog();
        DialogTheme.apply(d);
        d.setTitle("Save View");
        d.setHeaderText("Save current filters as a view");
        d.setContentText("View name:");

        d.showAndWait().ifPresent(nameRaw -> {
            String name = nameRaw == null ? "" : nameRaw.trim();
            if (name.isEmpty()) return;

            String userId = currentUserId();
            List<ProjectViewStore.ProjectViewData> saved = new ArrayList<>(viewStore.load(userId));

            ProjectViewStore.ProjectViewData existing = null;
            for (ProjectViewStore.ProjectViewData v : saved) {
                if (normalizeName(v.name()).equals(normalizeName(name))) {
                    existing = v;
                    break;
                }
            }

            String id = existing == null ? java.util.UUID.randomUUID().toString() : existing.id();
            ProjectViewStore.ProjectViewData next = snapshotViewData(id, name);
            if (existing != null) saved.remove(existing);
            saved.add(next);
            viewStore.save(userId, saved);
            reloadViews(id);
        });
    }

    private void deleteCurrentView() {
        ProjectView view = viewBox.getValue();
        if (view == null || view.builtIn()) return;

        String userId = currentUserId();
        List<ProjectViewStore.ProjectViewData> saved = new ArrayList<>(viewStore.load(userId));
        saved.removeIf(v -> v != null && view.id().equals(v.id()));
        viewStore.save(userId, saved);
        reloadViews("all");
    }

    private ProjectViewStore.ProjectViewData snapshotViewData(String id, String name) {
        ProjectScope scope = scopeFilter.get() == null ? ProjectScope.ALL : scopeFilter.get();
        HealthFilter hf = healthFilter.get() == null ? HealthFilter.ALL : healthFilter.get();

        return new ProjectViewStore.ProjectViewData(
                id,
                name,
                searchField.getText(),
                scope.name(),
                hf.name()
        );
    }

    private String currentUserId() {
        if (appState.getSession() == null) return "local";
        String id = appState.getSession().id();
        return id == null || id.isBlank() ? "local" : id.trim();
    }

    private void updateHealthCounts(String query) {
        int onTrack = 0;
        int atRisk = 0;
        int overdue = 0;

        for (Project p : store.getProjects()) {
            if (p == null) continue;
            if (!policy.canViewProject(appState, p)) continue;
            if (!matchesScope(p)) continue;
            if (!matchesSearch(p, query)) continue;

            if (isProjectOverdue(p)) {
                overdue++;
            } else if (projectHealth(p) == ProjectHealth.ON_TRACK) {
                onTrack++;
            } else {
                atRisk++;
            }
        }

        int total = onTrack + atRisk + overdue;
        filterAll.setText("All (" + total + ")");
        filterOnTrack.setText("On track (" + onTrack + ")");
        filterAtRisk.setText("At risk (" + atRisk + ")");
        filterOverdue.setText("Overdue (" + overdue + ")");
    }

    private boolean matchesHealthFilter(Project p) {
        if (p == null) return false;
        HealthFilter filter = healthFilter.get();
        if (filter == null || filter == HealthFilter.ALL) return true;
        boolean overdue = isProjectOverdue(p);
        ProjectHealth health = projectHealth(p);

        return switch (filter) {
            case ON_TRACK -> !overdue && health == ProjectHealth.ON_TRACK;
            case AT_RISK -> !overdue && health != ProjectHealth.ON_TRACK;
            case OVERDUE -> overdue;
            default -> true;
        };
    }

    private boolean matchesScope(Project p) {
        ProjectScope scope = scopeFilter.get();
        if (scope == null || scope == ProjectScope.ALL) return true;

        return switch (scope) {
            case MY_PROJECTS -> isMemberProject(p);
            case DUE_WEEK -> isDueThisWeek(p);
            case HIGH_RISK -> isProjectOverdue(p) || projectHealth(p) != ProjectHealth.ON_TRACK;
            default -> true;
        };
    }

    private boolean matchesSearch(Project p, String query) {
        if (p == null) return false;
        if (query == null || query.isBlank()) return true;
        String name = normalizeName(p.getName());
        String desc = normalizeName(p.getDescription());
        return name.contains(query) || desc.contains(query);
    }

    private void setHealthFilter(HealthFilter filter) {
        HealthFilter next = filter == null ? HealthFilter.ALL : filter;
        healthFilter.set(next);
        switch (next) {
            case ALL -> filterAll.setSelected(true);
            case ON_TRACK -> filterOnTrack.setSelected(true);
            case AT_RISK -> filterAtRisk.setSelected(true);
            case OVERDUE -> filterOverdue.setSelected(true);
        }
    }

    private boolean isMemberProject(Project p) {
        if (p == null) return false;
        String myId = policy.memberId(appState);
        if (myId == null || myId.isBlank()) return false;
        for (Member m : p.getMembers()) {
            if (m == null) continue;
            if (myId.equals(m.getId())) return true;
        }
        return false;
    }

    private boolean isDueThisWeek(Project p) {
        if (p == null) return false;
        LocalDate today = LocalDate.now();
        LocalDate end = today.plusDays(7);

        LocalDate projectEnd = p.getEndDate();
        if (projectEnd != null && !projectEnd.isBefore(today) && !projectEnd.isAfter(end)) {
            return true;
        }

        for (Task t : p.getTasks()) {
            if (t == null || t.getStatus() == TaskStatus.DONE) continue;
            LocalDate due = t.getDueDate();
            if (due == null) continue;
            if (!due.isBefore(today) && !due.isAfter(end)) return true;
        }
        return false;
    }

    private ProjectHealth projectHealth(Project p) {
        if (p == null) return ProjectHealth.ON_TRACK;
        ProjectHealth manual = p.getHealth();
        if (manual != null && manual != ProjectHealth.ON_TRACK) return manual;

        long blocked = p.getTasks().stream()
                .filter(t -> t != null && t.getStatus() == TaskStatus.BLOCKED)
                .count();
        long overdue = countOverdue(p);

        if (blocked >= 2 || overdue >= 3) return ProjectHealth.BLOCKED;
        if (blocked >= 1 || overdue >= 1) return ProjectHealth.AT_RISK;
        return ProjectHealth.ON_TRACK;
    }

    private boolean isProjectOverdue(Project p) {
        if (p == null) return false;
        LocalDate today = LocalDate.now();
        if (p.getEndDate() != null && p.getEndDate().isBefore(today) && p.getStatus() != Project.ProjectStatus.DONE) {
            return true;
        }
        for (Task t : p.getTasks()) {
            if (t == null) continue;
            if (t.getStatus() == TaskStatus.DONE) continue;
            LocalDate due = t.getDueDate();
            if (due != null && due.isBefore(today)) return true;
        }
        return false;
    }

    private int countOverdue(Project p) {
        if (p == null) return 0;
        LocalDate today = LocalDate.now();
        int count = 0;
        for (Task t : p.getTasks()) {
            if (t == null || t.getStatus() == TaskStatus.DONE) continue;
            LocalDate due = t.getDueDate();
            if (due != null && due.isBefore(today)) count++;
        }
        return count;
    }

    private LocalDate nextDueDate(Project p) {
        if (p == null) return null;
        LocalDate next = null;
        for (Task t : p.getTasks()) {
            if (t == null || t.getStatus() == TaskStatus.DONE) continue;
            LocalDate due = t.getDueDate();
            if (due == null) continue;
            if (next == null || due.isBefore(next)) next = due;
        }
        return next;
    }

    private Priority projectPriority(Project p) {
        if (p == null) return Priority.LOW;
        boolean hasHigh = false;
        boolean hasMedium = false;
        for (Task t : p.getTasks()) {
            if (t == null || t.getStatus() == TaskStatus.DONE) continue;
            Priority prio = t.getPriority();
            if (prio == Priority.HIGH) {
                hasHigh = true;
                break;
            }
            if (prio == Priority.MEDIUM) hasMedium = true;
        }
        if (hasHigh) return Priority.HIGH;
        if (hasMedium) return Priority.MEDIUM;
        return Priority.LOW;
    }

    private String priorityLabel(Priority p) {
        if (p == null) return "Low";
        return switch (p) {
            case HIGH -> "High";
            case MEDIUM -> "Medium";
            default -> "Low";
        };
    }

    private String priorityClass(Priority p) {
        if (p == null) return "priority-low";
        return switch (p) {
            case HIGH -> "priority-high";
            case MEDIUM -> "priority-medium";
            default -> "priority-low";
        };
    }

    private String initials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase();
        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
    }

    private String avatarClass(String name) {
        int idx = Math.abs((name == null ? 0 : name.hashCode())) % 5;
        return "avatar-" + (idx + 1);
    }

    private void alertInfo(String header, String text) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("ProjectPilot");
        a.setHeaderText(header);
        a.setContentText(text);
        a.showAndWait();
    }

    private static Label key(String t) {
        Label l = new Label(t);
        l.getStyleClass().add("label-strong");
        return l;
    }

    private void requestRefresh() {
        requestRefresh(true);
    }

    private void requestRefreshQuiet() {
        requestRefresh(false);
    }

    private void requestRefresh(boolean showSkeleton) {
        if (showSkeleton) showSkeletonIfNeeded();
        refreshDelay.playFromStart();
    }

    private void showSkeletonIfNeeded() {
        if (store.getProjects().isEmpty()) return;
        if (skeletonPane.isVisible()) return;
        skeletonPane.setVisible(true);
        skeletonPane.setManaged(true);
        if (skeletonPulse != null) skeletonPulse.playFromStart();
    }

    private void hideSkeleton() {
        if (skeletonPulse != null) skeletonPulse.stop();
        skeletonPane.setVisible(false);
        skeletonPane.setManaged(false);
    }

    private boolean isDuplicateProjectName(String name) {
        String n = normalizeName(name);
        if (n.isBlank()) return false;

        for (Project p : store.getProjects()) {
            if (p == null) continue;
            if (normalizeName(p.getName()).equals(n)) return true;
        }
        for (Project p : store.getHistoryProjects()) {
            if (p == null) continue;
            if (normalizeName(p.getName()).equals(n)) return true;
        }
        return false;
    }

    private boolean isDuplicateTaskTitle(Project p, String title) {
        if (p == null) return false;
        String n = normalizeName(title);
        if (n.isBlank()) return false;
        return p.getTasks().stream()
                .anyMatch(t -> t != null && normalizeName(t.getTitle()).equals(n));
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

    private static String safeDate(Object o) {
        return (o == null) ? "-" : o.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static <E extends Enum<E>> E safeEnum(Class<E> type, String name, E fallback) {
        if (name == null || name.isBlank()) return fallback;
        try { return Enum.valueOf(type, name); } catch (Exception e) { com.projectpilot.util.AppLog.warn("projects", "safeEnum parse failed for " + name + ": " + (e == null ? "" : e.getMessage())); return fallback; }
    }

    private enum HealthFilter { ALL, ON_TRACK, AT_RISK, OVERDUE }

    private enum ProjectScope { ALL, MY_PROJECTS, DUE_WEEK, HIGH_RISK }

    private record ProjectView(String id, String name, ProjectViewStore.ProjectViewData data, boolean builtIn) {}
}
