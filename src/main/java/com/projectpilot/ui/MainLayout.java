package com.projectpilot.ui;

import com.projectpilot.core.AppState;
import com.projectpilot.core.Router;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.ui.components.Sidebar;
import com.projectpilot.ui.components.TopBar;
import javafx.scene.layout.BorderPane;

public class MainLayout extends BorderPane {

    public MainLayout(Router router, InMemoryStore store, AppState appState) {
        TopBar topBar = new TopBar(store, appState);

        // ✅ pass appState so Sidebar can show Admin only for admins
        Sidebar sidebar = new Sidebar(page -> appState.setCurrentPage(page), appState);

        setTop(topBar);
        setLeft(sidebar);

        // initial page
        setCenter(router.navigate(appState.getCurrentPage()));

        // react to navigation
        appState.currentPageProperty().addListener((obs, oldV, newV) -> {
            setCenter(router.navigate(newV));
        });
    }
}
