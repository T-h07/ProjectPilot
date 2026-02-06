package com.projectpilot.ui.components;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.service.NotificationService;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public class TopBar extends HBox {

    public TopBar(InMemoryStore store, AppState appState, NotificationService notifications) {
        setPadding(new Insets(12));
        setSpacing(12);
        getStyleClass().add("topbar");

        Label title = new Label("ProjectPilot");
        title.getStyleClass().add("app-title");

        ProjectPicker picker = new ProjectPicker(store, appState);

        NotificationBellButton bell = new NotificationBellButton(notifications);

        TextField search = new TextField();
        search.setPromptText("Search (later)");
        search.setDisable(true);
        HBox.setHgrow(search, Priority.ALWAYS);

        getChildren().addAll(title, picker, bell, search);
    }

    // Backward compatible constructor (optional)
    public TopBar(InMemoryStore store, AppState appState) {
        this(store, appState, new NotificationService(store, appState));
    }
}
