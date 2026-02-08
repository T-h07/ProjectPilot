package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.Project;
import com.projectpilot.model.ResourceItem;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.ProjectRole;
import com.projectpilot.model.enums.ResourceType;
import com.projectpilot.security.AccessPolicy;
import com.projectpilot.ui.components.EmptyStatePane;
import com.projectpilot.ui.dialogs.DialogTheme;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.time.format.DateTimeFormatter;

public class ResourcesPage extends BorderPane {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MMM d, HH:mm");

    private final InMemoryStore store;
    private final AppState appState;
    private final AccessPolicy policy = new AccessPolicy();

    private final Label subtitle = new Label();
    private final TabPane tabs = new TabPane();
    private final ListView<ResourceItem> projectList = new ListView<>();
    private final ListView<ResourceItem> taskList = new ListView<>();
    private FilteredList<ResourceItem> projectResources;
    private FilteredList<ResourceItem> taskResources;

    private final ComboBox<Task> taskPicker = new ComboBox<>();
    private final Button addProjectLink = new Button("Add Link");
    private final Button addProjectFile = new Button("Add File");
    private final Button addTaskLink = new Button("Add Link");
    private final Button addTaskFile = new Button("Add File");

    private final EmptyStatePane emptyState = new EmptyStatePane(
            "No project selected",
            "Pick a project to manage files and links for tasks and specs."
    );

    private Project currentProject;
    private boolean syncingTaskSelection = false;

    public ResourcesPage(InMemoryStore store, AppState appState) {
        this.store = store;
        this.appState = appState;

        setPadding(new Insets(16));

        Label title = new Label("Files & Links");
        title.getStyleClass().add("page-title");

        subtitle.getStyleClass().add("muted");

        VBox header = new VBox(6, title, subtitle);
        setTop(header);

        projectList.setPlaceholder(placeholder("No files or links yet."));
        taskList.setPlaceholder(placeholder("Select a task to see its files."));
        projectList.setCellFactory(lv -> new ResourceCell());
        taskList.setCellFactory(lv -> new ResourceCell());

        addProjectLink.getStyleClass().add("primary");
        addProjectFile.getStyleClass().add("secondary");
        addTaskLink.getStyleClass().add("primary");
        addTaskFile.getStyleClass().add("secondary");

        addProjectLink.setOnAction(e -> showAddDialog(ResourceType.LINK, false));
        addProjectFile.setOnAction(e -> showAddDialog(ResourceType.FILE, false));
        addTaskLink.setOnAction(e -> showAddDialog(ResourceType.LINK, true));
        addTaskFile.setOnAction(e -> showAddDialog(ResourceType.FILE, true));

        VBox projectTabContent = new VBox(10, toolbar(addProjectLink, addProjectFile), projectList);
        VBox.setVgrow(projectList, Priority.ALWAYS);
        projectTabContent.getStyleClass().add("card");
        projectTabContent.setPadding(new Insets(12));

        taskPicker.setPromptText("Select task");
        taskPicker.setCellFactory(lv -> new TaskCell());
        taskPicker.setButtonCell(new TaskCell());

        taskPicker.valueProperty().addListener((obs, o, n) -> {
            if (syncingTaskSelection) return;
            appState.setSelectedTask(n);
            refreshTaskFilter();
            refreshPermissions();
        });

        HBox taskToolbar = new HBox(8, taskPicker, addTaskLink, addTaskFile);
        taskToolbar.getStyleClass().add("resource-toolbar");
        taskToolbar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(taskPicker, Priority.ALWAYS);

        VBox taskTabContent = new VBox(10, taskToolbar, taskList);
        VBox.setVgrow(taskList, Priority.ALWAYS);
        taskTabContent.getStyleClass().add("card");
        taskTabContent.setPadding(new Insets(12));

        Tab projectTab = new Tab("Project", projectTabContent);
        Tab taskTab = new Tab("Task", taskTabContent);
        projectTab.setClosable(false);
        taskTab.setClosable(false);
        tabs.getTabs().setAll(projectTab, taskTab);

        StackPane content = new StackPane(tabs, emptyState);
        setCenter(content);

        appState.selectedProjectProperty().addListener((obs, o, n) -> refreshProject(n));
        appState.selectedTaskProperty().addListener((obs, o, n) -> syncTaskSelection(n));
        appState.sessionProperty().addListener((obs, o, n) -> refreshPermissions());
        appState.currentProjectRoleProperty().addListener((obs, o, n) -> refreshPermissions());

        refreshProject(appState.getSelectedProject());
        refreshPermissions();
    }

