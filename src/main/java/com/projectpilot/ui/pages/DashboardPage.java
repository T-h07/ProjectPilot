package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.core.PageId;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.ActivityItem;
import com.projectpilot.model.Member;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.TaskStatus;
import com.projectpilot.security.AccessPolicy;
import com.projectpilot.ui.components.EmptyStatePane;
import com.projectpilot.ui.dialogs.CreateTaskDialog;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.layout.Priority;
import javafx.scene.shape.Polyline;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DashboardPage extends BorderPane {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final InMemoryStore store;
    private final AppState appState;
    private final AccessPolicy policy = new AccessPolicy();

    private final Label title = new Label("ProjectPilot");
    private final TextField search = new TextField();
    private final Button newBtn = new Button("New");
    private final Button refreshBtn = new Button("Refresh");

    private final Label bannerTitle = new Label();
    private final Label bannerSub = new Label();
    private final Button bannerCta = new Button("Open Tasks");
    private final Button bannerSecondary = new Button("View Activity");
    private final HBox banner = new HBox(16);

    private final MetricTile projectsTile = new MetricTile("Active Projects", 0);
    private final MetricTile doneTile = new MetricTile("Tasks Done", 0);
    private final MetricTile overdueTile = new MetricTile("Overdue", 0);
    private final MetricTile workloadTile = new MetricTile("Workload", 0);

    private final TodayCard dueTodayCard = new TodayCard("Due Today");
    private final TodayCard inProgressCard = new TodayCard("In Progress");
    private final TodayCard blockedCard = new TodayCard("Blocked");

    private final Label workloadHint = new Label();
    private final Label activityHint = new Label();
    private final Label riskHint = new Label();

    private final HBox workloadRow = new HBox(12);
    private final ScrollPane workloadScroll = new ScrollPane(workloadRow);

    private final ListView<ActivityFeedItem> activityList = new ListView<>();
    private final javafx.collections.ObservableList<ActivityFeedItem> activityFeed =
            javafx.collections.FXCollections.observableArrayList();
    private final FilteredList<ActivityItem> filteredActivity;

    private final VBox riskCard = new VBox(10);
    private final ListView<Task> riskList = new ListView<>();
    private final javafx.collections.ObservableList<Task> riskItems =
            javafx.collections.FXCollections.observableArrayList();

    private String activityQuery = "";
    private final PauseTransition refreshDelay = new PauseTransition(Duration.millis(120));

    public DashboardPage(InMemoryStore store, AppState appState) {
        this.store = store;
        this.appState = appState;
        this.filteredActivity = new FilteredList<>(store.getActivity(), a -> true);

        setPadding(new Insets(16));

        title.getStyleClass().add("page-title");

        search.setPromptText("Search...");
        search.setPrefWidth(320);

        newBtn.getStyleClass().add("primary");
        refreshBtn.getStyleClass().add("secondary");

        newBtn.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> policy.isAdmin(appState) || policy.canCreateTasks(appState),
                appState.sessionProperty(),
                appState.currentProjectRoleProperty(),
                appState.selectedProjectProperty()
        ));
        newBtn.managedProperty().bind(newBtn.visibleProperty());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);


        HBox topBar = new HBox(12, title, spacer, search, newBtn, refreshBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("card");
        topBar.setPadding(new Insets(12));

        banner.getStyleClass().addAll("card", "dashboard-banner");
        bannerTitle.getStyleClass().add("banner-title");
        bannerSub.getStyleClass().add("banner-sub");
        bannerSub.setWrapText(true);
        bannerCta.getStyleClass().addAll("primary", "banner-cta");
        bannerSecondary.getStyleClass().addAll("ghost", "banner-cta");

        bannerCta.setOnAction(e -> appState.setCurrentPage(PageId.TASKS));
        bannerSecondary.setOnAction(e -> appState.setCurrentPage(PageId.ACTIVITY));

        VBox bannerText = new VBox(4, bannerTitle, bannerSub);
        bannerText.getStyleClass().add("banner-text");

        Region bannerSpacer = new Region();
        HBox.setHgrow(bannerSpacer, Priority.ALWAYS);

        HBox bannerActions = new HBox(8, bannerCta, bannerSecondary);
        bannerActions.setAlignment(Pos.CENTER_RIGHT);
        bannerActions.getStyleClass().add("banner-actions");

        banner.setAlignment(Pos.CENTER_LEFT);
        banner.getChildren().addAll(bannerText, bannerSpacer, bannerActions);

        projectsTile.getStyleClass().addAll("metric-hero", "metric-accent");
        doneTile.getStyleClass().addAll("metric-hero", "metric-success");
        overdueTile.getStyleClass().addAll("metric-hero", "metric-danger");
        workloadTile.getStyleClass().addAll("metric-hero", "metric-warning");

        HBox metrics = new HBox(16, projectsTile, doneTile, overdueTile, workloadTile);
        for (MetricTile tile : List.of(projectsTile, doneTile, overdueTile, workloadTile)) {
            HBox.setHgrow(tile, Priority.ALWAYS);
            tile.setMaxWidth(Double.MAX_VALUE);
        }
        metrics.setPadding(new Insets(12, 0, 0, 0));

        HBox todayRow = new HBox(16, dueTodayCard, inProgressCard, blockedCard);
        todayRow.getStyleClass().add("today-row");
        for (TodayCard card : List.of(dueTodayCard, inProgressCard, blockedCard)) {
            HBox.setHgrow(card, Priority.ALWAYS);
            card.setMaxWidth(Double.MAX_VALUE);
        }

        Label workloadTitle = new Label("Workload Heat");
        workloadTitle.getStyleClass().add("section-title");
        workloadHint.getStyleClass().add("section-hint");

        workloadRow.getStyleClass().add("workload-row");
        workloadRow.setAlignment(Pos.CENTER_LEFT);

        workloadScroll.getStyleClass().addAll("pp-scroll", "workload-scroll");
        workloadScroll.setFitToHeight(true);
        workloadScroll.setFitToWidth(false);
        workloadScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        workloadScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        VBox workloadSection = new VBox(6, workloadTitle, workloadHint, workloadScroll);
        workloadSection.getStyleClass().add("workload-section");

        buildRiskPanel();

        VBox activityCard = new VBox(8);
        activityCard.getStyleClass().add("card");
        activityCard.setPadding(new Insets(12));

        Label activityTitle = new Label("Recent Activity");
        activityTitle.getStyleClass().add("section-title");
        activityHint.getStyleClass().add("section-hint");

        setupActivityFeed();
        VBox.setVgrow(activityList, Priority.ALWAYS);

        activityCard.getChildren().addAll(activityTitle, activityHint, activityList);

        HBox lowerRow = new HBox(16, riskCard, activityCard);
        HBox.setHgrow(activityCard, Priority.ALWAYS);
        riskCard.setMinWidth(280);
        riskCard.setPrefWidth(360);
        riskCard.setMaxWidth(420);

        VBox center = new VBox(16, banner, metrics, todayRow, workloadSection, lowerRow);
        center.setPadding(new Insets(4, 0, 8, 0));
        VBox.setVgrow(activityCard, Priority.ALWAYS);

        ScrollPane scroll = new ScrollPane(center);
        scroll.getStyleClass().add("pp-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        setTop(topBar);
        setCenter(scroll);

        search.textProperty().addListener((obs, oldV, newV) -> {
            activityQuery = (newV == null) ? "" : newV;
            applyActivityFilter(activityQuery);
        });

        refreshBtn.setOnAction(e -> refreshAll());
        newBtn.setOnAction(e -> showQuickCreateMenu(newBtn));

        refreshDelay.setOnFinished(e -> refreshAll());

        store.getProjects().addListener((ListChangeListener<Project>) c -> requestRefresh());
        store.getActivity().addListener((ListChangeListener<ActivityItem>) c -> requestRefresh());
        filteredActivity.addListener((ListChangeListener<ActivityItem>) c -> rebuildActivityFeed());
        appState.selectedProjectProperty().addListener((obs, oldV, newV) -> requestRefresh());
        appState.sessionProperty().addListener((obs, oldV, newV) -> requestRefresh());

        refreshAll();
        applyEntryAnimations(
                banner,
                projectsTile, doneTile, overdueTile, workloadTile,
                dueTodayCard, inProgressCard, blockedCard,
                workloadSection, riskCard, activityCard
        );
    }

    private void setupActivityFeed() {
        activityList.setItems(activityFeed);
        activityList.getStyleClass().add("activity-feed");
        activityList.setFocusTraversable(false);
        updateActivityPlaceholder();

        activityList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ActivityFeedItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }

                if (item.isHeader()) {
                    Label header = new Label(item.header());
                    header.getStyleClass().add("activity-group");
                    setGraphic(header);
                    return;
                }

                ActivityItem a = item.activity();
                String actor = safe(a.getActor());
                if (actor.isBlank()) actor = "System";
                String time = a.getTime() == null ? "-" : a.getTime().format(TIME);

                Label initials = new Label(initials(actor));
                initials.getStyleClass().add("activity-initials");

                StackPane avatar = new StackPane(initials);
                avatar.getStyleClass().add("activity-avatar");

                Label message = new Label(safe(a.getMessage()));
                message.getStyleClass().add("activity-message");
                message.setWrapText(true);

                Label meta = new Label(actor + " | " + time);
                meta.getStyleClass().add("activity-meta");

                VBox text = new VBox(2, message, meta);

                HBox row = new HBox(12, avatar, text);
                row.getStyleClass().add("activity-row");
                row.setAlignment(Pos.CENTER_LEFT);

                setGraphic(row);
            }
        });
    }

    private void applyActivityFilter(String query) {
        String q = (query == null) ? "" : query.trim().toLowerCase();

        filteredActivity.setPredicate(a -> {
            if (a == null) return false;

            // role filter
            if (!policy.isAdmin(appState)) {
                String pn = safe(a.getProjectName());
                boolean allowed = store.getProjects().stream().anyMatch(p -> p != null && p.getName() != null
                        && p.getName().equals(pn) && policy.canViewProject(appState, p));
                if (!allowed) return false;
            }

            if (q.isBlank()) return true;

            String p = safe(a.getProjectName()).toLowerCase();
            String m = safe(a.getMessage()).toLowerCase();
            return p.contains(q) || m.contains(q);
        });

        rebuildActivityFeed();
    }

    private void updateActivityPlaceholder() {
        List<Node> actions = new ArrayList<>();

        Button openTasks = new Button("Open Tasks");
        openTasks.getStyleClass().add("subtle");
        openTasks.setOnAction(e -> appState.setCurrentPage(PageId.TASKS));
        actions.add(openTasks);

        if (policy.canCreateTasks(appState) || policy.isAdmin(appState)) {
            Button createTask = new Button("Create Task");
            createTask.getStyleClass().add("primary");
            createTask.setOnAction(e -> quickCreateTaskForSelected());
            actions.add(0, createTask);
        }

        EmptyStatePane empty = new EmptyStatePane(
                "No activity yet",
                "Create your first task to kick things off and activity will appear here.",
                actions.toArray(new Node[0])
        );
        activityList.setPlaceholder(empty);
    }

    private void updateTodayHints(int dueToday, int inProgress, int blocked) {
        if (dueToday == 0) {
            dueTodayCard.setHint("You are clear for today.");
        } else if (dueToday <= 2) {
            dueTodayCard.setHint("Light day - keep it moving.");
        } else {
            dueTodayCard.setHint("Prioritize the top tasks.");
        }

        if (inProgress == 0) {
            inProgressCard.setHint("Nothing in flight yet.");
        } else if (inProgress <= 3) {
            inProgressCard.setHint("Good momentum.");
        } else {
            inProgressCard.setHint("High focus load.");
        }

        if (blocked == 0) {
            blockedCard.setHint("No blockers - nice.");
        } else if (blocked == 1) {
            blockedCard.setHint("One blocker needs attention.");
        } else {
            blockedCard.setHint(blocked + " blockers need attention.");
        }
    }

    private void updateBanner(int activeProjects, int dueToday, int overdue, int blocked, int inProgress) {
        String name = displayName();
        String greeting = greetingForNow();
        bannerTitle.setText(greeting + ", " + name);

        List<String> pieces = new ArrayList<>();
        if (dueToday > 0) pieces.add(dueToday + " due today");
        if (overdue > 0) pieces.add(overdue + " overdue");
        if (blocked > 0) pieces.add(blocked + " blocked");
        if (pieces.size() < 3 && inProgress > 0) pieces.add(inProgress + " in progress");

        if (pieces.isEmpty()) {
            bannerSub.setText("You are clear today - " + activeProjects + " active projects in motion.");
        } else {
            bannerSub.setText(String.join(" | ", pieces));
        }
    }

    private String displayName() {
        if (appState == null || appState.getSession() == null) return "there";
        String name = safe(appState.getSession().displayName());
        if (!name.isBlank()) return name;
        name = safe(appState.getSession().username());
        return name.isBlank() ? "there" : name;
    }

    private String greetingForNow() {
        int hour = LocalTime.now().getHour();
        if (hour < 12) return "Good morning";
        if (hour < 18) return "Good afternoon";
        return "Good evening";
    }

    private Trend trendFromSeries(List<Integer> series) {
        if (series == null || series.size() < 2) return new Trend("- steady", "metric-chip-neutral");
        int last = series.get(series.size() - 1);
        int prev = series.get(series.size() - 2);
        int delta = last - prev;
        if (delta == 0) return new Trend("- steady", "metric-chip-neutral");

        int base = Math.max(1, Math.abs(prev));
        int pct = Math.round((Math.abs(delta) * 100f) / base);
        String prefix = delta > 0 ? "^ +" : "v ";
        String label = prefix + Math.abs(delta) + " (" + pct + "%) vs yesterday";
        return new Trend(label, delta > 0 ? "metric-chip-up" : "metric-chip-down");
    }

    private String buildContributorTooltip(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) return "Top contributors: no completed tasks yet.";

        Map<String, Integer> counts = new HashMap<>();
        for (Task t : tasks) {
            if (t == null || t.getStatus() != TaskStatus.DONE) continue;
            Member assignee = t.getAssignee();
            if (assignee == null) continue;
            String name = safe(assignee.getName());
            if (name.isBlank()) continue;
            counts.put(name, counts.getOrDefault(name, 0) + 1);
        }

        if (counts.isEmpty()) return "Top contributors: no completed tasks yet.";

        List<Map.Entry<String, Integer>> list = new ArrayList<>(counts.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        int limit = Math.min(3, list.size());
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Integer> entry = list.get(i);
            parts.add(entry.getKey() + " (" + entry.getValue() + ")");
        }
        return "Top contributors this week: " + String.join(", ", parts);
    }

    private void refreshAll() {
        int projectsVisible = 0;
        int activeProjects = 0;

        int totalTasks = 0;
        int done = 0;
        int overdue = 0;
        int open = 0;
        int dueToday = 0;
        int inProgress = 0;
        int blocked = 0;

        List<Project> visibleProjects = new ArrayList<>();
        List<Task> visibleTasks = new ArrayList<>();

        for (Project p : store.getProjects()) {
            if (p == null) continue;
            if (!policy.canViewProject(appState, p)) continue;

            projectsVisible++;
            visibleProjects.add(p);
            if (p.getStatus() == Project.ProjectStatus.ACTIVE) activeProjects++;

            for (Task t : p.getTasks()) {
                if (t == null) continue;

                // USER requirement: only things assigned to them
                if (!policy.isAdmin(appState) && !policy.isAssignedToMe(appState, t)) continue;

                visibleTasks.add(t);
                totalTasks++;
                TaskStatus s = t.getStatus();
                if (s == TaskStatus.DONE) done++;
                if (s != TaskStatus.DONE) open++;

                LocalDate due = safeDueDate(t, LocalDate.now());
                if (due != null && s != TaskStatus.DONE) {
                    if (due.isBefore(LocalDate.now())) overdue++;
                    if (due.isEqual(LocalDate.now())) dueToday++;
                }
                if (s == TaskStatus.IN_PROGRESS) inProgress++;
                if (s == TaskStatus.BLOCKED) blocked++;
            }
        }

        projectsTile.setValue(activeProjects);
        doneTile.setValue(done);
        overdueTile.setValue(overdue);
        workloadTile.setValue(open);

        int points = 12;
        LocalDate today = LocalDate.now();
        List<Integer> projectSeries = buildProjectSeries(visibleProjects, points, activeProjects);
        List<Integer> doneSeries = buildTaskSeries(visibleTasks, points, (t, day) ->
                t.getStatus() == TaskStatus.DONE && !safeDueDate(t, today).isAfter(day), done
        );
        List<Integer> overdueSeries = buildTaskSeries(visibleTasks, points, (t, day) ->
                t.getStatus() != TaskStatus.DONE && safeDueDate(t, today).isBefore(day), overdue
        );
        List<Integer> workloadSeries = buildTaskSeries(visibleTasks, points, (t, day) ->
                t.getStatus() != TaskStatus.DONE && !safeDueDate(t, today).isAfter(day), open
        );

        projectsTile.setSeries(projectSeries);
        doneTile.setSeries(doneSeries);
        overdueTile.setSeries(overdueSeries);
        workloadTile.setSeries(workloadSeries);

        Trend projectTrend = trendFromSeries(projectSeries);
        Trend doneTrend = trendFromSeries(doneSeries);
        Trend overdueTrend = trendFromSeries(overdueSeries);
        Trend workloadTrend = trendFromSeries(workloadSeries);

        projectsTile.setChip(projectTrend.text(), projectTrend.style());
        doneTile.setChip(doneTrend.text(), doneTrend.style());
        overdueTile.setChip(overdueTrend.text(), overdueTrend.style());
        workloadTile.setChip(workloadTrend.text(), workloadTrend.style());

        String contributorTip = buildContributorTooltip(visibleTasks);
        projectsTile.setTooltipText(contributorTip);
        doneTile.setTooltipText(contributorTip);
        overdueTile.setTooltipText(contributorTip);
        workloadTile.setTooltipText(contributorTip);

        dueTodayCard.setValue(dueToday);
        inProgressCard.setValue(inProgress);
        blockedCard.setValue(blocked);
        updateTodayHints(dueToday, inProgress, blocked);
        updateBanner(activeProjects, dueToday, overdue, blocked, inProgress);

        rebuildRiskPanel(visibleTasks);
        rebuildWorkloadRow(visibleProjects, visibleTasks);
        updateActivityPlaceholder();

        // refresh activity predicate too (membership may have changed)
        applyActivityFilter(activityQuery);
    }

    private void rebuildActivityFeed() {
        activityFeed.clear();
        String lastProject = null;
        int updates = 0;
        for (ActivityItem item : filteredActivity) {
            if (item == null) continue;
            String projectName = safe(item.getProjectName());
            if (projectName.isBlank()) projectName = "General";
            if (!projectName.equals(lastProject)) {
                activityFeed.add(ActivityFeedItem.header(projectName));
                lastProject = projectName;
            }
            activityFeed.add(ActivityFeedItem.item(item));
            updates++;
        }
        if (updates == 0) {
            activityHint.setText("No updates yet - create a task to get things moving.");
        } else {
            activityHint.setText("Showing " + updates + " recent updates.");
        }
    }

    private void rebuildWorkloadRow(List<Project> projects, List<Task> tasks) {
        workloadRow.getChildren().clear();

        boolean isAdmin = policy.isAdmin(appState);
        String selfId = appState.getSession() == null ? "" : safe(appState.getSession().id());

        Map<String, WorkloadStats> statsMap = new HashMap<>();

        for (Project p : projects) {
            if (p == null) continue;
            for (Member m : p.getMembers()) {
                if (m == null) continue;
                if (!isAdmin && (selfId.isBlank() || !selfId.equals(m.getId()))) continue;
                statsMap.computeIfAbsent(m.getId(), id -> new WorkloadStats(id, safe(m.getName())));
            }
        }

        for (Task t : tasks) {
            if (t == null) continue;
            Member assignee = t.getAssignee();
            if (assignee == null) continue;
            String id = safe(assignee.getId());
            if (id.isBlank()) continue;
            if (!isAdmin && (selfId.isBlank() || !selfId.equals(id))) continue;

            WorkloadStats stats = statsMap.computeIfAbsent(id, key -> new WorkloadStats(id, safe(assignee.getName())));
            TaskStatus s = t.getStatus();
            if (s == TaskStatus.DONE) {
                stats.done++;
            } else {
                stats.open++;
            }
            if (s == TaskStatus.BLOCKED) stats.blocked++;

            LocalDate due = safeDueDate(t, LocalDate.now());
            if (s != TaskStatus.DONE && due != null && due.isBefore(LocalDate.now())) {
                stats.overdue++;
            }
        }

        if (statsMap.isEmpty()) {
            workloadHint.setText("Assign tasks to see workload balance.");
            Label empty = new Label("No workload data yet.");
            empty.getStyleClass().add("muted");
            workloadRow.getChildren().add(empty);
            return;
        }

        List<WorkloadStats> list = new ArrayList<>(statsMap.values());
        list.sort(Comparator
                .comparingInt(WorkloadStats::open).reversed()
                .thenComparing(WorkloadStats::name, String.CASE_INSENSITIVE_ORDER));

        int overdueTotal = list.stream().mapToInt(s -> s.overdue).sum();
        int blockedTotal = list.stream().mapToInt(s -> s.blocked).sum();
        int openTotal = list.stream().mapToInt(s -> s.open).sum();

        if (openTotal == 0) {
            workloadHint.setText("No active workload right now.");
        } else if (overdueTotal + blockedTotal == 0) {
            workloadHint.setText("Balanced right now - no blockers or overdue items.");
        } else {
            workloadHint.setText("Some workload is at risk - check blockers and overdue tasks.");
        }

        int maxOpen = list.stream().mapToInt(WorkloadStats::open).max().orElse(0);
        for (WorkloadStats stats : list) {
            WorkloadCard card = new WorkloadCard();
            card.setStats(stats, maxOpen);
            workloadRow.getChildren().add(card);
        }
    }

    private void buildRiskPanel() {
        riskCard.getStyleClass().add("card");
        riskCard.setPadding(new Insets(12));

        Label title = new Label("At Risk");
        title.getStyleClass().add("section-title");
        riskHint.getStyleClass().add("section-hint");

        riskList.setItems(riskItems);
        riskList.getStyleClass().addAll("risk-list");
        riskList.setPlaceholder(new EmptyStatePane(
                "All clear",
                "No at-risk tasks right now. Keep the momentum."
        ));

        riskList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Task item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }

                Label title = new Label(safe(item.getTitle()));
                title.getStyleClass().add("risk-title");

                String projectName = findProjectName(item);
                String due = item.getDueDate() == null ? "-" : item.getDueDate().toString();
                String priority = item.getPriority() == null ? "-" : item.getPriority().name();

                Label meta = new Label(projectName + " | " + due + " | " + priority);
                meta.getStyleClass().add("risk-meta");

                Label badge = new Label(isCritical(item) ? "Critical" : "Overdue");
                badge.getStyleClass().addAll("risk-badge", "risk-critical");
                if (!isCritical(item)) {
                    badge.getStyleClass().remove("risk-critical");
                    badge.getStyleClass().add("risk-high");
                }

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                HBox top = new HBox(8, title, spacer, badge);
                top.setAlignment(Pos.CENTER_LEFT);

                VBox row = new VBox(4, top, meta);
                row.getStyleClass().add("risk-row");
                setGraphic(row);
            }
        });

        VBox.setVgrow(riskList, Priority.ALWAYS);
        riskCard.getChildren().addAll(title, riskHint, riskList);
    }

    private void rebuildRiskPanel(List<Task> tasks) {
        riskItems.clear();
        if (tasks == null) return;

        LocalDate today = LocalDate.now();
        List<Task> risky = new ArrayList<>();
        for (Task t : tasks) {
            if (t == null) continue;
            if (t.getStatus() == TaskStatus.DONE) continue;
            LocalDate due = safeDueDate(t, today);
            boolean overdue = due != null && due.isBefore(today);
            boolean critical = isCritical(t);
            if (overdue || critical) risky.add(t);
        }

        risky.sort(Comparator
                .comparing((Task t) -> isCritical(t) ? 0 : 1)
                .thenComparing(t -> safeDueDate(t, today), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(t -> safe(t.getTitle()), String.CASE_INSENSITIVE_ORDER));

        int limit = Math.min(risky.size(), 8);
        if (limit > 0) {
            riskItems.addAll(risky.subList(0, limit));
            riskHint.setText("Prioritize these next.");
        } else {
            riskHint.setText("All clear - nothing critical right now.");
        }
    }

    private void requestRefresh() {
        refreshDelay.playFromStart();
    }

    private void applyEntryAnimations(Node... nodes) {
        if (nodes == null) return;
        int index = 0;
        for (Node node : nodes) {
            if (node == null) continue;
            animateIn(node, index++);
        }
    }

    private void animateIn(Node node, int index) {
        node.setOpacity(0);
        node.setTranslateY(6);

        FadeTransition fade = new FadeTransition(Duration.millis(260), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition slide = new TranslateTransition(Duration.millis(260), node);
        slide.setFromY(6);
        slide.setToY(0);
        slide.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition anim = new ParallelTransition(fade, slide);
        anim.setDelay(Duration.millis(index * 60L));
        anim.play();
    }

    private void showQuickCreateMenu(Button anchor) {
        ContextMenu menu = new ContextMenu();

        if (policy.isAdmin(appState)) {
            MenuItem newProject = new MenuItem("New Project");
            newProject.setOnAction(e -> quickCreateProject());
            menu.getItems().add(newProject);
        }

        if (policy.canCreateTasks(appState) || policy.isAdmin(appState)) {
            MenuItem newTask = new MenuItem("New Task (Selected Project)");
            newTask.setOnAction(e -> quickCreateTaskForSelected());
            menu.getItems().add(newTask);
        }

        if (menu.getItems().isEmpty()) return;

        menu.show(anchor, Side.BOTTOM, 0, 4);
    }

    private void quickCreateProject() {
        if (!policy.isAdmin(appState)) return;

        TextInputDialog d = new TextInputDialog();
        d.setTitle("New Project");
        d.setHeaderText("Create a project");
        d.setContentText("Project name:");
        d.showAndWait().ifPresent(name -> {
            String n = name.trim();
            if (n.isBlank()) return;
            if (isDuplicateProjectName(n)) {
                alertInfo("Duplicate project", "A project with that name already exists.");
                return;
            }
            Project p = new Project(n);
            store.createProject(p);
            appState.setSelectedProject(p);
        });
    }

    private void quickCreateTaskForSelected() {
        if (!policy.canCreateTasks(appState) && !policy.isAdmin(appState)) return;

        Project p = appState.getSelectedProject();
        if (p == null) {
            alertInfo("No project selected", "Select a project first, then create a task.");
            return;
        }
        if (p.getMembers().isEmpty()) {
            alertInfo("No members yet", "Go to Team, add members to the project, then create tasks.");
            return;
        }

        CreateTaskDialog d = new CreateTaskDialog(p, appState);
        d.showAndWait().ifPresent(t -> {
            if (isDuplicateTaskTitle(p, t.getTitle())) {
                alertInfo("Duplicate task", "A task with that title already exists in this project.");
                return;
            }
            store.addTask(p, t);
        });
    }

    private void alertInfo(String header, String text) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("ProjectPilot");
        a.setHeaderText(header);
        a.setContentText(text);
        a.showAndWait();
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

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String initials(String name) {
        String s = safe(name).trim();
        if (s.isBlank()) return "-";
        String[] parts = s.split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase();
        String first = parts[0].substring(0, 1);
        String last = parts[parts.length - 1].substring(0, 1);
        return (first + last).toUpperCase();
    }

    private String findProjectName(Task task) {
        if (task == null) return "-";
        for (Project p : store.getProjects()) {
            if (p == null) continue;
            if (p.getTasks().contains(task)) return safe(p.getName());
        }
        for (Project p : store.getHistoryProjects()) {
            if (p == null) continue;
            if (p.getTasks().contains(task)) return safe(p.getName());
        }
        return "-";
    }

    private boolean isCritical(Task task) {
        if (task == null) return false;
        var prio = task.getPriority();
        return prio != null && "CRITICAL".equalsIgnoreCase(prio.name());
    }

    private LocalDate safeDueDate(Task t, LocalDate fallback) {
        if (t == null) return fallback;
        LocalDate due = t.getDueDate();
        return due == null ? fallback : due;
    }

    private List<Integer> buildProjectSeries(List<Project> projects, int points, int fallbackValue) {
        LocalDate today = LocalDate.now();
        List<Integer> out = new ArrayList<>();
        for (int i = points - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            int count = 0;
            for (Project p : projects) {
                if (p == null) continue;
                if (p.getStatus() != Project.ProjectStatus.ACTIVE) continue;
                LocalDate start = p.getStartDate() == null ? day : p.getStartDate();
                LocalDate end = p.getEndDate() == null ? day : p.getEndDate();
                if (!start.isAfter(day) && !end.isBefore(day)) count++;
            }
            out.add(count);
        }
        return normalizeSeries(out, fallbackValue);
    }

    private List<Integer> buildTaskSeries(List<Task> tasks, int points,
                                          java.util.function.BiPredicate<Task, LocalDate> predicate,
                                          int fallbackValue) {
        LocalDate today = LocalDate.now();
        List<Integer> out = new ArrayList<>();
        for (int i = points - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            int count = 0;
            for (Task t : tasks) {
                if (t == null) continue;
                if (predicate.test(t, day)) count++;
            }
            out.add(count);
        }
        return normalizeSeries(out, fallbackValue);
    }

    private List<Integer> normalizeSeries(List<Integer> series, int fallbackValue) {
        if (series.isEmpty()) return series;
        boolean allZero = true;
        for (int v : series) {
            if (v != 0) {
                allZero = false;
                break;
            }
        }
        if (!allZero || fallbackValue <= 0) return series;

        int n = series.size();
        int start = Math.max(0, fallbackValue - n + 1);
        List<Integer> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int v = start + i;
            if (v > fallbackValue) v = fallbackValue;
            out.add(v);
        }
        return out;
    }

    private static class MetricTile extends VBox {
        private final Label label = new Label();
        private final Label value = new Label();
        private final Label chip = new Label();
        private final Sparkline sparkline = new Sparkline();
        private final Tooltip tooltip = new Tooltip();
        private final IntegerProperty displayValue = new SimpleIntegerProperty(0);
        private Timeline valueAnim;

        MetricTile(String title, int initialValue) {
            getStyleClass().add("metric-tile");
            setPadding(new Insets(16));
            setSpacing(6);
            setMinHeight(92);
            setPrefWidth(240);

            label.setText(title);
            label.getStyleClass().add("metric-label");

            displayValue.set(initialValue);
            value.setText(Integer.toString(initialValue));
            value.getStyleClass().addAll("metric-value", "metric-value-hero");

            chip.getStyleClass().add("metric-chip");

            sparkline.setMinHeight(34);
            sparkline.setPrefHeight(34);
            VBox.setVgrow(sparkline, Priority.NEVER);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox top = new HBox(8, label, spacer, chip);
            top.setAlignment(Pos.CENTER_LEFT);

            tooltip.setShowDelay(Duration.millis(250));
            Tooltip.install(this, tooltip);

            displayValue.addListener((obs, oldV, newV) -> value.setText(Integer.toString(newV.intValue())));

            getChildren().addAll(top, value, sparkline);
        }

        void setValue(int v) {
            if (valueAnim != null) valueAnim.stop();
            int start = displayValue.get();
            if (start == v) return;

            valueAnim = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(displayValue, start)),
                    new KeyFrame(Duration.millis(420), new KeyValue(displayValue, v, Interpolator.EASE_OUT))
            );
            valueAnim.play();
        }

        void setSeries(List<Integer> values) { sparkline.setSeries(values); }

        void setChip(String text, String style) {
            chip.setText(text == null ? "" : text);
            chip.getStyleClass().removeAll("metric-chip-up", "metric-chip-down", "metric-chip-neutral");
            if (style != null && !style.isBlank()) chip.getStyleClass().add(style);
        }

        void setTooltipText(String text) {
            tooltip.setText(text == null ? "" : text);
        }
    }

    private static final class Sparkline extends Region {
        private final Polyline line = new Polyline();
        private List<Integer> series = List.of();

        Sparkline() {
            getStyleClass().add("sparkline");
            line.getStyleClass().add("sparkline-line");
            getChildren().add(line);
        }

        void setSeries(List<Integer> values) {
            series = values == null ? List.of() : values;
            requestLayout();
        }

        @Override
        protected void layoutChildren() {
            double w = getWidth();
            double h = getHeight();
            if (w <= 0 || h <= 0 || series.isEmpty()) {
                line.getPoints().clear();
                return;
            }

            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (int v : series) {
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            if (min == max) max = min + 1;

            double padX = 2;
            double padY = 2;
            double usableW = Math.max(1, w - padX * 2);
            double usableH = Math.max(1, h - padY * 2);

            int count = series.size();
            line.getPoints().clear();
            for (int i = 0; i < count; i++) {
                double x = padX + (usableW * i / Math.max(1, count - 1));
                double norm = (series.get(i) - min) / (double) (max - min);
                double y = padY + (usableH * (1 - norm));
                line.getPoints().addAll(x, y);
            }
        }
    }

    private static final class TodayCard extends VBox {
        private final Label title = new Label();
        private final Label value = new Label();
        private final Label caption = new Label();
        private final Label hint = new Label();

        TodayCard(String label) {
            getStyleClass().add("today-card");
            setPadding(new Insets(12, 14, 12, 14));
            setSpacing(4);
            setMinHeight(84);

            title.setText(label == null ? "" : label);
            title.getStyleClass().add("today-title");

            value.setText("0");
            value.getStyleClass().add("today-value");

            caption.setText("tasks");
            caption.getStyleClass().add("today-caption");

            hint.getStyleClass().add("today-hint");
            getChildren().addAll(title, value, caption, hint);
        }

        void setValue(int count) {
            value.setText(Integer.toString(count));
        }

        void setHint(String text) {
            hint.setText(text == null ? "" : text);
        }
    }

    private static final class ActivityFeedItem {
        private final String header;
        private final ActivityItem activity;

        private ActivityFeedItem(String header, ActivityItem activity) {
            this.header = header;
            this.activity = activity;
        }

        static ActivityFeedItem header(String title) {
            return new ActivityFeedItem(title, null);
        }

        static ActivityFeedItem item(ActivityItem activity) {
            return new ActivityFeedItem(null, activity);
        }

        boolean isHeader() { return header != null; }
        String header() { return header == null ? "" : header; }
        ActivityItem activity() { return activity; }
    }

    private record Trend(String text, String style) {}

    private static final class WorkloadStats {
        private final String id;
        private final String name;
        private int open;
        private int done;
        private int blocked;
        private int overdue;

        WorkloadStats(String id, String name) {
            this.id = id;
            this.name = name == null || name.isBlank() ? "Member" : name;
        }

        int open() { return open; }
        String name() { return name; }
    }

    private enum RiskLevel {
        LOW("Low", "risk-low"),
        MEDIUM("Medium", "risk-medium"),
        HIGH("High", "risk-high"),
        CRITICAL("At Risk", "risk-critical");

        private final String label;
        private final String style;

        RiskLevel(String label, String style) {
            this.label = label;
            this.style = style;
        }
    }

    private static final class WorkloadCard extends VBox {
        private final Label nameLabel = new Label();
        private final Label metaLabel = new Label();
        private final Label badge = new Label();
        private final ProgressBar bar = new ProgressBar(0);

        WorkloadCard() {
            getStyleClass().add("workload-card");
            setPadding(new Insets(12, 14, 12, 14));
            setSpacing(6);
            setMinWidth(220);

            nameLabel.getStyleClass().add("workload-name");
            metaLabel.getStyleClass().add("workload-meta");

            badge.getStyleClass().add("risk-badge");

            bar.getStyleClass().add("workload-bar");
            bar.setMinHeight(8);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox header = new HBox(8, nameLabel, spacer, badge);
            header.setAlignment(Pos.CENTER_LEFT);

            getChildren().addAll(header, metaLabel, bar);
        }

        void setStats(WorkloadStats stats, int maxOpen) {
            nameLabel.setText(stats.name());
            metaLabel.setText("Open " + stats.open + " | Overdue " + stats.overdue + " | Blocked " + stats.blocked);

            double progress = maxOpen <= 0 ? 0 : Math.min(1.0, stats.open / (double) maxOpen);
            bar.setProgress(progress);

            RiskLevel level = riskFor(stats);
            badge.setText(level.label);
            badge.getStyleClass().removeAll("risk-low", "risk-medium", "risk-high", "risk-critical");
            badge.getStyleClass().add(level.style);
        }

        private RiskLevel riskFor(WorkloadStats stats) {
            if (stats.open == 0) return RiskLevel.LOW;
            int score = stats.overdue * 2 + stats.blocked;
            if (stats.open >= 6) score += 1;
            if (score >= 4) return RiskLevel.CRITICAL;
            if (score >= 2) return RiskLevel.HIGH;
            if (score >= 1) return RiskLevel.MEDIUM;
            return RiskLevel.LOW;
        }
    }
}






