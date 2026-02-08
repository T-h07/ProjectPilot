package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.PersonalNote;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.security.AccessPolicy;
import com.projectpilot.ui.components.EmptyStatePane;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;

public class NotesPage extends BorderPane {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MMM d, HH:mm");

    private final InMemoryStore store;
    private final AppState appState;
    private final AccessPolicy policy = new AccessPolicy();

    private final ListView<PersonalNote> notesList = new ListView<>();
    private FilteredList<PersonalNote> filteredNotes;

    private final TextField titleField = new TextField();
    private final TextArea bodyField = new TextArea();
    private final ComboBox<Task> taskPicker = new ComboBox<>();
    private final Label updatedLabel = new Label("-");

    private final Button newBtn = new Button("New Note");
    private final Button deleteBtn = new Button("Delete");
    private final Button clearTaskBtn = new Button("Clear Task");

    private final EmptyStatePane emptyState = new EmptyStatePane(
            "No project selected",
            "Pick a project to see your personal notes."
    );

    private Project currentProject;
    private PersonalNote selectedNote;
    private boolean updatingFields = false;
    private final ListChangeListener<Task> taskListener = c -> refreshFilters();

    public NotesPage(InMemoryStore store, AppState appState) {
        this.store = store;
        this.appState = appState;

        setPadding(new Insets(16));

        Label title = new Label("Notes");
        title.getStyleClass().add("page-title");

        Label subtitle = new Label("Personal notes for your assigned tasks.");
        subtitle.getStyleClass().add("muted");

        VBox header = new VBox(6, title, subtitle);
        setTop(header);

        notesList.setPlaceholder(placeholder("No notes yet."));
        notesList.setCellFactory(lv -> new NoteCell());

        newBtn.getStyleClass().add("primary");
        deleteBtn.getStyleClass().add("danger-outline");
        clearTaskBtn.getStyleClass().add("subtle");

        newBtn.setOnAction(e -> createNote());
        deleteBtn.setOnAction(e -> deleteSelected());
        clearTaskBtn.setOnAction(e -> {
            taskPicker.getSelectionModel().clearSelection();
            if (selectedNote != null) {
                selectedNote.setTaskId(null);
                selectedNote.touchUpdatedAt();
            }
        });

        VBox left = new VBox(10, newBtn, notesList);
        left.getStyleClass().add("card");
        left.setPadding(new Insets(12));
        left.setPrefWidth(320);
        VBox.setVgrow(notesList, Priority.ALWAYS);

        Label formTitle = new Label("Note Details");
        formTitle.getStyleClass().add("panel-title");

        taskPicker.setPromptText("Project note");
        taskPicker.setCellFactory(lv -> new TaskCell());
        taskPicker.setButtonCell(new TaskCell());

        HBox taskRow = new HBox(8, taskPicker, clearTaskBtn);
        HBox.setHgrow(taskPicker, Priority.ALWAYS);

        updatedLabel.getStyleClass().add("muted");

        bodyField.setWrapText(true);
        bodyField.setPrefRowCount(10);

        VBox right = new VBox(10,
                formTitle,
                labeled("Title", titleField),
                labeled("Task", taskRow),
                labeled("Updated", updatedLabel),
                labeled("Notes", bodyField),
                deleteBtn
        );
        right.getStyleClass().add("card");
        right.setPadding(new Insets(12));
        VBox.setVgrow(bodyField, Priority.ALWAYS);

        HBox content = new HBox(14, left, right);
        HBox.setHgrow(right, Priority.ALWAYS);

        StackPane center = new StackPane(content, emptyState);
        setCenter(center);

        notesList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> selectNote(n));
        titleField.textProperty().addListener((obs, o, n) -> updateSelectedTitle(n));
        bodyField.textProperty().addListener((obs, o, n) -> updateSelectedBody(n));
        taskPicker.valueProperty().addListener((obs, o, n) -> updateSelectedTask(n));

        appState.selectedProjectProperty().addListener((obs, o, n) -> refreshProject(n));
        appState.sessionProperty().addListener((obs, o, n) -> refreshFilters());

