package com.projectpilot.ui.dialogs;

import com.projectpilot.model.Member;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.Priority;
import com.projectpilot.model.enums.TaskStatus;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.Node;

import java.time.LocalDate;

public class CreateTaskDialog extends Dialog<Task> {

    public CreateTaskDialog(java.util.List<Member> members) {
        setTitle("New Task");
        setHeaderText("Create a new task");

        ButtonType createBtn = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(createBtn, ButtonType.CANCEL);

        TextField title = new TextField();
        title.setPromptText("Task title");

        TextArea desc = new TextArea();
        desc.setPromptText("Description (optional)");
        desc.setPrefRowCount(3);

        ComboBox<TaskStatus> status = new ComboBox<>();
        status.getItems().addAll(TaskStatus.values());
        status.setValue(TaskStatus.TODO);

        ComboBox<Priority> priority = new ComboBox<>();
        priority.getItems().addAll(Priority.values());
        priority.setValue(Priority.MEDIUM);

        DatePicker due = new DatePicker(LocalDate.now().plusDays(7));

        ComboBox<Member> assignee = new ComboBox<>();
        assignee.setPromptText("Unassigned");
        assignee.getItems().setAll(members);
        assignee.setPrefWidth(220);

        assignee.setCellFactory(cb -> new ListCell<>() {
            @Override protected void updateItem(Member item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getName());
            }
        });
        assignee.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Member item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Unassigned" : item.getName());
            }
        });



        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));

        grid.add(new Label("Title"), 0, 0);
        grid.add(title, 1, 0);

        grid.add(new Label("Description"), 0, 1);
        grid.add(desc, 1, 1);

        grid.add(new Label("Status"), 0, 2);
        grid.add(status, 1, 2);

        grid.add(new Label("Priority"), 0, 3);
        grid.add(priority, 1, 3);

        grid.add(new Label("Due date"), 0, 4);
        grid.add(due, 1, 4);

        grid.add(new Label("Assignee"), 0, 5);
        grid.add(assignee, 1, 5);

        getDialogPane().setContent(grid);

        Node ok = getDialogPane().lookupButton(createBtn);
        ok.setDisable(true);
        title.textProperty().addListener((obs, o, n) -> ok.setDisable(n == null || n.trim().isEmpty()));

        setResultConverter(bt -> {
            if (bt != createBtn) return null;

            Task t = new Task(title.getText().trim());
            t.setDescription(desc.getText() == null ? "" : desc.getText().trim());
            t.setStatus(status.getValue());
            t.setPriority(priority.getValue());
            t.setDueDate(due.getValue());
            t.setAssignee(assignee.getValue());
            return t;
        });
    }
}
