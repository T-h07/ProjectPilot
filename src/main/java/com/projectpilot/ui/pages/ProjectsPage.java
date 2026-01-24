package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.Project;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;

public class ProjectsPage extends VBox {

    public ProjectsPage(InMemoryStore store, AppState appState) {
        setPadding(new Insets(16));
        setSpacing(12);

        Label title = new Label("Projects");
        title.getStyleClass().add("page-title");

        ListView<Project> list = new ListView<>(store.getProjects());
        list.setPrefHeight(500);

        // set initial selection
        if (appState.getSelectedProject() != null) {
            list.getSelectionModel().select(appState.getSelectedProject());
        }

        // selection -> app state
        list.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) appState.setSelectedProject(newV);
        });

        getChildren().addAll(title, new Label("Select a project:"), list);
    }
}