        refreshProject(appState.getSelectedProject());
    }

    private void refreshProject(Project project) {
        if (currentProject != null) {
            currentProject.getTasks().removeListener(taskListener);
        }

        currentProject = project;

        if (project == null) {
            notesList.setItems(FXCollections.observableArrayList());
            taskPicker.setItems(FXCollections.observableArrayList());
            emptyState.setVisible(true);
            emptyState.setManaged(true);
            selectNote(null);
            return;
        }

        filteredNotes = new FilteredList<>(project.getNotes(), note -> false);
        notesList.setItems(filteredNotes);
        project.getTasks().addListener(taskListener);
        refreshFilters();

        emptyState.setVisible(false);
        emptyState.setManaged(false);
    }

    private void refreshFilters() {
        if (currentProject == null || filteredNotes == null) return;

        String me = currentUserId();
        filteredNotes.setPredicate(note -> {
            if (note == null) return false;
            if (me.isBlank() || !me.equals(safe(note.getOwnerId()))) return false;
            String taskId = safe(note.getTaskId());
            if (taskId.isBlank()) return true;
            Task t = findTaskById(currentProject, taskId);
            return t != null && policy.isAssignedToMe(appState, t);
        });

        taskPicker.setItems(currentProject.getTasks().filtered(t -> policy.isAssignedToMe(appState, t)));
        notesList.refresh();
    }

    private void selectNote(PersonalNote note) {
        selectedNote = note;
        updatingFields = true;
        try {
            if (note == null) {
                titleField.setText("");
                bodyField.setText("");
                taskPicker.getSelectionModel().clearSelection();
                updatedLabel.setText("-");
                deleteBtn.setDisable(true);
                return;
            }

            titleField.setText(safe(note.getTitle()));
            bodyField.setText(safe(note.getBody()));
            taskPicker.getSelectionModel().select(findTaskById(currentProject, note.getTaskId()));
            updatedLabel.setText(formatTime(note.getUpdatedAt()));
            deleteBtn.setDisable(false);
        } finally {
            updatingFields = false;
        }
    }

    private void updateSelectedTitle(String value) {
        if (updatingFields || selectedNote == null) return;
        selectedNote.setTitle(safe(value));
        selectedNote.touchUpdatedAt();
        updatedLabel.setText(formatTime(selectedNote.getUpdatedAt()));
        notesList.refresh();
    }

    private void updateSelectedBody(String value) {
        if (updatingFields || selectedNote == null) return;
        selectedNote.setBody(safe(value));
        selectedNote.touchUpdatedAt();
        updatedLabel.setText(formatTime(selectedNote.getUpdatedAt()));
    }

    private void updateSelectedTask(Task task) {
        if (updatingFields || selectedNote == null) return;
        selectedNote.setTaskId(task == null ? null : task.getId());
        selectedNote.touchUpdatedAt();
        updatedLabel.setText(formatTime(selectedNote.getUpdatedAt()));
    }

    private void createNote() {
        if (currentProject == null) {
            alertInfo("No project selected", "Select a project first.");
            return;
        }

        String me = currentUserId();
        if (me.isBlank()) {
            alertInfo("No user", "Log in before creating notes.");
            return;
        }

        PersonalNote note = new PersonalNote(currentProject.getId(), me);
        note.setTitle("New note");
        Task task = taskPicker.getValue();
        if (task != null) note.setTaskId(task.getId());

        PersonalNote existing = store.addNote(currentProject, note);
        if (existing != note) {
            alertInfo("Duplicate note", "A note with that title already exists.");
            return;
        }

        notesList.getSelectionModel().select(note);
    }

    private void deleteSelected() {
        if (currentProject == null || selectedNote == null) return;
        store.removeNote(currentProject, selectedNote);
        notesList.getSelectionModel().clearSelection();
    }

    private Task findTaskById(Project project, String id) {
        if (project == null || id == null) return null;
        for (Task t : project.getTasks()) {
            if (t != null && id.equals(t.getId())) return t;
        }
        return null;
    }

    private String currentUserId() {
        if (appState == null || appState.getSession() == null) return "";
        String id = appState.getSession().id();
        return id == null ? "" : id.trim();
    }

    private static String formatTime(java.time.LocalDateTime t) {
        if (t == null) return "-";
        return t.format(TIME_FMT);
    }

    private static String safe(String v) {
        return v == null ? "" : v.trim();
    }

    private void alertInfo(String header, String text) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("ProjectPilot");
        a.setHeaderText(header);
        a.setContentText(text == null ? "" : text);
        a.showAndWait();
    }

    private static VBox labeled(String label, Node node) {
        Label l = new Label(label);
        l.getStyleClass().add("label-strong");
        VBox box = new VBox(6, l, node);
        return box;
    }

    private static Label placeholder(String text) {
        Label label = new Label(text == null ? "" : text);
        label.getStyleClass().add("muted");
        return label;
    }

    private final class NoteCell extends ListCell<PersonalNote> {
        @Override
        protected void updateItem(PersonalNote item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }

            Label title = new Label(item.getTitle());
            title.getStyleClass().add("note-title");

            String snippet = safe(item.getBody());
            if (snippet.length() > 80) snippet = snippet.substring(0, 77) + "...";
            Label meta = new Label(snippet.isBlank() ? "No content yet." : snippet);
            meta.getStyleClass().add("note-meta");

            VBox box = new VBox(4, title, meta);
            box.getStyleClass().add("note-row");
            setGraphic(box);
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
