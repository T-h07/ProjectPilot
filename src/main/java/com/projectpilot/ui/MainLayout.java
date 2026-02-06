package com.projectpilot.ui;

import com.projectpilot.core.AppState;
import com.projectpilot.core.PageId;
import com.projectpilot.core.Router;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.security.AccessPolicy;
import com.projectpilot.ui.components.Sidebar;
import com.projectpilot.ui.components.TopBar;
import com.projectpilot.ui.pages.AccessDeniedPage;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;

public class MainLayout extends BorderPane {

    private final Router router;
    private final AppState appState;

    private final Sidebar sidebar;
    private final AccessPolicy policy = new AccessPolicy();

    public MainLayout(Router router, InMemoryStore store, AppState appState, Runnable onLogout) {
        this.router = router;
        this.appState = appState;

        TopBar topBar = new TopBar(store, appState);
        sidebar = new Sidebar(page -> appState.setCurrentPage(page), onLogout, appState);

        setTop(topBar);
        setLeft(sidebar);

        // initial render
        sidebar.rebuild();
        render(appState.getCurrentPage());

        // Render on navigation
        appState.currentPageProperty().addListener((obs, oldV, newV) -> render(newV));

        // Rebuild + re-render when permissions context changes
        appState.sessionProperty().addListener((obs, o, n) -> {
            sidebar.rebuild();
            render(appState.getCurrentPage());
        });

        appState.selectedProjectProperty().addListener((obs, o, n) -> {
            sidebar.rebuild();
            render(appState.getCurrentPage());
        });

        appState.currentProjectRoleProperty().addListener((obs, o, n) -> {
            sidebar.rebuild();
            render(appState.getCurrentPage());
        });
    }

    private void render(PageId id) {
        if (!policy.canAccess(id, appState)) {
            setCenter(new AccessDeniedPage(policy.denialMessage(id, appState)));
            return;
        }

        try {
            Node page = router.navigate(id);
            setCenter(page);
        } catch (Exception ex) {
            setCenter(new AccessDeniedPage("Page is not available: " + ex.getMessage()));
        }
    }
}
