package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.Member;
import com.projectpilot.model.Project;
import com.projectpilot.model.enums.ProjectRole;
import com.projectpilot.security.AccessPolicy;
import com.projectpilot.ui.dialogs.CreateProjectDialog;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.IdentityHashMap;
import java.util.Map;

public class ProjectsPage extends BorderPane {

    private final InMemoryStore store;
    private final AppState appState;
    private final AccessPolicy policy = new AccessPolicy();

    private final Label header = new Label("Projects");
    private final Button newProjectBtn = new Button("New Project");

    private final ListView<Project> projectsList = new ListView<>();
    private final FilteredList<Project> visibleProjects;

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

    // refresh when membership changes
    private final Map<Project, ListChangeListener<?>> memberHooks = new IdentityHashMap<>();

    private final BooleanBinding canCreateProject;
    private final BooleanBinding canMarkDone;
    private final BooleanBinding canDelete;

    public ProjectsPage(InMemoryStore store, AppState appState) {
        this.store = store;
        this.appState = appState;

        setPadding(new Insets(16));

        visibleProjects = new FilteredList<>(store.getProjects(), p -> policy.canViewProject(appState, p));

        canCreateProject = Bindings.createBooleanBinding(
                () -> policy.canCreateProject(appState),
                appState.sessionProperty()
        );

        canMarkDone = Bindings.createBooleanBinding(
                () -> policy.canMarkProjectDone(appState),
                appState.sessionProperty(),
                appState.currentProjectRoleProperty(),
                appState.selectedProjectProperty()
        );

        canDelete = Bindings.createBooleanBinding(
                () -> policy.canDeleteProject(appState),
                appState.sessionProperty()
        );

        hookProjectLists();

        // LEFT
        header.getStyleClass().add("page-title");
        header.setStyle("-fx-font-size: 28px; -fx-font-weight: 800;");

        newProjectBtn.getStyleClass().add("primary");
        newProjectBtn.visibleProperty().bind(canCreateProject);
        newProjectBtn.managedProperty().bind(newProjectBtn.visibleProperty());
        newProjectBtn.setOnAction(e -> createProject());

        VBox leftTop = new VBox(10, header, newProjectBtn);
        leftTop.setAlignment(Pos.TOP_LEFT);

        projectsList.setItems(visibleProjects);
        projectsList.getStyleClass().add("card");
        projectsList.setPrefWidth(360);

        projectsList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Project item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });

        VBox left = new VBox(14, leftTop, projectsList);
        VBox.setVgrow(projectsList, Priority.ALWAYS);
        left.setPrefWidth(420);

        // RIGHT
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
                if (empty || m == null) { setText(null); return; }
                String role = (m.getRole() == null) ? "-" : m.getRole().toString();
                setText(m.getName() + "  •  " + role);
            }
        });

        markDoneBtn.getStyleClass().add("primary");
        markDoneBtn.visibleProperty().bind(canMarkDone);
        markDoneBtn.managedProperty().bind(markDoneBtn.visibleProperty());
        markDoneBtn.setOnAction(e -> markSelectedDone());

        deleteBtn.getStyleClass().add("secondary");
        deleteBtn.visibleProperty().bind(canDelete);
        deleteBtn.managedProperty().bind(deleteBtn.visibleProperty());
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

        projectsList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            appState.setSelectedProject(n);
            refreshDetails(appState.getSelectedProject());
        });

        // session/membership changes can invalidate selection -> re-apply
        appState.sessionProperty().addListener((obs, o, n) -> {
            refreshFilters();
            pickFirstIfNeeded();
        });

        // initial
        refreshFilters();
        pickFirstIfNeeded();
    }

    private void hookProjectLists() {
        store.getProjects().forEach(this::hookProject);
        store.getProjects().addListener((ListChangeListener<Project>) c -> {
            while (c.next()) {
                if (c.wasAdded()) c.getAddedSubList().forEach(this::hookProject);
                if (c.wasRemoved()) c.getRemoved().forEach(this::unhookProject);
            }
            refreshFilters();
            pickFirstIfNeeded();
        });
    }

    private void refreshFilters() {
        visibleProjects.setPredicate(p -> policy.canViewProject(appState, p));

        Project sel = projectsList.getSelectionModel().getSelectedItem();
        if (sel != null && !visibleProjects.contains(sel)) {
            projectsList.getSelectionModel().clearSelection();
            appState.setSelectedProject(null);
            refreshDetails(null);
        }
    }

    private void pickFirstIfNeeded() {
        if (appState.getSelectedProject() != null && visibleProjects.contains(appState.getSelectedProject())) {
            projectsList.getSelectionModel().select(appState.getSelectedProject());
            refreshDetails(appState.getSelectedProject());
            return;
        }

        if (!visibleProjects.isEmpty()) {
            projectsList.getSelectionModel().select(0);
            Project p = projectsList.getSelectionModel().getSelectedItem();
            appState.setSelectedProject(p);
            refreshDetails(appState.getSelectedProject());
        } else {
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
            return;
        }

        nameLabel.setText(p.getName());
        datesLabel.setText(safeDate(p.getStartDate()) + " → " + safeDate(p.getEndDate()));

        long tasks = p.getTasks().size();
        long phases = p.getPhases().size();
        long milestones = p.getMilestones().size();
        summaryLabel.setText("Tasks: " + tasks + " | Phases: " + phases + " | Milestones: " + milestones);

        membersList.setItems(p.getMembers());
    }

    private void createProject() {
        if (!policy.canCreateProject(appState)) return;

        CreateProjectDialog d = new CreateProjectDialog();
        d.showAndWait().ifPresent(p -> {
            p.setStatus(Project.ProjectStatus.ACTIVE);

            store.createProject(p);

            // Ensure project has at least 1 member (creator) so tasks can be created immediately.
            ensureCreatorIsMember(p);

            appState.setSelectedProject(p);
            refreshFilters();
            projectsList.getSelectionModel().select(p);
        });
    }

    private void ensureCreatorIsMember(Project p) {
        if (p == null) return;
        if (p.getMembers() != null && !p.getMembers().isEmpty()) return;

        var s = appState.getSession();
        if (s == null) return;

        String id = s.id();
        String name = (s.displayName() != null && !s.displayName().isBlank()) ? s.displayName() : s.username();
        if (name == null || name.isBlank()) name = "System";

        Member creator = new Member(id, name, ProjectRole.LEADER);
        store.addMember(p, creator);
    }

    private void markSelectedDone() {
        if (!policy.canMarkProjectDone(appState)) return;

        Project p = projectsList.getSelectionModel().getSelectedItem();
        if (p == null) return;

        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("ProjectPilot");
        a.setHeaderText("Mark project as DONE?");
        a.setContentText("This will move it to History.");
        var res = a.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.OK) return;

        store.markProjectDone(p);

        refreshFilters();
        pickFirstIfNeeded();
    }

    private void deleteSelected() {
        if (!policy.canDeleteProject(appState)) return;

        Project p = projectsList.getSelectionModel().getSelectedItem();
        if (p == null) return;

        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("ProjectPilot");
        a.setHeaderText("Delete project?");
        a.setContentText("This is permanent.");
        var res = a.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.OK) return;

        store.deleteProject(p);

        refreshFilters();
        pickFirstIfNeeded();
    }

    private void hookProject(Project p) {
        if (p == null) return;

        p.statusProperty().addListener(projectStatusListener);

        if (!memberHooks.containsKey(p)) {
            ListChangeListener<?> l = c -> refreshFilters();
            try {
                p.getMembers().addListener((ListChangeListener) l);
                memberHooks.put(p, l);
            } catch (Exception ignored) {}
        }
    }

    private void unhookProject(Project p) {
        if (p == null) return;

        p.statusProperty().removeListener(projectStatusListener);

        ListChangeListener<?> l = memberHooks.remove(p);
        if (l != null) {
            try { p.getMembers().removeListener((ListChangeListener) l); } catch (Exception ignored) {}
        }
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
