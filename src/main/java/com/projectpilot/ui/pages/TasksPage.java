package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.Priority;
import com.projectpilot.model.enums.TaskStatus;
import com.projectpilot.ui.dialogs.CreateTaskDialog;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import com.projectpilot.ui.components.ProjectPicker;
import javafx.scene.layout.Region;


public class TasksPage extends VBox {

    private final Label header = new Label("Tasks");
    private final ListView<Task> tasksList = new ListView<>();

    private Task bound;

    private final TextField titleField = new TextField();
    private final TextArea descField = new TextArea();
    private final ComboBox<TaskStatus> statusBox = new ComboBox<>();
    private final ComboBox<Priority> priorityBox = new ComboBox<>();
    private final DatePicker duePicker = new DatePicker();

    public TasksPage(InMemoryStore store, AppState appState) {
        setPadding(new Insets(16));
        setSpacing(12);

        header.getStyleClass().add("page-title");


        ProjectPicker taskProjectPicker = new ProjectPicker(store, appState);
        taskProjectPicker.setPrefWidth(320);

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);


        Button newTask = new Button("New Task");
        newTask.getStyleClass().add("primary");
        newTask.setOnAction(e -> {
            Project p = appState.getSelectedProject();
            if (p == null) return;

            if (p.getMembers().isEmpty()) {
                Alert a = new Alert(Alert.AlertType.INFORMATION);
                a.setTitle("No members yet");
                a.setHeaderText("Add at least one member first");
                a.setContentText("Go to Projects → Add Member, then create tasks and assign them.");
                a.showAndWait();
                return;
            }

            CreateTaskDialog d = new CreateTaskDialog(p.getMembers());
            d.showAndWait().ifPresent(t -> {
                store.addTask(p, t);
                tasksList.getSelectionModel().select(t);
            });
        });


        HBox toolbar = new HBox(10, new Label("Project:"), taskProjectPicker, spacer, newTask);

        tasksList.setPrefWidth(420);
        tasksList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> bindTask(newV));

        statusBox.getItems().setAll(TaskStatus.values());
        priorityBox.getItems().setAll(Priority.values());

        descField.setPrefRowCount(6);
        descField.getStyleClass().add("pp-textarea");




        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);

        form.add(new Label("Title"), 0, 0);
        form.add(titleField, 1, 0);

        form.add(new Label("Description"), 0, 1);
        form.add(descField, 1, 1);

        form.add(new Label("Status"), 0, 2);
        form.add(statusBox, 1, 2);

        form.add(new Label("Priority"), 0, 3);
        form.add(priorityBox, 1, 3);

        form.add(new Label("Due date"), 0, 4);
        form.add(duePicker, 1, 4);

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
    }

    private void refresh(Project p) {
        if (p == null) {
            header.setText("Tasks (no project selected)");
            tasksList.setItems(null);
            bindTask(null);
            return;
        }
        header.setText("Tasks — " + p.getName());
        tasksList.setItems(p.getTasks());
        if (!p.getTasks().isEmpty()) tasksList.getSelectionModel().select(0);
        else bindTask(null);
    }

    private void bindTask(Task t) {
        if (bound != null) {
            titleField.textProperty().unbindBidirectional(bound.titleProperty());
            descField.textProperty().unbindBidirectional(bound.descriptionProperty());
            statusBox.valueProperty().unbindBidirectional(bound.statusProperty());
            priorityBox.valueProperty().unbindBidirectional(bound.priorityProperty());
            duePicker.valueProperty().unbindBidirectional(bound.dueDateProperty());
        }

        bound = t;
        boolean disabled = (t == null);

        titleField.setDisable(disabled);
        descField.setDisable(disabled);
        statusBox.setDisable(disabled);
        priorityBox.setDisable(disabled);
        duePicker.setDisable(disabled);

        if (t == null) {
            titleField.setText("");
            descField.setText("");
            statusBox.setValue(null);
            priorityBox.setValue(null);
            duePicker.setValue(null);
            return;
        }

        titleField.textProperty().bindBidirectional(t.titleProperty());
        descField.textProperty().bindBidirectional(t.descriptionProperty());
        statusBox.valueProperty().bindBidirectional(t.statusProperty());
        priorityBox.valueProperty().bindBidirectional(t.priorityProperty());
        duePicker.valueProperty().bindBidirectional(t.dueDateProperty());

        statusBox.setValue(t.getStatus());
        priorityBox.setValue(t.getPriority());
        duePicker.setValue(t.getDueDate());
    }
}
