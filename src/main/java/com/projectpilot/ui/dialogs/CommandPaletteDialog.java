package com.projectpilot.ui.dialogs;

import com.projectpilot.core.AppState;
import com.projectpilot.core.PageId;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.security.AccessPolicy;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class CommandPaletteDialog extends Dialog<Void> {

    private static CommandPaletteDialog activeDialog;

    private final ObservableList<CommandItem> items = FXCollections.observableArrayList();
    private final FilteredList<CommandItem> filtered = new FilteredList<>(items, i -> true);
    private final ListView<CommandItem> list = new ListView<>(filtered);
    private final TextField search = new TextField();

    private final InMemoryStore store;
    private final AppState appState;
    private final Consumer<PageId> onNavigate;
    private final AccessPolicy policy = new AccessPolicy();

    public CommandPaletteDialog(InMemoryStore store, AppState appState, Consumer<PageId> onNavigate) {
        DialogTheme.apply(this);
        this.store = store;
        this.appState = appState;
        this.onNavigate = onNavigate;

        setTitle("Command Palette");
        setHeaderText(null);
        getDialogPane().getStyleClass().add("command-palette");
        getDialogPane().getButtonTypes().clear();
        setOnCloseRequest(e -> forceClose());

        items.setAll(buildCommands());

        search.setPromptText("Type a command...");
        search.getStyleClass().add("command-search");
        search.textProperty().addListener((obs, o, n) -> applyFilter(n));

        list.getStyleClass().add("command-list");
        list.setItems(filtered);
        list.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(CommandItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Label title = new Label(item.label());
                title.getStyleClass().add("command-title");
                Label hint = new Label(item.hint());
                hint.getStyleClass().add("command-hint");
                VBox box = new VBox(2, title, hint);
                box.getStyleClass().add("command-row");
                setGraphic(box);
            }
        });

        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) runSelected();
        });
        list.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) runSelected();
        });

        Button closeButton = new Button("×");
        closeButton.getStyleClass().add("command-close");
        closeButton.setFocusTraversable(false);
        closeButton.setOnAction(e -> forceClose());

        HBox header = new HBox(8, search, closeButton);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(search, Priority.ALWAYS);

        VBox content = new VBox(10, header, list);
        content.setPadding(new Insets(12));
        getDialogPane().setContent(content);

        getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE || (e.isControlDown() && e.getCode() == KeyCode.K)) {
                forceClose();
                e.consume();
            }
        });

        setOnShown(e -> Platform.runLater(() -> {
            search.requestFocus();
            if (!filtered.isEmpty()) list.getSelectionModel().select(0);
        }));
    }

    public static void show(Window owner, InMemoryStore store, AppState appState, Consumer<PageId> onNavigate) {
        if (activeDialog != null) {
            activeDialog.forceClose();
            activeDialog = null;
        }
        CommandPaletteDialog dialog = new CommandPaletteDialog(store, appState, onNavigate);
        if (owner != null) dialog.initOwner(owner);
        dialog.initModality(Modality.NONE);
        dialog.setOnHidden(e -> activeDialog = null);
        activeDialog = dialog;
        dialog.show();
    }

    private void applyFilter(String queryRaw) {
        String query = queryRaw == null ? "" : queryRaw.trim().toLowerCase(Locale.ROOT);
        filtered.setPredicate(item -> {
            if (item == null) return false;
            if (query.isEmpty()) return true;
            String label = item.label().toLowerCase(Locale.ROOT);
            String hint = item.hint().toLowerCase(Locale.ROOT);
            return label.contains(query) || hint.contains(query);
        });
        if (!filtered.isEmpty()) list.getSelectionModel().select(0);
    }

    private void runSelected() {
        CommandItem item = list.getSelectionModel().getSelectedItem();
        if (item == null) return;
        forceClose();
        item.action().run();
    }

    private void forceClose() {
        if (getDialogPane() != null && getDialogPane().getScene() != null) {
            Window w = getDialogPane().getScene().getWindow();
            if (w != null) {
                w.hide();
            }
        }
        close();
    }

    private List<CommandItem> buildCommands() {
        List<CommandItem> out = new ArrayList<>();

        addNav(out, "Dashboard", PageId.DASHBOARD);
        addNav(out, "Activity", PageId.ACTIVITY);
        addNav(out, "Projects", PageId.PROJECTS);
        addNav(out, "Project Overview", PageId.PROJECT_OVERVIEW);
        addNav(out, "Tasks", PageId.TASKS);
        addNav(out, "Gantt", PageId.GANTT);
        addNav(out, "Calendar", PageId.CALENDAR);
        addNav(out, "Files & Links", PageId.RESOURCES);
        addNav(out, "Notes", PageId.NOTES);
        addNav(out, "Team", PageId.TEAM);
        addNav(out, "Messages", PageId.MESSAGES);
        addNav(out, "History", PageId.HISTORY);
        addNav(out, "Export", PageId.EXPORT_REPORT);
        addNav(out, "Admin", PageId.ADMIN);

        if (policy.canCreateTasks(appState)) {
            out.add(new CommandItem("New Task", "Action", this::createTask));
        }

        if (policy.canCreateProject(appState)) {
            out.add(new CommandItem("New Project", "Action", this::createProject));
        }

        out.add(new CommandItem("Diagnostics", "Tools", LogViewerDialog::show));

        return out;
    }

    private void addNav(List<CommandItem> out, String label, PageId id) {
        if (!policy.canAccessPage(appState, id)) return;
        out.add(new CommandItem("Go to " + label, "Navigation", () -> onNavigate.accept(id)));
    }

    private void createTask() {
        Project p = appState.getSelectedProject();
        if (p == null) {
            alertInfo("No project selected", "Select a project first, then create a task.");
            return;
        }
        if (p.getMembers() == null || p.getMembers().isEmpty()) {
            alertInfo("No project members yet", "Add at least one project member before creating tasks.");
            return;
        }

        CreateTaskDialog d = new CreateTaskDialog(p, appState);
        d.showAndWait().ifPresent(t -> {
            if (isDuplicateTaskTitle(p, t.getTitle())) {
                alertInfo("Duplicate task", "A task with that title already exists in this project.");
                return;
            }
            store.addTask(p, t);
            onNavigate.accept(PageId.TASKS);
        });
    }

    private void createProject() {
        CreateProjectDialog d = new CreateProjectDialog();
        d.showAndWait().ifPresent(p -> {
            store.createProject(p);
            onNavigate.accept(PageId.PROJECTS);
        });
    }

    private boolean isDuplicateTaskTitle(Project p, String title) {
        if (p == null) return false;
        String n = normalizeName(title);
        if (n.isBlank()) return false;
        return p.getTasks().stream()
                .anyMatch(t -> t != null && normalizeName(t.getTitle()).equals(n));
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private void alertInfo(String header, String text) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, text, ButtonType.OK);
        a.setTitle("ProjectPilot");
        a.setHeaderText(header);
        a.showAndWait();
    }

    private record CommandItem(String label, String hint, Runnable action) {}
}
