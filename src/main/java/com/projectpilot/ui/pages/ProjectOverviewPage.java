package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.Project;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class ProjectOverviewPage extends VBox {

    private final Label projectName = new Label("-");
    private final Label counts = new Label("-");

    public ProjectOverviewPage(InMemoryStore store, AppState appState) {
        setPadding(new Insets(16));
        setSpacing(10);

        Label title = new Label("Project Overview");
        title.getStyleClass().add("page-title");

        refresh(appState.getSelectedProject());

        appState.selectedProjectProperty().addListener((obs, oldV, newV) -> refresh(newV));

        getChildren().addAll(
                title,
                new Label("Selected project:"),
                projectName,
                counts
        );
    }

    private void refresh(Project p) {
        if (p == null) {
            projectName.setText("No project selected");
            counts.setText("-");
            return;
        }
        projectName.setText(p.getName());
        counts.setText("Tasks: " + p.getTasks().size()
                + " | Members: " + p.getMembers().size()
                + " | Phases: " + p.getPhases().size()
                + " | Milestones: " + p.getMilestones().size());
    }
}
