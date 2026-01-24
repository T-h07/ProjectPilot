package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.Project;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

public class ExportReportPage extends VBox {

    private final Label header = new Label("Export Report");
    private final TextArea preview = new TextArea();

    public ExportReportPage(InMemoryStore store, AppState appState) {
        setPadding(new Insets(16));
        setSpacing(12);

        header.getStyleClass().add("page-title");

        preview.setEditable(false);
        preview.setPrefHeight(600);

        refresh(appState.getSelectedProject());
        appState.selectedProjectProperty().addListener((obs, oldV, newV) -> refresh(newV));

        getChildren().addAll(header, preview);
    }

    private void refresh(Project p) {
        if (p == null) {
            header.setText("Export Report (no project selected)");
            preview.setText("Select a project to generate a report preview.");
            return;
        }

        header.setText("Export Report — " + p.getName());
        preview.setText(
                "PROJECT SNAPSHOT (Preview)\n" +
                        "Project: " + p.getName() + "\n" +
                        "Tasks: " + p.getTasks().size() + "\n" +
                        "Members: " + p.getMembers().size() + "\n" +
                        "Phases: " + p.getPhases().size() + "\n" +
                        "Milestones: " + p.getMilestones().size() + "\n\n" +
                        "(Next: we’ll generate a structured report layout.)"
        );
    }
}
