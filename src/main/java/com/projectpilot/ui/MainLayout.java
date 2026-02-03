package com.projectpilot.ui;

import com.projectpilot.core.AppState;
import com.projectpilot.core.Router;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.ui.components.Sidebar;
import com.projectpilot.ui.components.TopBar;
import javafx.scene.layout.BorderPane;

public class MainLayout extends BorderPane {

    public MainLayout(Router router, InMemoryStore store, AppState appState, Runnable onLogout) {
        TopBar topBar = new TopBar(store, appState);

        Sidebar sidebar = new Sidebar(page -> appState.setCurrentPage(page), onLogout, appState);


        setTop(topBar);
        setLeft(sidebar);

        setCenter(router.navigate(appState.getCurrentPage()));

        appState.currentPageProperty().addListener((obs, oldV, newV) -> {
            setCenter(router.navigate(newV));
        });
    }
}

