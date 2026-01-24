package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.Project;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class GanttPage extends VBox {

    private final Label header = new Label("Gantt");

    public GanttPage(InMemoryStore store, AppState appState) {
        setPadding(new Insets(16));
        setSpacing(10);

        header.getStyleClass().add("page-title");

        refresh(appState.getSelectedProject());
        appState.selectedProjectProperty().addListener((obs, oldV, newV) -> refresh(newV));

        getChildren().addAll(header, new Label("Timeline view will go here (MVP: table, later: visual bars)."));
    }

    private void refresh(Project p) {
        header.setText(p == null ? "Gantt (no project selected)" : "Gantt — " + p.getName());
    }
}
