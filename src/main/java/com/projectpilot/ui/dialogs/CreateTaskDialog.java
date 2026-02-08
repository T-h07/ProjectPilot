package com.projectpilot.ui.dialogs;

import com.projectpilot.core.AppState;
import com.projectpilot.model.ChecklistItem;
import com.projectpilot.model.Member;
import com.projectpilot.model.Phase;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.Priority;
import com.projectpilot.model.enums.TaskStatus;
import com.projectpilot.ui.components.ChecklistEditor;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.projectpilot.ui.dialogs.DialogTheme;
import com.projectpilot.util.TaskTemplateStore;

public class CreateTaskDialog extends Dialog<Task> {

    public CreateTaskDialog(Project project, AppState appState) {
        DialogTheme.apply(this);

        setTitle("New Task");
        setHeaderText("Create a new task");

        ButtonType createBtn = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(createBtn, ButtonType.CANCEL);

        TaskTemplateStore templateStore = new TaskTemplateStore();
        String userId = appState == null || appState.getSession() == null ? "local" : appState.getSession().id();
        List<TaskTemplateStore.TaskTemplateData> templates = new ArrayList<>(templateStore.load(userId));

        ComboBox<TaskTemplateStore.TaskTemplateData> templateBox = new ComboBox<>();
        templateBox.setPromptText("No template");
        templateBox.getItems().setAll(templates);
        templateBox.setCellFactory(cb -> new ListCell<>() {
            @Override protected void updateItem(TaskTemplateStore.TaskTemplateData item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.name());
            }
        });
        templateBox.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(TaskTemplateStore.TaskTemplateData item, boolean empty) {
                super.updateItem(item, empty);
                setText(item == null ? "No template" : item.name());
            }
        });

        TextField title = new TextField();
        title.setPromptText("Task title");

        TextArea desc = new TextArea();
        desc.setPromptText("Description (optional)");
        desc.setPrefRowCount(3);

        // ✅ WORD WRAP (fix horizontal overflow)
        desc.setWrapText(true);

        ComboBox<TaskStatus> status = new ComboBox<>();
        status.getItems().addAll(TaskStatus.values());
        status.setValue(TaskStatus.TODO);

        ComboBox<Priority> priority = new ComboBox<>();
        priority.getItems().addAll(Priority.values());
        priority.setValue(Priority.MEDIUM);

        DatePicker due = new DatePicker(LocalDate.now().plusDays(7));

        ObservableList<ChecklistItem> checklistItems = FXCollections.observableArrayList();
        ChecklistEditor checklistEditor = new ChecklistEditor();
        checklistEditor.setItems(checklistItems);

        CheckBox saveTemplate = new CheckBox("Save as template");
        TextField templateName = new TextField();
        templateName.setPromptText("Template name");
        templateName.visibleProperty().bind(saveTemplate.selectedProperty());
        templateName.managedProperty().bind(saveTemplate.selectedProperty());

        ComboBox<Phase> phase = new ComboBox<>();
        phase.setPromptText("Select phase");
        phase.getItems().setAll(project.getPhases());
        phase.setPrefWidth(220);

        ComboBox<Member> assignee = new ComboBox<>();
        assignee.setPromptText("Unassigned");
        assignee.getItems().setAll(project.getMembers());
        assignee.setPrefWidth(220);

        // nice labels for Member/Phase
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

        phase.setCellFactory(cb -> new ListCell<>() {
            @Override protected void updateItem(Phase item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getName());
            }
        });
        phase.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Phase item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Select phase" : item.getName());
            }
        });

        templateBox.valueProperty().addListener((obs, ov, nv) -> {
            if (nv == null) return;
            title.setText(nv.title() == null ? "" : nv.title());
            desc.setText(nv.description() == null ? "" : nv.description());
            status.setValue(nv.status() == null ? TaskStatus.TODO : nv.status());
            priority.setValue(nv.priority() == null ? Priority.MEDIUM : nv.priority());
            if (nv.dueOffsetDays() != null) {
                due.setValue(LocalDate.now().plusDays(nv.dueOffsetDays()));
            }
            checklistItems.setAll(fromTemplateChecklist(nv));
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));

        int r = 0;
        grid.add(new Label("Template"), 0, r);     grid.add(templateBox, 1, r++);
        grid.add(new Label("Title"), 0, r);        grid.add(title, 1, r++);
        grid.add(new Label("Description"), 0, r);  grid.add(desc, 1, r++);
        grid.add(new Label("Status"), 0, r);       grid.add(status, 1, r++);
        grid.add(new Label("Priority"), 0, r);     grid.add(priority, 1, r++);
        grid.add(new Label("Due date"), 0, r);     grid.add(due, 1, r++);
        grid.add(new Label("Phase"), 0, r);        grid.add(phase, 1, r++);
        grid.add(new Label("Assignee"), 0, r);     grid.add(assignee, 1, r++);
        grid.add(new Label("Checklist"), 0, r);    grid.add(checklistEditor, 1, r++);
        grid.add(new Label("Save template"), 0, r); grid.add(new HBox(8, saveTemplate, templateName), 1, r++);

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
            t.setPhase(phase.getValue());         // ✅ phase stored on task
            t.setAssignee(assignee.getValue());
            if (!checklistItems.isEmpty()) {
                List<ChecklistItem> next = new ArrayList<>();
                for (ChecklistItem item : checklistItems) {
                    if (item == null) continue;
                    next.add(new ChecklistItem(null, item.getText(), item.isDone()));
                }
                t.setChecklist(next);
            }

            if (saveTemplate.isSelected()) {
                String name = templateName.getText() == null ? "" : templateName.getText().trim();
                if (!name.isEmpty()) {
                    TaskTemplateStore.TaskTemplateData data = buildTemplateData(
                            name,
                            title.getText(),
                            desc.getText(),
                            status.getValue(),
                            priority.getValue(),
                            due.getValue(),
                            checklistItems
                    );

                    TaskTemplateStore.TaskTemplateData existing = null;
                    for (TaskTemplateStore.TaskTemplateData tpl : templates) {
                        if (tpl != null && normalizeName(tpl.name()).equals(normalizeName(name))) {
                            existing = tpl;
                            break;
                        }
                    }
                    if (existing != null) templates.remove(existing);
                    templates.add(data);
                    templateStore.save(userId, templates);
                }
            }
            return t;
        });
    }

    private static List<ChecklistItem> fromTemplateChecklist(TaskTemplateStore.TaskTemplateData tpl) {
        if (tpl == null || tpl.checklist() == null) return List.of();
        List<ChecklistItem> out = new ArrayList<>();
        for (TaskTemplateStore.ChecklistItemData item : tpl.checklist()) {
            if (item == null) continue;
            out.add(new ChecklistItem(null, item.text(), item.done()));
        }
        return out;
    }

    private static TaskTemplateStore.TaskTemplateData buildTemplateData(
            String name,
            String title,
            String description,
            TaskStatus status,
            Priority priority,
            LocalDate due,
            ObservableList<ChecklistItem> checklistItems
    ) {
        Integer offset = null;
        if (due != null) {
            long delta = ChronoUnit.DAYS.between(LocalDate.now(), due);
            offset = (int) delta;
        }

        List<TaskTemplateStore.ChecklistItemData> checklist = new ArrayList<>();
        if (checklistItems != null) {
            for (ChecklistItem item : checklistItems) {
                if (item == null) continue;
                checklist.add(new TaskTemplateStore.ChecklistItemData(item.getText(), item.isDone()));
            }
        }

        return new TaskTemplateStore.TaskTemplateData(
                UUID.randomUUID().toString(),
                name == null ? "" : name.trim(),
                title == null ? "" : title.trim(),
                description == null ? "" : description.trim(),
                status == null ? TaskStatus.TODO : status,
                priority == null ? Priority.MEDIUM : priority,
                offset,
                checklist
        );
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }
}
