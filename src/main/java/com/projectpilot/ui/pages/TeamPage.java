package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.Member;
import com.projectpilot.model.Project;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;

public class TeamPage extends VBox {

    private final Label header = new Label("Team");
    private final ListView<Member> membersList = new ListView<>();

    public TeamPage(InMemoryStore store, AppState appState) {
        setPadding(new Insets(16));
        setSpacing(12);

        header.getStyleClass().add("page-title");
        membersList.setPrefHeight(500);

        refresh(appState.getSelectedProject());
        appState.selectedProjectProperty().addListener((obs, oldV, newV) -> refresh(newV));

        getChildren().addAll(header, membersList);
    }

    private void refresh(Project p) {
        if (p == null) {
            header.setText("Team (no project selected)");
            membersList.setItems(null);
            return;
        }
        header.setText("Team — " + p.getName());
        membersList.setItems(p.getMembers());
    }
}
