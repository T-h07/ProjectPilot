package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.ChecklistItem;
import com.projectpilot.model.Member;
import com.projectpilot.model.Phase;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.Priority;
import com.projectpilot.model.enums.TaskStatus;
import com.projectpilot.security.AccessPolicy;
import com.projectpilot.ui.components.ChecklistEditor;
import com.projectpilot.ui.components.ProjectPicker;
import com.projectpilot.ui.dialogs.CreateTaskDialog;
import com.projectpilot.ui.dialogs.DialogTheme;
import com.projectpilot.util.TaskViewStore;
import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TasksPage extends VBox {

    private final InMemoryStore store;
    private final AppState appState;
    private final AccessPolicy policy = new AccessPolicy();

    private final Label header = new Label("Tasks");

    private final ObservableList<Task> taskSource = FXCollections.observableArrayList();
    private final FilteredList<Task> filteredTasks = new FilteredList<>(taskSource, t -> true);

    private final ListView<Task> tasksList = new ListView<>();

    private final TextField searchField = new TextField();
    private final CheckBox showDone = new CheckBox("Show Done");
    private final TaskViewStore viewStore = new TaskViewStore();
    private final ObservableList<TaskView> viewOptions = FXCollections.observableArrayList();
    private final ComboBox<TaskView> viewBox = new ComboBox<>();
    private final Button saveView = new Button("Save View");
    private final Button deleteView = new Button("Delete View");

    private final ComboBox<FilterOption<TaskStatus>> statusFilter = new ComboBox<>();
    private final ComboBox<FilterOption<Priority>> priorityFilter = new ComboBox<>();
    private final ComboBox<FilterOption<DueRange>> dueFilter = new ComboBox<>();
    private final CheckBox assignedToMe = new CheckBox("Assigned to me");

    private Task bound;

    private final ObjectProperty<Task> selectedTask = new SimpleObjectProperty<>();

    private final TextField titleField = new TextField();
    private final TextArea descField = new TextArea();
    private final ComboBox<TaskStatus> statusBox = new ComboBox<>();
    private final ComboBox<Priority> priorityBox = new ComboBox<>();
    private final DatePicker duePicker = new DatePicker();

    private final ComboBox<Phase> phaseBox = new ComboBox<>();
    private final ComboBox<Member> assigneeBox = new ComboBox<>();
    private final ChecklistEditor checklistEditor = new ChecklistEditor();

    private Project boundProject;

    private final Set<Task> hooked = new HashSet<>();
    private final PauseTransition rebuildDelay = new PauseTransition(Duration.millis(120));
    private final PauseTransition filterDelay = new PauseTransition(Duration.millis(120));

    private final BooleanBinding canCreate;
    private final BooleanBinding canSeeAll;
    private final BooleanBinding canEditSelected;
    private final BooleanBinding canEditMeta;
    private final BooleanBinding canReassign;

    private final ListChangeListener<Task> projectTasksListener = c -> {
        if (boundProject == null) return;

        while (c.next()) {
            if (c.wasAdded()) for (Task t : c.getAddedSubList()) hookTaskOnce(t);
            if (c.wasRemoved()) hooked.removeAll(c.getRemoved());
        }
        requestRebuild();
    };

    public TasksPage(InMemoryStore store, AppState appState) {
        this.store = store;
        this.appState = appState;

        this.canCreate = Bindings.createBooleanBinding(
                () -> policy.canCreateTasks(appState),
                appState.sessionProperty(),
                appState.selectedProjectProperty(),
                appState.currentProjectRoleProperty()
        );

        this.canSeeAll = Bindings.createBooleanBinding(
                () -> policy.canSeeAllProjectTasks(appState),
                appState.sessionProperty(),
                appState.selectedProjectProperty(),
                appState.currentProjectRoleProperty()
        );

        this.canEditSelected = Bindings.createBooleanBinding(
                () -> policy.canEditTask(appState, selectedTask.get()),
                appState.sessionProperty(),
                appState.selectedProjectProperty(),
                appState.currentProjectRoleProperty(),
                selectedTask
        );

        this.canEditMeta = Bindings.createBooleanBinding(
                () -> policy.canEditTaskMeta(appState),
                appState.sessionProperty(),
                appState.selectedProjectProperty(),
                appState.currentProjectRoleProperty()
        );

        this.canReassign = Bindings.createBooleanBinding(
                () -> policy.canReassignTasks(appState),
                appState.sessionProperty(),
                appState.selectedProjectProperty(),
                appState.currentProjectRoleProperty()
        );

        setPadding(new Insets(16));
        setSpacing(12);

        header.getStyleClass().add("page-title");

        ProjectPicker taskProjectPicker = new ProjectPicker(store, appState);
        taskProjectPicker.setPrefWidth(320);

        searchField.setPromptText("Search tasks...");
        searchField.setPrefWidth(320);
        searchField.textProperty().addListener((obs, ov, nv) -> requestFilter());

        viewBox.setItems(viewOptions);
        viewBox.setPrefWidth(200);
        viewBox.setPromptText("View");
        viewBox.setCellFactory(cb -> new ListCell<>() {
            @Override protected void updateItem(TaskView item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.name());
            }
        });
        viewBox.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(TaskView item, boolean empty) {
                super.updateItem(item, empty);
                setText(item == null ? "View" : item.name());
            }
        });
        viewBox.valueProperty().addListener((obs, ov, nv) -> {
            if (nv != null) applyView(nv);
        });

        showDone.setSelected(false);
        showDone.selectedProperty().addListener((obs, ov, nv) -> requestFilter());

        statusFilter.getItems().setAll(
                opt("All status", null),
                opt("TODO", TaskStatus.TODO),
                opt("IN PROGRESS", TaskStatus.IN_PROGRESS),
                opt("BLOCKED", TaskStatus.BLOCKED),
                opt("DONE", TaskStatus.DONE)
        );
        statusFilter.setValue(statusFilter.getItems().get(0));
        statusFilter.valueProperty().addListener((obs, ov, nv) -> requestFilter());

        priorityFilter.getItems().setAll(
                opt("All priority", null),
                opt("LOW", Priority.LOW),
                opt("MEDIUM", Priority.MEDIUM),
                opt("HIGH", Priority.HIGH)
        );
        priorityFilter.setValue(priorityFilter.getItems().get(0));
        priorityFilter.valueProperty().addListener((obs, ov, nv) -> requestFilter());

        dueFilter.getItems().setAll(
                opt("Any due date", DueRange.ANY),
                opt("Overdue", DueRange.OVERDUE),
                opt("Due today", DueRange.TODAY),
                opt("Due this week", DueRange.WEEK),
                opt("Due next 30 days", DueRange.MONTH)
        );
        dueFilter.setValue(dueFilter.getItems().get(0));
        dueFilter.valueProperty().addListener((obs, ov, nv) -> requestFilter());

        assignedToMe.selectedProperty().addListener((obs, ov, nv) -> requestFilter());
        assignedToMe.visibleProperty().bind(canSeeAll);
        assignedToMe.managedProperty().bind(assignedToMe.visibleProperty());

        Button newTask = new Button("New Task");
        newTask.getStyleClass().add("primary");
        newTask.visibleProperty().bind(canCreate);
        newTask.managedProperty().bind(newTask.visibleProperty());

        newTask.setOnAction(e -> {
            if (!canCreate.get()) return;

            Project p = appState.getSelectedProject();
            if (p == null) return;

            if (p.getMembers() == null || p.getMembers().isEmpty()) {
                Alert a = new Alert(Alert.AlertType.INFORMATION);
                a.setTitle("No project members yet");
                a.setHeaderText("Add at least one project member first");
                a.setContentText("Go to Team and add users/members to this project, then create tasks.");
                a.showAndWait();
                return;
            }

            CreateTaskDialog d = new CreateTaskDialog(p, appState);
            d.showAndWait().ifPresent(t -> {
                if (isDuplicateTaskTitle(p, t.getTitle())) {
                    alertInfo("Duplicate task", "A task with that title already exists in this project.");
                    return;
                }
                store.addTask(p, t);
                hookTaskOnce(t);
                rebuildTaskSource(p);
                requestFilter();
                tasksList.getSelectionModel().select(t);
            });
        });

        saveView.setOnAction(e -> saveCurrentView());
        deleteView.setOnAction(e -> deleteCurrentView());
        deleteView.disableProperty().bind(Bindings.createBooleanBinding(
                () -> viewBox.getValue() == null || viewBox.getValue().builtIn(),
                viewBox.valueProperty()
        ));

        saveView.getStyleClass().add("subtle");
        deleteView.getStyleClass().add("ghost");

        Label projectLabel = new Label("Project");
        projectLabel.getStyleClass().add("filter-label");
        VBox projectGroup = new VBox(4, projectLabel, taskProjectPicker);
        projectGroup.getStyleClass().add("filter-group");

        Label viewLabel = new Label("View");
        viewLabel.getStyleClass().add("filter-label");
        VBox viewGroup = new VBox(4, viewLabel, viewBox);
        viewGroup.getStyleClass().add("filter-group");

        Label searchLabel = new Label("Search");
        searchLabel.getStyleClass().add("filter-label");
        VBox searchGroup = new VBox(4, searchLabel, searchField);
        searchGroup.getStyleClass().add("filter-group");

        taskProjectPicker.setMaxWidth(Double.MAX_VALUE);
        viewBox.setMaxWidth(Double.MAX_VALUE);
        searchField.setMaxWidth(Double.MAX_VALUE);

        HBox topRow = new HBox(12, projectGroup, viewGroup, searchGroup, newTask);
        topRow.getStyleClass().add("filter-row");
        HBox.setHgrow(searchGroup, javafx.scene.layout.Priority.ALWAYS);

        HBox filterRow = new HBox(
                10,
                showDone,
                statusFilter,
                priorityFilter,
                dueFilter,
                assignedToMe,
                saveView,
                deleteView
        );
        filterRow.getStyleClass().add("filter-row");

        VBox toolbar = new VBox(8, topRow, filterRow);

        tasksList.setPrefWidth(420);
        tasksList.setItems(filteredTasks);
        tasksList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            selectedTask.set(newV);
            if (appState.getSelectedTask() != newV) {
                appState.setSelectedTask(newV);
            }
            bindTask(newV);
        });
        appState.selectedTaskProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) return;
            if (tasksList.getItems().contains(newV)) {
                tasksList.getSelectionModel().select(newV);
            }
        });

        statusBox.getItems().setAll(TaskStatus.values());
        priorityBox.getItems().setAll(Priority.values());

        descField.setPrefRowCount(6);
        descField.getStyleClass().add("pp-textarea");

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        duePicker.setPromptText("yyyy-MM-dd");
        duePicker.setConverter(new StringConverter<>() {
            @Override public String toString(LocalDate date) { return date == null ? "" : fmt.format(date); }
            @Override public LocalDate fromString(String s) {
                if (s == null) return null;
                String v = s.trim();
                if (v.isEmpty()) return null;
                try { return LocalDate.parse(v, fmt); } catch (Exception e) { com.projectpilot.util.AppLog.warn("tasks", "Failed to parse due date: " + (e == null ? "" : e.getMessage())); return null; }
            }
        });

        phaseBox.setPromptText("No phase");
        phaseBox.setCellFactory(cb -> new ListCell<>() {
            @Override protected void updateItem(Phase item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getName());
            }
        });
        phaseBox.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Phase item, boolean empty) {
                super.updateItem(item, empty);
                setText(item == null ? "No phase" : item.getName());
            }
        });

        assigneeBox.setPromptText("Unassigned");
        assigneeBox.setCellFactory(cb -> new ListCell<>() {
            @Override protected void updateItem(Member item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getName());
            }
        });
        assigneeBox.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Member item, boolean empty) {
                super.updateItem(item, empty);
                setText(item == null ? "Unassigned" : item.getName());
            }
        });

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);

        int r = 0;
        form.add(new Label("Title"), 0, r);
        form.add(titleField, 1, r++);

        form.add(new Label("Description"), 0, r);
        form.add(descField, 1, r++);

        form.add(new Label("Status"), 0, r);
        form.add(statusBox, 1, r++);

        form.add(new Label("Priority"), 0, r);
        form.add(priorityBox, 1, r++);

        form.add(new Label("Phase"), 0, r);
        form.add(phaseBox, 1, r++);

        form.add(new Label("Assignee"), 0, r);
        form.add(assigneeBox, 1, r++);

        form.add(new Label("Due date"), 0, r);
        form.add(duePicker, 1, r++);

        ColumnConstraints c1 = new ColumnConstraints();
        c1.setMinWidth(90);

        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(javafx.scene.layout.Priority.ALWAYS);

        form.getColumnConstraints().addAll(c1, c2);

        Label editorTitle = new Label("Task Details");
        editorTitle.getStyleClass().add("muted");

        Label checklistTitle = new Label("Checklist");
        checklistTitle.getStyleClass().add("muted");
        VBox checklistSection = new VBox(8, checklistTitle, checklistEditor);

        VBox editor = new VBox(12, editorTitle, form, checklistSection);
        editor.getStyleClass().add("card");

        HBox body = new HBox(14, tasksList, editor);
        HBox.setHgrow(editor, javafx.scene.layout.Priority.ALWAYS);

        getChildren().addAll(header, toolbar, body);

        rebuildDelay.setOnFinished(e -> {
            if (boundProject != null) rebuildTaskSource(boundProject);
        });
        filterDelay.setOnFinished(e -> applyFilterPreserveSelection());

        refresh(appState.getSelectedProject());
        appState.selectedProjectProperty().addListener((obs, oldV, newV) -> refresh(newV));
        appState.sessionProperty().addListener((obs, o, n) -> reloadViews(null));

        // Editing rules:
        // - Meta (title/priority/phase/assignee): Admin/Leader only
        // - Own task edits (status/desc/due): Admin/Leader or Member (assigned to them)
        titleField.disableProperty().bind(canEditMeta.not());
        priorityBox.disableProperty().bind(canEditMeta.not());
        phaseBox.disableProperty().bind(canEditMeta.not());
        assigneeBox.disableProperty().bind(canReassign.not());

        BooleanBinding canEditOwnFields = canEditSelected; // includes admin/leader + member for own tasks
        descField.disableProperty().bind(canEditOwnFields.not());
        statusBox.disableProperty().bind(canEditOwnFields.not());
        duePicker.disableProperty().bind(canEditMeta.not());
        checklistEditor.editableProperty().bind(canEditOwnFields);

        reloadViews("all");
    }

    private void refresh(Project p) {
        if (boundProject != null) {
            try { boundProject.getTasks().removeListener(projectTasksListener); } catch (Exception e) { com.projectpilot.util.AppLog.warn("tasks", "Failed to remove project tasks listener: " + (e == null ? "" : e.getMessage())); }
        }

        boundProject = p;
        hooked.clear();

        if (p == null) {
            header.setText("Tasks (no project selected)");
            taskSource.clear();
            phaseBox.setItems(FXCollections.observableArrayList());
            assigneeBox.setItems(FXCollections.observableArrayList());
            bindTask(null);
            return;
        }

        header.setText(canSeeAll.get() ? ("Tasks — " + p.getName()) : ("My Tasks — " + p.getName()));

        phaseBox.setItems(p.getPhases());
        assigneeBox.setItems(p.getMembers());

        rebuildTaskSource(p);
        p.getTasks().addListener(projectTasksListener);

        if (!filteredTasks.isEmpty()) tasksList.getSelectionModel().select(0);
        else bindTask(null);
    }

    private void rebuildTaskSource(Project p) {
        Task selected = tasksList.getSelectionModel().getSelectedItem();
        int selectedIndex = tasksList.getSelectionModel().getSelectedIndex();

        if (canSeeAll.get()) {
            taskSource.setAll(p.getTasks());
        } else {
            taskSource.setAll(p.getTasks().stream().filter(t -> policy.isAssignedToMe(appState, t)).toList());
        }

        for (Task t : p.getTasks()) hookTaskOnce(t);

        applyFilter();

        Task requested = appState.getSelectedTask();
        if (requested != null && filteredTasks.contains(requested)) {
            tasksList.getSelectionModel().select(requested);
            return;
        }

        if (selected != null && filteredTasks.contains(selected)) {
            tasksList.getSelectionModel().select(selected);
        } else if (!filteredTasks.isEmpty()) {
            int idx = Math.min(Math.max(selectedIndex, 0), filteredTasks.size() - 1);
            tasksList.getSelectionModel().select(idx);
        } else {
            tasksList.getSelectionModel().clearSelection();
            bindTask(null);
        }
    }

    private void hookTaskOnce(Task t) {
        if (t == null) return;
        if (!hooked.add(t)) return;

        // status changes can hide DONE tasks from list
        try {
            t.statusProperty().addListener((obs, ov, nv) -> {
                applyFilterPreserveSelection();
                // if USER is in “my tasks only”, also re-check visibility
                if (!canSeeAll.get()) requestRebuild();
            });
        } catch (Exception e) { com.projectpilot.util.AppLog.warn("tasks", "Failed to attach status listener: " + (e == null ? "" : e.getMessage())); }

        // assignee changes affect “my tasks only”
        try {
            t.assigneeProperty().addListener((obs, ov, nv) -> {
                if (!canSeeAll.get()) requestRebuild();
            });
        } catch (Exception e) { com.projectpilot.util.AppLog.warn("tasks", "Failed to attach assignee listener: " + (e == null ? "" : e.getMessage())); }
    }

    private void applyFilterPreserveSelection() {
        Task selected = tasksList.getSelectionModel().getSelectedItem();
        int selectedIndex = tasksList.getSelectionModel().getSelectedIndex();

        applyFilter();

        if (selected != null && filteredTasks.contains(selected)) {
            tasksList.getSelectionModel().select(selected);
        } else if (!filteredTasks.isEmpty()) {
            int idx = Math.min(Math.max(selectedIndex, 0), filteredTasks.size() - 1);
            tasksList.getSelectionModel().select(idx);
        } else {
            tasksList.getSelectionModel().clearSelection();
            bindTask(null);
        }
    }

    private void applyFilter() {
        boolean includeDone = showDone.isSelected();
        String q = searchField.getText();
        String query = (q == null) ? "" : q.trim().toLowerCase();
        TaskStatus status = valueOf(statusFilter);
        Priority priority = valueOf(priorityFilter);
        DueRange dueRange = valueOf(dueFilter);
        DueRange filterDueRange = dueRange == null ? DueRange.ANY : dueRange;
        boolean onlyMine = assignedToMe.isSelected() && canSeeAll.get();

        filteredTasks.setPredicate(t -> {
            if (t == null) return false;

            if (!includeDone && t.getStatus() == TaskStatus.DONE && status != TaskStatus.DONE) return false;
            if (status != null && t.getStatus() != status) return false;
            if (priority != null && t.getPriority() != priority) return false;
            if (onlyMine && !policy.isAssignedToMe(appState, t)) return false;

            if (filterDueRange != DueRange.ANY) {
                LocalDate due = t.getDueDate();
                if (due == null) return false;
                LocalDate today = LocalDate.now();
                switch (filterDueRange) {
                    case OVERDUE -> {
                        if (!due.isBefore(today)) return false;
                    }
                    case TODAY -> {
                        if (!due.equals(today)) return false;
                    }
                    case WEEK -> {
                        LocalDate end = today.plusDays(7);
                        if (due.isBefore(today) || due.isAfter(end)) return false;
                    }
                    case MONTH -> {
                        LocalDate end = today.plusDays(30);
                        if (due.isBefore(today) || due.isAfter(end)) return false;
                    }
                    default -> {}
                }
            }

            if (query.isEmpty()) return true;

            String title = (t.getTitle() == null) ? "" : t.getTitle().toLowerCase();
            String desc = (t.getDescription() == null) ? "" : t.getDescription().toLowerCase();

            String phase = "";
            try { phase = (t.getPhase() == null || t.getPhase().getName() == null) ? "" : t.getPhase().getName().toLowerCase(); }
            catch (Exception e) { com.projectpilot.util.AppLog.warn("tasks", "Failed reading task phase: " + (e == null ? "" : e.getMessage())); }

            String assignee = "";
            try { assignee = (t.getAssignee() == null || t.getAssignee().getName() == null) ? "" : t.getAssignee().getName().toLowerCase(); }
            catch (Exception e) { com.projectpilot.util.AppLog.warn("tasks", "Failed reading task assignee: " + (e == null ? "" : e.getMessage())); }

            return title.contains(query)
                    || desc.contains(query)
                    || phase.contains(query)
                    || assignee.contains(query);
        });
    }

    private void requestRebuild() {
        rebuildDelay.playFromStart();
    }

    private void requestFilter() {
        filterDelay.playFromStart();
    }

    private void bindTask(Task t) {
        if (bound != null) {
            titleField.textProperty().unbindBidirectional(bound.titleProperty());
            descField.textProperty().unbindBidirectional(bound.descriptionProperty());
            statusBox.valueProperty().unbindBidirectional(bound.statusProperty());
            priorityBox.valueProperty().unbindBidirectional(bound.priorityProperty());
            duePicker.valueProperty().unbindBidirectional(bound.dueDateProperty());

            try { phaseBox.valueProperty().unbindBidirectional(bound.phaseProperty()); } catch (Exception e) { com.projectpilot.util.AppLog.warn("tasks", "Failed to unbind phase binding: " + (e == null ? "" : e.getMessage())); }
            try { assigneeBox.valueProperty().unbindBidirectional(bound.assigneeProperty()); } catch (Exception e) { com.projectpilot.util.AppLog.warn("tasks", "Failed to unbind assignee binding: " + (e == null ? "" : e.getMessage())); }
        }

        bound = t;

        if (t == null) {
            titleField.setText("");
            descField.setText("");
            statusBox.setValue(null);
            priorityBox.setValue(null);
            duePicker.setValue(null);
            phaseBox.setValue(null);
            assigneeBox.setValue(null);
            checklistEditor.setItems(FXCollections.observableArrayList());
            return;
        }

        titleField.textProperty().bindBidirectional(t.titleProperty());
        descField.textProperty().bindBidirectional(t.descriptionProperty());
        statusBox.valueProperty().bindBidirectional(t.statusProperty());
        priorityBox.valueProperty().bindBidirectional(t.priorityProperty());
        duePicker.valueProperty().bindBidirectional(t.dueDateProperty());

        try { phaseBox.valueProperty().bindBidirectional(t.phaseProperty()); } catch (Exception e) { com.projectpilot.util.AppLog.warn("tasks", "Failed to bind phase: " + (e == null ? "" : e.getMessage())); }
        try { assigneeBox.valueProperty().bindBidirectional(t.assigneeProperty()); } catch (Exception e) { com.projectpilot.util.AppLog.warn("tasks", "Failed to bind assignee: " + (e == null ? "" : e.getMessage())); }

        statusBox.setValue(t.getStatus());
        priorityBox.setValue(t.getPriority());
        duePicker.setValue(t.getDueDate());
        try { phaseBox.setValue(t.getPhase()); } catch (Exception e) { com.projectpilot.util.AppLog.warn("tasks", "Failed to set phase value: " + (e == null ? "" : e.getMessage())); }
        try { assigneeBox.setValue(t.getAssignee()); } catch (Exception e) { com.projectpilot.util.AppLog.warn("tasks", "Failed to set assignee value: " + (e == null ? "" : e.getMessage())); }
        checklistEditor.setItems(t.getChecklist());
    }

    private void reloadViews(String selectId) {
        String toSelect = selectId;
        if (toSelect == null && viewBox.getValue() != null) {
            toSelect = viewBox.getValue().id();
        }

        viewOptions.setAll(buildBuiltInViews());

        String userId = currentUserId();
        for (TaskViewStore.TaskViewData data : viewStore.load(userId)) {
            viewOptions.add(new TaskView(data.id(), data.name(), data, false));
        }

        if (toSelect != null) {
            for (TaskView view : viewOptions) {
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

    private List<TaskView> buildBuiltInViews() {
        List<TaskView> builtIn = new ArrayList<>();
        builtIn.add(new TaskView(
                "all",
                "All tasks",
                new TaskViewStore.TaskViewData(
                        "all",
                        "All tasks",
                        "",
                        false,
                        null,
                        null,
                        DueRange.ANY.name(),
                        false
                ),
                true
        ));
        builtIn.add(new TaskView(
                "my-high",
                "My High Priority",
                new TaskViewStore.TaskViewData(
                        "my-high",
                        "My High Priority",
                        "",
                        false,
                        null,
                        Priority.HIGH.name(),
                        DueRange.ANY.name(),
                        true
                ),
                true
        ));
        builtIn.add(new TaskView(
                "due-week",
                "Due This Week",
                new TaskViewStore.TaskViewData(
                        "due-week",
                        "Due This Week",
                        "",
                        false,
                        null,
                        null,
                        DueRange.WEEK.name(),
                        false
                ),
                true
        ));
        return builtIn;
    }

    private void applyView(TaskView view) {
        if (view == null || view.data() == null) return;
        TaskViewStore.TaskViewData data = view.data();

        searchField.setText(data.query() == null ? "" : data.query());
        showDone.setSelected(data.showDone());

        TaskStatus status = safeEnum(TaskStatus.class, data.status(), null);
        Priority priority = safeEnum(Priority.class, data.priority(), null);
        DueRange due = safeEnum(DueRange.class, data.dueRange(), DueRange.ANY);

        selectOption(statusFilter, status);
        selectOption(priorityFilter, priority);
        selectOption(dueFilter, due);
        assignedToMe.setSelected(data.assignedToMe() && canSeeAll.get());

        requestFilter();
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
            List<TaskViewStore.TaskViewData> saved = new ArrayList<>(viewStore.load(userId));

            TaskViewStore.TaskViewData existing = null;
            for (TaskViewStore.TaskViewData v : saved) {
                if (normalizeName(v.name()).equals(normalizeName(name))) {
                    existing = v;
                    break;
                }
            }

            String id = existing == null ? UUID.randomUUID().toString() : existing.id();
            TaskViewStore.TaskViewData next = snapshotViewData(id, name);
            if (existing != null) saved.remove(existing);
            saved.add(next);
            viewStore.save(userId, saved);
            reloadViews(id);
        });
    }

    private void deleteCurrentView() {
        TaskView view = viewBox.getValue();
        if (view == null || view.builtIn()) return;

        String userId = currentUserId();
        List<TaskViewStore.TaskViewData> saved = new ArrayList<>(viewStore.load(userId));
        saved.removeIf(v -> v != null && view.id().equals(v.id()));
        viewStore.save(userId, saved);
        reloadViews("all");
    }

    private TaskViewStore.TaskViewData snapshotViewData(String id, String name) {
        TaskStatus status = valueOf(statusFilter);
        Priority priority = valueOf(priorityFilter);
        DueRange due = valueOf(dueFilter);
        if (due == null) due = DueRange.ANY;

        return new TaskViewStore.TaskViewData(
                id,
                name,
                searchField.getText(),
                showDone.isSelected(),
                status == null ? null : status.name(),
                priority == null ? null : priority.name(),
                due.name(),
                assignedToMe.isSelected() && canSeeAll.get()
        );
    }

    private String currentUserId() {
        if (appState.getSession() == null) return "local";
        String id = appState.getSession().id();
        return id == null || id.isBlank() ? "local" : id.trim();
    }

    private static <T> FilterOption<T> opt(String label, T value) {
        return new FilterOption<>(label, value);
    }

    private static <T> T valueOf(ComboBox<FilterOption<T>> box) {
        FilterOption<T> opt = box.getValue();
        return opt == null ? null : opt.value();
    }

    private static <T> void selectOption(ComboBox<FilterOption<T>> box, T value) {
        if (box == null) return;
        for (FilterOption<T> opt : box.getItems()) {
            if (opt == null) continue;
            if (value == null && opt.value() == null) {
                box.setValue(opt);
                return;
            }
            if (value != null && value.equals(opt.value())) {
                box.setValue(opt);
                return;
            }
        }
    }

    private static <E extends Enum<E>> E safeEnum(Class<E> type, String name, E fallback) {
        if (name == null || name.isBlank()) return fallback;
        try { return Enum.valueOf(type, name); } catch (Exception e) { com.projectpilot.util.AppLog.warn("tasks", "safeEnum parse failed for " + name + ": " + (e == null ? "" : e.getMessage())); return fallback; }
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

    private void alertInfo(String header, String text) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("ProjectPilot");
        a.setHeaderText(header);
        a.setContentText(text);
        a.showAndWait();
    }

    private record FilterOption<T>(String label, T value) {
        @Override public String toString() { return label; }
    }

    private enum DueRange { ANY, OVERDUE, TODAY, WEEK, MONTH }

    private record TaskView(String id, String name, TaskViewStore.TaskViewData data, boolean builtIn) {}
}
