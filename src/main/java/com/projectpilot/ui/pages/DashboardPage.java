package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.ActivityItem;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.TaskStatus;
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

public class DashboardPage extends BorderPane {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final InMemoryStore store;
    private final AppState appState;

    // Top bar
    private final Label title = new Label("ProjectPilot");
    private final TextField search = new TextField();
    private final Button newBtn = new Button("New");
    private final Button refreshBtn = new Button("Refresh");

    // Metric tiles
    private final MetricTile projectsTile = new MetricTile("Projects", "0");
    private final MetricTile activeTasksTile = new MetricTile("Active Tasks", "0");
    private final MetricTile blockedTile = new MetricTile("Blocked", "0");
    private final MetricTile completionTile = new MetricTile("Completion", "0%");

    // Activity table
    private final TableView<ActivityItem> activityTable = new TableView<>();
    private final FilteredList<ActivityItem> filteredActivity;

    public DashboardPage(InMemoryStore store, AppState appState) {
        this.store = store;
        this.appState = appState;
        this.filteredActivity = new FilteredList<>(store.getActivity(), a -> true);

        setPadding(new Insets(14));

        // --- Top bar ---
        title.getStyleClass().add("page-title");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 700;");

        search.setPromptText("Search...");
        search.setPrefWidth(320);

        newBtn.getStyleClass().add("primary");
        refreshBtn.getStyleClass().add("secondary");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topBar = new HBox(12, title, spacer, search, newBtn, refreshBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("card");
        topBar.setPadding(new Insets(12));

        // --- Metrics row ---
        HBox metrics = new HBox(12, projectsTile, activeTasksTile, blockedTile, completionTile);
        metrics.setPadding(new Insets(12, 0, 0, 0));

        // --- Activity card ---
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

        // --- Wiring ---
        search.textProperty().addListener((obs, oldV, newV) -> applyActivityFilter(newV));

        refreshBtn.setOnAction(e -> refreshAll());
        newBtn.setOnAction(e -> showQuickCreateMenu(newBtn));

        // Auto-refresh when projects list changes
        store.getProjects().addListener((ListChangeListener<Project>) c -> refreshAll());

        // Auto-refresh when activity changes
        store.getActivity().addListener((ListChangeListener<ActivityItem>) c -> refreshAll());

        // Auto-refresh when selected project changes
        appState.selectedProjectProperty().addListener((obs, oldV, newV) -> refreshAll());

        // Initial
        refreshAll();
    }

    private void setupActivityTable() {
        activityTable.setItems(filteredActivity);

        activityTable.getStyleClass().add("pp-table");
        activityTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        activityTable.setFixedCellSize(38);

        // Placeholder (theme-friendly)
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

    // ✅ This was missing / out of scope in your file (caused "cannot find symbol" at the listener)
    private void applyActivityFilter(String query) {
        String q = (query == null) ? "" : query.trim().toLowerCase();
        if (q.isBlank()) {
            filteredActivity.setPredicate(a -> true);
            return;
        }
        filteredActivity.setPredicate(a -> {
            String p = safe(a.getProjectName()).toLowerCase();
            String m = safe(a.getMessage()).toLowerCase();
            return p.contains(q) || m.contains(q);
        });
    }

    private void refreshAll() {
        int projects = store.getProjects().size();

        int totalTasks = 0;
        int done = 0;
        int blocked = 0;
        int active = 0;

        for (Project p : store.getProjects()) {
            for (Task t : p.getTasks()) {
                totalTasks++;
                TaskStatus s = t.getStatus();
                if (s == TaskStatus.DONE) done++;
                else active++;
                if (s == TaskStatus.BLOCKED) blocked++;
            }
        }

        int completionPct = (totalTasks == 0) ? 0 : (int) Math.round((done * 100.0) / totalTasks);

        projectsTile.setValue(Integer.toString(projects));
        activeTasksTile.setValue(Integer.toString(active));
        blockedTile.setValue(Integer.toString(blocked));
        completionTile.setValue(completionPct + "%");
    }

    private void showQuickCreateMenu(Button anchor) {
        ContextMenu menu = new ContextMenu();

        MenuItem newProject = new MenuItem("New Project");
        newProject.setOnAction(e -> quickCreateProject());

        MenuItem newTask = new MenuItem("New Task (Selected Project)");
        newTask.setOnAction(e -> quickCreateTaskForSelected());

        menu.getItems().addAll(newProject, newTask);
        menu.show(anchor, Side.BOTTOM, 0, 4);
    }

    private void quickCreateProject() {
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
        Project p = appState.getSelectedProject();
        if (p == null) {
            alertInfo("No project selected", "Select a project first, then create a task.");
            return;
        }
        if (p.getMembers().isEmpty()) {
            alertInfo("No members yet", "Go to Projects → Add Member, then create tasks and assign them.");
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

    // Simple metric tile
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
