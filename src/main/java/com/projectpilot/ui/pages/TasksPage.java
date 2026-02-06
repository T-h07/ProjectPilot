package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.Member;
import com.projectpilot.model.Phase;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.Priority;
import com.projectpilot.model.enums.TaskStatus;
import com.projectpilot.security.AccessPolicy;
import com.projectpilot.ui.components.ProjectPicker;
import com.projectpilot.ui.dialogs.CreateTaskDialog;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

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

    private Task bound;

    private final ObjectProperty<Task> selectedTask = new SimpleObjectProperty<>();

    private final TextField titleField = new TextField();
    private final TextArea descField = new TextArea();
    private final ComboBox<TaskStatus> statusBox = new ComboBox<>();
    private final ComboBox<Priority> priorityBox = new ComboBox<>();
    private final DatePicker duePicker = new DatePicker();

    private final ComboBox<Phase> phaseBox = new ComboBox<>();
    private final ComboBox<Member> assigneeBox = new ComboBox<>();

    private Project boundProject;

    private final Set<Task> hooked = new HashSet<>();

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
        rebuildTaskSource(boundProject);
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
        searchField.textProperty().addListener((obs, ov, nv) -> applyFilterPreserveSelection());

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);


        showDone.setSelected(false);
        showDone.setStyle("-fx-text-fill: white;");
        showDone.selectedProperty().addListener((obs, ov, nv) -> applyFilterPreserveSelection());

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

            CreateTaskDialog d = new CreateTaskDialog(p);
            d.showAndWait().ifPresent(t -> {
                store.addTask(p, t);
                hookTaskOnce(t);
                rebuildTaskSource(p);
                applyFilterPreserveSelection();
                tasksList.getSelectionModel().select(t);
            });
        });

        HBox toolbar = new HBox(
                10,
                new Label("Project:"),
                taskProjectPicker,
                searchField,
                spacer,
                showDone,
                newTask
        );

        tasksList.setPrefWidth(420);
        tasksList.setItems(filteredTasks);
        tasksList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            selectedTask.set(newV);
            bindTask(newV);
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
                try { return LocalDate.parse(v, fmt); } catch (Exception ignored) { return null; }
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

        VBox editor = new VBox(12, editorTitle, form);
        editor.getStyleClass().add("card");

        HBox body = new HBox(14, tasksList, editor);
        HBox.setHgrow(editor, javafx.scene.layout.Priority.ALWAYS);

        getChildren().addAll(header, toolbar, body);

        refresh(appState.getSelectedProject());
        appState.selectedProjectProperty().addListener((obs, oldV, newV) -> refresh(newV));

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
        duePicker.disableProperty().bind(canEditOwnFields.not());
    }

    private void refresh(Project p) {
        if (boundProject != null) {
            try { boundProject.getTasks().removeListener(projectTasksListener); } catch (Exception ignored) {}
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
                if (!canSeeAll.get()) rebuildTaskSource(boundProject);
            });
        } catch (Exception ignored) {}

        // assignee changes affect “my tasks only”
        try {
            t.assigneeProperty().addListener((obs, ov, nv) -> {
                if (!canSeeAll.get()) rebuildTaskSource(boundProject);
            });
        } catch (Exception ignored) {}
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

        filteredTasks.setPredicate(t -> {
            if (t == null) return false;

            if (!includeDone && t.getStatus() == TaskStatus.DONE) return false;

            if (query.isEmpty()) return true;

            String title = (t.getTitle() == null) ? "" : t.getTitle().toLowerCase();
            String desc = (t.getDescription() == null) ? "" : t.getDescription().toLowerCase();

            String phase = "";
            try { phase = (t.getPhase() == null || t.getPhase().getName() == null) ? "" : t.getPhase().getName().toLowerCase(); }
            catch (Exception ignored) {}

            String assignee = "";
            try { assignee = (t.getAssignee() == null || t.getAssignee().getName() == null) ? "" : t.getAssignee().getName().toLowerCase(); }
            catch (Exception ignored) {}

            return title.contains(query)
                    || desc.contains(query)
                    || phase.contains(query)
                    || assignee.contains(query);
        });
    }

    private void bindTask(Task t) {
        if (bound != null) {
            titleField.textProperty().unbindBidirectional(bound.titleProperty());
            descField.textProperty().unbindBidirectional(bound.descriptionProperty());
            statusBox.valueProperty().unbindBidirectional(bound.statusProperty());
            priorityBox.valueProperty().unbindBidirectional(bound.priorityProperty());
            duePicker.valueProperty().unbindBidirectional(bound.dueDateProperty());

            try { phaseBox.valueProperty().unbindBidirectional(bound.phaseProperty()); } catch (Exception ignored) {}
            try { assigneeBox.valueProperty().unbindBidirectional(bound.assigneeProperty()); } catch (Exception ignored) {}
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
            return;
        }

        titleField.textProperty().bindBidirectional(t.titleProperty());
        descField.textProperty().bindBidirectional(t.descriptionProperty());
        statusBox.valueProperty().bindBidirectional(t.statusProperty());
        priorityBox.valueProperty().bindBidirectional(t.priorityProperty());
        duePicker.valueProperty().bindBidirectional(t.dueDateProperty());

        try { phaseBox.valueProperty().bindBidirectional(t.phaseProperty()); } catch (Exception ignored) {}
        try { assigneeBox.valueProperty().bindBidirectional(t.assigneeProperty()); } catch (Exception ignored) {}

        statusBox.setValue(t.getStatus());
        priorityBox.setValue(t.getPriority());
        duePicker.setValue(t.getDueDate());
        try { phaseBox.setValue(t.getPhase()); } catch (Exception ignored) {}
        try { assigneeBox.setValue(t.getAssignee()); } catch (Exception ignored) {}
    }
}
