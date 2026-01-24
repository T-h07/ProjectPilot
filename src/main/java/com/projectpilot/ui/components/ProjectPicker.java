package com.projectpilot.ui.components;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.Project;
import javafx.scene.control.ComboBox;

public class ProjectPicker extends ComboBox<Project> {

    public ProjectPicker(InMemoryStore store, AppState appState) {
        setItems(store.getProjects());
        setPromptText("Select project");
        setPrefWidth(320);

        // init selection
        if (appState.getSelectedProject() != null) setValue(appState.getSelectedProject());

        // UI -> state
        valueProperty().addListener((obs, oldV, newV) -> appState.setSelectedProject(newV));

        // state -> UI (if changed elsewhere later)
        appState.selectedProjectProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV != getValue()) setValue(newV);
        });
    }
}
