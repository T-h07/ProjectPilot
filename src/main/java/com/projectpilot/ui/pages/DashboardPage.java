package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class DashboardPage extends VBox {
    public DashboardPage(InMemoryStore store, AppState appState) {
        setPadding(new Insets(16));
        setSpacing(10);
        getChildren().addAll(
                new Label("Dashboard"),
                new Label("Active projects: " + store.getProjects().size()),
                new Label("Selected project: " + (appState.getSelectedProject() == null ? "-" : appState.getSelectedProject().getName()))
        );
    }
}