    private void refreshProject(Project project) {
        currentProject = project;
        subtitle.setText(project == null ? "No project selected" : project.getName());

        if (project == null) {
            projectResources = new FilteredList<>(FXCollections.observableArrayList());
            taskResources = new FilteredList<>(FXCollections.observableArrayList());
            projectList.setItems(projectResources);
            taskList.setItems(taskResources);
            taskPicker.setItems(FXCollections.observableArrayList());
            emptyState.setVisible(true);
            emptyState.setManaged(true);
            tabs.setVisible(false);
            tabs.setManaged(false);
            refreshPermissions();
            return;
        }

        projectResources = new FilteredList<>(project.getResources(), r -> isProjectResource(r));
        taskResources = new FilteredList<>(project.getResources(), r -> isTaskResource(r, appState.getSelectedTask()));

        projectList.setItems(projectResources);
        taskList.setItems(taskResources);
        taskPicker.setItems(project.getTasks());

        emptyState.setVisible(false);
        emptyState.setManaged(false);
        tabs.setVisible(true);
        tabs.setManaged(true);

        syncTaskSelection(appState.getSelectedTask());
        refreshPermissions();
    }

    private void syncTaskSelection(Task task) {
        if (currentProject == null) return;
        syncingTaskSelection = true;
        try {
            if (task == null) {
                taskPicker.getSelectionModel().clearSelection();
            } else if (currentProject.getTasks().contains(task)) {
                taskPicker.getSelectionModel().select(task);
            }
            refreshTaskFilter();
        } finally {
            syncingTaskSelection = false;
        }
    }

    private void refreshTaskFilter() {
        if (taskResources == null) return;
        Task selected = taskPicker.getValue();
        taskResources.setPredicate(r -> isTaskResource(r, selected));
        String message = selected == null ? "Select a task to see its files." : "No files or links yet.";
        taskList.setPlaceholder(placeholder(message));
    }

    private void refreshPermissions() {
        boolean projectAllowed = canEditProjectResources();
        addProjectLink.setDisable(!projectAllowed);
        addProjectFile.setDisable(!projectAllowed);

        Task task = taskPicker.getValue();
        boolean taskAllowed = task != null && canEditTaskResources(task);
        addTaskLink.setDisable(!taskAllowed);
        addTaskFile.setDisable(!taskAllowed);
    }

    private boolean canEditProjectResources() {
        if (policy.isAdmin(appState)) return true;
        ProjectRole role = appState.getCurrentProjectRole();
        return role == ProjectRole.LEADER || role == ProjectRole.ADMIN;
    }

    private boolean canEditTaskResources(Task task) {
        if (policy.isAdmin(appState)) return true;
        ProjectRole role = appState.getCurrentProjectRole();
        if (role == ProjectRole.LEADER || role == ProjectRole.ADMIN) return true;
        return policy.isAssignedToMe(appState, task);
    }

    private void showAddDialog(ResourceType type, boolean forTask) {
        if (currentProject == null) {
            alertInfo("No project selected", "Select a project first.");
            return;
        }

        Task task = forTask ? taskPicker.getValue() : null;
        if (forTask && task == null) {
            alertInfo("No task selected", "Select a task first.");
            return;
        }

        Dialog<ResourceItem> dlg = new Dialog<>();
        DialogTheme.apply(dlg);
        dlg.setTitle(type == ResourceType.FILE ? "Add File" : "Add Link");
        dlg.setHeaderText(null);
        dlg.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

        TextField titleField = new TextField();
        TextField targetField = new TextField();
        TextArea notesField = new TextArea();
        notesField.setPrefRowCount(3);

        if (type == ResourceType.FILE) {
            targetField.setPromptText("Choose a file...");
        } else {
            targetField.setPromptText("https://");
        }

        Button browse = new Button("Browse");
        browse.setDisable(type != ResourceType.FILE);
        browse.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            File file = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
            if (file != null) {
                targetField.setText(file.getAbsolutePath());
                if (titleField.getText().isBlank()) {
                    titleField.setText(file.getName());
                }
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.addRow(0, new Label("Title"), titleField);

        HBox targetRow = new HBox(8, targetField, browse);
        HBox.setHgrow(targetField, Priority.ALWAYS);
        grid.addRow(1, new Label(type == ResourceType.FILE ? "File" : "URL"), targetRow);

        grid.addRow(2, new Label("Notes"), notesField);
        dlg.getDialogPane().setContent(grid);

        Button okBtn = (Button) dlg.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.disableProperty().bind(Bindings.createBooleanBinding(
                () -> targetField.getText() == null || targetField.getText().trim().isEmpty(),
                targetField.textProperty()
        ));

        dlg.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            String title = safe(titleField.getText());
            String target = safe(targetField.getText());
            if (title.isBlank()) {
                title = target;
            }

            ResourceItem item = new ResourceItem(currentProject.getId());
            item.setTaskId(task == null ? null : task.getId());
            item.setType(type);
            item.setTitle(title);
            item.setTarget(target);
            item.setNotes(safe(notesField.getText()));
            item.setAddedBy(displayName());
            item.touchUpdatedAt();
            return item;
        });

