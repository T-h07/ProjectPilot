package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.Member;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.TaskStatus;
import javafx.beans.InvalidationListener;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import com.projectpilot.ui.dialogs.CreateProjectDialog;


import java.time.format.DateTimeFormatter;

public class ProjectsPage extends BorderPane {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final InMemoryStore store;
    private final AppState appState;

    // left
    private final Label header = new Label("Projects");
    private final Button newProjectBtn = new Button("New Project");
    private final ListView<Project> projectsList = new ListView<>();
    private final FilteredList<Project> activeProjects;

    // right
    private final Label detailsTitle = new Label("Project Details");
    private final Label nameLabel = new Label("-");
    private final Label datesLabel = new Label("-");
    private final Label summaryLabel = new Label("-");
    private final Separator sep = new Separator();

    private final Label membersTitle = new Label("Members");
    private final ListView<Member> membersList = new ListView<>();

    private final Button markDoneBtn = new Button("Mark DONE");
    private final Button deleteBtn = new Button("Delete");

    private final InvalidationListener projectStatusListener = obs -> refreshFilters();

    public ProjectsPage(InMemoryStore store, AppState appState) {
        this.store = store;
        this.appState = appState;

        setPadding(new Insets(16));

        // Filter ACTIVE projects only
        activeProjects = new FilteredList<>(store.getProjects(), p -> p.getStatus() == Project.ProjectStatus.ACTIVE);

        // Hook status listeners so filter updates when status changes
        store.getProjects().forEach(this::hookProject);
        store.getProjects().addListener((ListChangeListener<Project>) c -> {
            while (c.next()) {
                if (c.wasAdded()) c.getAddedSubList().forEach(this::hookProject);
                if (c.wasRemoved()) c.getRemoved().forEach(this::unhookProject);
            }
            refreshFilters();
        });

        // LEFT PANEL
        header.getStyleClass().add("page-title");
        header.setStyle("-fx-font-size: 28px; -fx-font-weight: 800;");
        newProjectBtn.getStyleClass().add("primary");
        newProjectBtn.setOnAction(e -> createProject());

        VBox leftTop = new VBox(10, header, newProjectBtn);
        leftTop.setAlignment(Pos.TOP_LEFT);

        projectsList.setItems(activeProjects);
        projectsList.getStyleClass().add("card");
        projectsList.setPrefWidth(360);

        projectsList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Project item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(item.getName());
            }
        });

        VBox left = new VBox(14, leftTop, projectsList);
        VBox.setVgrow(projectsList, Priority.ALWAYS);
        left.setPrefWidth(420);

        // RIGHT PANEL (details)
        detailsTitle.getStyleClass().add("page-title");
        detailsTitle.setStyle("-fx-font-size: 28px; -fx-font-weight: 800;");

        Label nameKey = key("Name:");
        Label datesKey = key("Dates:");
        Label summaryKey = key("Summary:");
        nameLabel.getStyleClass().add("muted");
        datesLabel.getStyleClass().add("muted");
        summaryLabel.getStyleClass().add("muted");

        membersTitle.getStyleClass().add("muted");

        membersList.getStyleClass().add("card");
        membersList.setPrefHeight(220);
        membersList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Member m, boolean empty) {
                super.updateItem(m, empty);
                if (empty || m == null) {
                    setText(null);
                    return;
                }
                // Expecting Member has getName() + getRole()
                String role = (m.getRole() == null) ? "-" : m.getRole().toString();
                setText(m.getName() + "  •  " + role);
            }
        });

        markDoneBtn.getStyleClass().add("primary");
        markDoneBtn.setOnAction(e -> markSelectedDone());

        deleteBtn.getStyleClass().add("secondary");
        deleteBtn.setOnAction(e -> deleteSelected());

        HBox actions = new HBox(10, markDoneBtn, deleteBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox rightCard = new VBox(12,
                detailsTitle,
                nameKey, nameLabel,
                datesKey, datesLabel,
                summaryKey, summaryLabel,
                sep,
                membersTitle,
                membersList,
                actions
        );
        rightCard.getStyleClass().add("card");
        VBox.setVgrow(membersList, Priority.ALWAYS);

        HBox root = new HBox(14, left, rightCard);
        HBox.setHgrow(rightCard, Priority.ALWAYS);

        setCenter(root);

        // Selection wiring
        projectsList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            appState.setSelectedProject(n);
            refreshDetails(n);
        });

        // initial selection
        if (!activeProjects.isEmpty()) {
            projectsList.getSelectionModel().select(0);
        } else {
            refreshDetails(null);
        }
    }

    private void refreshFilters() {
        activeProjects.setPredicate(p -> p.getStatus() == Project.ProjectStatus.ACTIVE);
        // keep selection valid
        Project sel = projectsList.getSelectionModel().getSelectedItem();
        if (sel != null && sel.getStatus() != Project.ProjectStatus.ACTIVE) {
            projectsList.getSelectionModel().clearSelection();
            appState.setSelectedProject(null);
            refreshDetails(null);
        }
    }

    private void refreshDetails(Project p) {
        if (p == null) {
            nameLabel.setText("-");
            datesLabel.setText("-");
            summaryLabel.setText("-");
            membersList.setItems(null);
            markDoneBtn.setDisable(true);
            deleteBtn.setDisable(true);
            return;
        }

        nameLabel.setText(p.getName());

        String dates = safeDate(p.getStartDate()) + " → " + safeDate(p.getEndDate());
        datesLabel.setText(dates);

        long tasks = p.getTasks().size();
        long phases = p.getPhases().size();
        long milestones = p.getMilestones().size();
        summaryLabel.setText("Tasks: " + tasks + " | Phases: " + phases + " | Milestones: " + milestones);

        membersList.setItems(p.getMembers());

        markDoneBtn.setDisable(false);
        deleteBtn.setDisable(false);
    }

    private void createProject() {
        com.projectpilot.ui.dialogs.CreateProjectDialog d =
                new com.projectpilot.ui.dialogs.CreateProjectDialog();

        d.showAndWait().ifPresent(p -> {
            // ensure ACTIVE (dialog already sets template + phases + details)
            p.setStatus(Project.ProjectStatus.ACTIVE);

            store.createProject(p);
            appState.setSelectedProject(p);

            // select in list (will appear because ACTIVE)
            projectsList.getSelectionModel().select(p);
        });
    }


    private void markSelectedDone() {
        Project p = projectsList.getSelectionModel().getSelectedItem();
        if (p == null) return;

        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("ProjectPilot");
        a.setHeaderText("Mark project as DONE?");
        a.setContentText("This will move it to History. You can still keep tasks for reference.");
        var res = a.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.OK) return;

        // MVP: mark done with a simple “System” marker (upgrade later to real user/member)
        store.markProjectDone(p);


        // After predicate refresh, it disappears from ACTIVE list automatically.
        refreshFilters();
        refreshDetails(null);
    }

    private void deleteSelected() {
        Project p = projectsList.getSelectionModel().getSelectedItem();
        if (p == null) return;

        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("ProjectPilot");
        a.setHeaderText("Delete project?");
        a.setContentText("This is permanent. The project and its tasks will be removed.");
        var res = a.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.OK) return;

        store.getProjects().remove(p);

        if (appState.getSelectedProject() == p) {
            appState.setSelectedProject(null);
        }

        if (!activeProjects.isEmpty()) {
            projectsList.getSelectionModel().select(0);
        } else {
            refreshDetails(null);
        }
    }

    private void hookProject(Project p) {
        if (p == null) return;
        p.statusProperty().addListener(projectStatusListener);
    }

    private void unhookProject(Project p) {
        if (p == null) return;
        p.statusProperty().removeListener(projectStatusListener);
    }

    private static Label key(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-font-weight: 700;");
        return l;
    }

    private static String safeDate(Object o) {
        return (o == null) ? "-" : o.toString();
    }
}
