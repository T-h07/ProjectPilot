package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;

public class TasksPage extends VBox {

    private final Label header = new Label("Tasks");
    private final ListView<Task> tasksList = new ListView<>();

    public TasksPage(InMemoryStore store, AppState appState) {
        setPadding(new Insets(16));
        setSpacing(12);

        header.getStyleClass().add("page-title");

        tasksList.setPrefHeight(500);

        refresh(appState.getSelectedProject());
        appState.selectedProjectProperty().addListener((obs, oldV, newV) -> refresh(newV));

        getChildren().addAll(header, tasksList);
    }

    private void refresh(Project p) {
        if (p == null) {
            header.setText("Tasks (no project selected)");
            tasksList.setItems(null);
            return;
        }
        header.setText("Tasks — " + p.getName());
        tasksList.setItems(p.getTasks());
    }
}