        dlg.showAndWait().ifPresent(item -> {
            ResourceItem existing = store.addResource(currentProject, item);
            if (existing != item) {
                alertInfo("Duplicate resource", "A resource with that title already exists.");
                return;
            }
            selectResource(item);
        });
    }

    private void selectResource(ResourceItem item) {
        if (item == null) return;
        if (isProjectResource(item)) {
            projectList.getSelectionModel().select(item);
        } else {
            taskList.getSelectionModel().select(item);
        }
    }

    private boolean isProjectResource(ResourceItem r) {
        if (r == null) return false;
        String taskId = safe(r.getTaskId());
        return taskId.isBlank();
    }

    private boolean isTaskResource(ResourceItem r, Task task) {
        if (r == null || task == null) return false;
        String taskId = safe(r.getTaskId());
        return !taskId.isBlank() && taskId.equals(task.getId());
    }

    private void openResource(ResourceItem r) {
        if (r == null) return;
        String target = safe(r.getTarget());
        if (target.isBlank()) return;

        try {
            if (!Desktop.isDesktopSupported()) {
                alertInfo("Open failed", "Desktop integration is not available.");
                return;
            }
            Desktop desktop = Desktop.getDesktop();
            if (r.getType() == ResourceType.FILE) {
                desktop.open(new File(target));
            } else {
                desktop.browse(new URI(target));
            }
        } catch (Exception e) {
            alertInfo("Open failed", e.getMessage());
        }
    }

    private void removeResource(ResourceItem r) {
        if (currentProject == null || r == null) return;
        if (!canDeleteResource(r)) return;
        store.removeResource(currentProject, r);
    }

    private boolean canDeleteResource(ResourceItem r) {
        if (r == null) return false;
        if (policy.isAdmin(appState)) return true;
        ProjectRole role = appState.getCurrentProjectRole();
        if (role == ProjectRole.LEADER || role == ProjectRole.ADMIN) return true;
        Task t = findTaskById(currentProject, r.getTaskId());
        return t != null && policy.isAssignedToMe(appState, t);
    }

    private Task findTaskById(Project project, String id) {
        if (project == null || id == null) return null;
        for (Task t : project.getTasks()) {
            if (t != null && id.equals(t.getId())) return t;
        }
        return null;
    }

    private static HBox toolbar(Button... buttons) {
        HBox box = new HBox(8, buttons);
        box.getStyleClass().add("resource-toolbar");
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static Label placeholder(String text) {
        Label label = new Label(text == null ? "" : text);
        label.getStyleClass().add("muted");
        return label;
    }

    private String displayName() {
        if (appState == null || appState.getSession() == null) return "";
        String dn = appState.getSession().displayName();
        if (dn != null && !dn.isBlank()) return dn.trim();
        String un = appState.getSession().username();
        return un == null ? "" : un.trim();
    }

    private void alertInfo(String header, String text) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("ProjectPilot");
        a.setHeaderText(header);
        a.setContentText(text == null ? "" : text);
        a.showAndWait();
    }

    private static String safe(String v) {
        return v == null ? "" : v.trim();
    }

    private final class ResourceCell extends ListCell<ResourceItem> {
        @Override
        protected void updateItem(ResourceItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }

            Label title = new Label(item.getTitle());
            title.getStyleClass().add("resource-title");

            String metaText = item.getType() == ResourceType.FILE ? "File" : "Link";
            String target = safe(item.getTarget());
            if (!target.isBlank()) metaText += " | " + target;
            if (item.getUpdatedAt() != null) {
                metaText += " | " + item.getUpdatedAt().format(TIME_FMT);
            }

            Label meta = new Label(metaText);
            meta.getStyleClass().add("resource-meta");

            VBox info = new VBox(4, title, meta);

            Button openBtn = new Button("Open");
            openBtn.getStyleClass().addAll("subtle", "sm");
            openBtn.setDisable(target.isBlank());
            openBtn.setOnAction(e -> openResource(item));

            Button removeBtn = new Button("Remove");
            removeBtn.getStyleClass().addAll("danger-outline", "sm");
            removeBtn.setDisable(!canDeleteResource(item));
            removeBtn.setOnAction(e -> removeResource(item));

            HBox actions = new HBox(6, openBtn, removeBtn);
            actions.getStyleClass().add("resource-actions");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox row = new HBox(12, info, spacer, actions);
            row.getStyleClass().add("resource-row");
            row.setAlignment(Pos.CENTER_LEFT);
            setGraphic(row);
        }
    }

    private static final class TaskCell extends ListCell<Task> {
        @Override
        protected void updateItem(Task item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item.getTitle());
        }
    }
}

