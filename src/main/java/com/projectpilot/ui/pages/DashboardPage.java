package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.ActivityItem;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.TaskStatus;
import com.projectpilot.security.AccessPolicy;
import com.projectpilot.ui.dialogs.CreateTaskDialog;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;
import java.util.HashSet;
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

    private final MetricTile projectsTile = new MetricTile("Projects", "0");
    private final MetricTile activeTasksTile = new MetricTile("Active Tasks", "0");
    private final MetricTile blockedTile = new MetricTile("Blocked", "0");
    private final MetricTile completionTile = new MetricTile("Completion", "0%");

    private final TableView<ActivityItem> activityTable = new TableView<>();
    private final FilteredList<ActivityItem> filteredActivity;

    private String activityQuery = "";

    public DashboardPage(InMemoryStore store, AppState appState) {
        this.store = store;
        this.appState = appState;
        this.filteredActivity = new FilteredList<>(store.getActivity(), a -> true);

        setPadding(new Insets(14));

        title.getStyleClass().add("page-title");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 700;");

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
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);


        HBox topBar = new HBox(12, title, spacer, search, newBtn, refreshBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("card");
        topBar.setPadding(new Insets(12));

        HBox metrics = new HBox(12, projectsTile, activeTasksTile, blockedTile, completionTile);
        metrics.setPadding(new Insets(12, 0, 0, 0));

        VBox activityCard = new VBox(10);
        activityCard.getStyleClass().add("card");
        activityCard.setPadding(new Insets(12));

        Label activityTitle = new Label("Recent Activity");
        activityTitle.getStyleClass().add("section-title");

        setupActivityTable();
        VBox.setVgrow(activityTable, Priority.ALWAYS);

        activityCard.getChildren().addAll(activityTitle, activityTable);

        VBox center = new VBox(12, metrics, activityCard);
        VBox.setVgrow(activityCard, Priority.ALWAYS);

        setTop(topBar);
        setCenter(center);

        search.textProperty().addListener((obs, oldV, newV) -> {
            activityQuery = (newV == null) ? "" : newV;
            applyActivityFilter(activityQuery);
        });

        refreshBtn.setOnAction(e -> refreshAll());
        newBtn.setOnAction(e -> showQuickCreateMenu(newBtn));

        store.getProjects().addListener((ListChangeListener<Project>) c -> refreshAll());
        store.getActivity().addListener((ListChangeListener<ActivityItem>) c -> refreshAll());
        appState.selectedProjectProperty().addListener((obs, oldV, newV) -> refreshAll());
        appState.sessionProperty().addListener((obs, oldV, newV) -> refreshAll());

        refreshAll();
    }

    private void setupActivityTable() {
        activityTable.setItems(filteredActivity);

        activityTable.getStyleClass().add("pp-table");
        activityTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        activityTable.setFixedCellSize(38);

        Label ph = new Label("No activity yet.");
        ph.getStyleClass().add("muted");
        activityTable.setPlaceholder(ph);

        TableColumn<ActivityItem, String> timeCol = new TableColumn<>("Time");
        timeCol.setPrefWidth(90);
        timeCol.setMaxWidth(90);
        timeCol.setResizable(false);

        TableColumn<ActivityItem, String> projectCol = new TableColumn<>("Project");
        projectCol.setPrefWidth(220);
        projectCol.setMaxWidth(260);

        TableColumn<ActivityItem, String> msgCol = new TableColumn<>("Detail");
        msgCol.setPrefWidth(600);

        timeCol.setCellValueFactory(cd ->
                Bindings.createStringBinding(() ->
                        cd.getValue().getTime() == null ? "-" : cd.getValue().getTime().format(TIME)
                )
        );

        projectCol.setCellValueFactory(cd ->
                Bindings.createStringBinding(() -> safe(cd.getValue().getProjectName()))
        );

        msgCol.setCellValueFactory(cd ->
                Bindings.createStringBinding(() -> safe(cd.getValue().getMessage()))
        );

        activityTable.getColumns().setAll(timeCol, projectCol, msgCol);
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
    }

    private void refreshAll() {
        int projectsVisible = 0;

        int totalTasks = 0;
        int done = 0;
        int blocked = 0;
        int active = 0;

        for (Project p : store.getProjects()) {
            if (p == null) continue;
            if (!policy.canViewProject(appState, p)) continue;

            projectsVisible++;

            for (Task t : p.getTasks()) {
                if (t == null) continue;

                // USER requirement: only things assigned to them
                if (!policy.isAdmin(appState) && !policy.isAssignedToMe(appState, t)) continue;

                totalTasks++;
                TaskStatus s = t.getStatus();
                if (s == TaskStatus.DONE) done++;
                else active++;
                if (s == TaskStatus.BLOCKED) blocked++;
            }
        }

        int completionPct = (totalTasks == 0) ? 0 : (int) Math.round((done * 100.0) / totalTasks);

        projectsTile.setValue(Integer.toString(projectsVisible));
        activeTasksTile.setValue(Integer.toString(active));
        blockedTile.setValue(Integer.toString(blocked));
        completionTile.setValue(completionPct + "%");

        // refresh activity predicate too (membership may have changed)
        applyActivityFilter(activityQuery);
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

        CreateTaskDialog d = new CreateTaskDialog(p);
        d.showAndWait().ifPresent(t -> store.addTask(p, t));
    }

    private void alertInfo(String header, String text) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("ProjectPilot");
        a.setHeaderText(header);
        a.setContentText(text);
        a.showAndWait();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private static class MetricTile extends VBox {
        private final Label label = new Label();
        private final Label value = new Label();

        MetricTile(String title, String initialValue) {
            getStyleClass().add("metric-tile");
            setPadding(new Insets(14));
            setSpacing(6);
            setMinHeight(92);
            setPrefWidth(240);

            label.setText(title);
            label.getStyleClass().add("muted");

            value.setText(initialValue);
            value.setStyle("-fx-font-size: 22px; -fx-font-weight: 800;");

            getChildren().addAll(label, value);
        }

        void setValue(String v) { value.setText(v); }
    }
}
