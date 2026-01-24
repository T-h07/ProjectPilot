package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.Member;
import com.projectpilot.model.Project;
import com.projectpilot.ui.dialogs.AddMemberDialog;
import com.projectpilot.ui.dialogs.CreateProjectDialog;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class ProjectsPage extends VBox {

    public ProjectsPage(InMemoryStore store, AppState appState) {
        setPadding(new Insets(16));
        setSpacing(12);

        Label title = new Label("Projects");
        title.getStyleClass().add("page-title");

        Button newProject = new Button("New Project");
        newProject.getStyleClass().add("primary");
        newProject.setOnAction(e -> {
            CreateProjectDialog d = new CreateProjectDialog();
            d.showAndWait().ifPresent(p -> {
                store.createProject(p);
                appState.setSelectedProject(p);
            });
        });

        HBox toolbar = new HBox(10, newProject);
        toolbar.setPadding(new Insets(0, 0, 6, 0));

        ListView<Project> projectList = new ListView<>(store.getProjects());
        projectList.setPrefWidth(360);

        // keep list selection in sync with AppState
        if (appState.getSelectedProject() != null) projectList.getSelectionModel().select(appState.getSelectedProject());

        projectList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) appState.setSelectedProject(newV);
        });

        // Right side details
        Label detailsTitle = new Label("Project Details");
        detailsTitle.getStyleClass().add("page-title");

        Label name = new Label("-");
        Label dates = new Label("-");
        Label meta = new Label("-");
        name.getStyleClass().add("muted");
        dates.getStyleClass().add("muted");
        meta.getStyleClass().add("muted");

        ListView<Member> members = new ListView<>();
        members.setPrefHeight(220);

        Button addMember = new Button("Add Member");
        addMember.setOnAction(e -> {
            Project p = appState.getSelectedProject();
            if (p == null) return;
            AddMemberDialog d = new AddMemberDialog();
            d.showAndWait().ifPresent(m -> store.addMember(p, m));
        });

        VBox right = new VBox(12,
                detailsTitle,
                new Label("Name:"), name,
                new Label("Dates:"), dates,
                new Label("Summary:"), meta,
                new Separator(),
                new Label("Members"),
                members,
                addMember
        );
        right.getStyleClass().add("card");

        // refresh details when selection changes
        Runnable refresh = () -> {
            Project p = appState.getSelectedProject();
            if (p == null) {
                name.setText("No project selected");
                dates.setText("-");
                meta.setText("-");
                members.setItems(null);
                addMember.setDisable(true);
                return;
            }
            name.setText(p.getName());
            dates.setText(p.getStartDate() + " → " + p.getEndDate());
            meta.setText("Tasks: " + p.getTasks().size() + " | Phases: " + p.getPhases().size() + " | Milestones: " + p.getMilestones().size());
            members.setItems(p.getMembers());
            addMember.setDisable(false);
        };

        refresh.run();
        appState.selectedProjectProperty().addListener((obs, o, n) -> {
            projectList.getSelectionModel().select(n);
            refresh.run();
        });

        HBox content = new HBox(14, projectList, right);
        HBox.setHgrow(right, Priority.ALWAYS);

        getChildren().addAll(title, toolbar, content);
    }
}
