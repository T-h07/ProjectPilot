package com.projectpilot.ui;

import com.projectpilot.core.AppState;
import com.projectpilot.core.PageId;
import com.projectpilot.core.Router;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.security.AccessPolicy;
import com.projectpilot.service.NotificationService;
import com.projectpilot.ui.components.Sidebar;
import com.projectpilot.ui.components.TopBar;
import com.projectpilot.ui.dialogs.CommandPaletteDialog;
import com.projectpilot.ui.dialogs.NotificationsDialog;
import com.projectpilot.ui.pages.AccessDeniedPage;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.stage.Window;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.event.EventHandler;

public class MainLayout extends BorderPane {

    private final Router router;
    private final AppState appState;
    private final InMemoryStore store;

    private final Sidebar sidebar;
    private final AccessPolicy policy = new AccessPolicy();

    private final NotificationService notifications;
    private final EventHandler<KeyEvent> paletteShortcut;

    // ensure login dialog shows only once per session instance
    private Object lastShownSessionRef = null;

    // ✅ Keep your existing 4-arg constructor so Main.java compiles
    public MainLayout(Router router, InMemoryStore store, AppState appState, Runnable onLogout) {
        this(router, store, appState, new NotificationService(store, appState), onLogout);
    }

    // Optional injection constructor (nice for testing / future refactor)
    public MainLayout(Router router, InMemoryStore store, AppState appState, NotificationService notifications, Runnable onLogout) {
        this.router = router;
        this.store = store;
        this.appState = appState;
        this.notifications = notifications;
        this.paletteShortcut = event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.K) {
                Window owner = (getScene() == null) ? null : getScene().getWindow();
                CommandPaletteDialog.show(owner, store, appState, appState::setCurrentPage);
                event.consume();
            }
        };

        TopBar topBar = new TopBar(store, appState, notifications);
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
            maybeShowLoginNotifications();
        });

        appState.selectedProjectProperty().addListener((obs, o, n) -> {
            sidebar.rebuild();
            render(appState.getCurrentPage());
        });

        appState.currentProjectRoleProperty().addListener((obs, o, n) -> {
            sidebar.rebuild();
            render(appState.getCurrentPage());
        });

        // If scene becomes available later, try showing login notifications then
        sceneProperty().addListener((obs, o, n) -> {
            if (o != null) o.removeEventFilter(KeyEvent.KEY_PRESSED, paletteShortcut);
            if (n != null) n.addEventFilter(KeyEvent.KEY_PRESSED, paletteShortcut);
            maybeShowLoginNotifications();
        });
    }

    private void maybeShowLoginNotifications() {
        if (appState.getSession() == null) return;
        if (appState.getSession() == lastShownSessionRef) return;

        Window owner = (getScene() == null) ? null : getScene().getWindow();
        if (owner == null) return;

        lastShownSessionRef = appState.getSession();

        // Build notifications for this user + show “What’s new” dialog
        Platform.runLater(() -> {
            notifications.rebuildNow();
            NotificationsDialog.showLogin(owner, notifications);
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
