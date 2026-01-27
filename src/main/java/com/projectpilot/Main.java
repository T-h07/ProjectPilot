package com.projectpilot;

import com.projectpilot.core.AppState;
import com.projectpilot.core.PageId;
import com.projectpilot.core.Router;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.data.db.DbManager;
import com.projectpilot.data.db.DbStore;
import com.projectpilot.data.db.auth.AuthService;
import com.projectpilot.data.db.auth.UserSession;
import com.projectpilot.ui.MainLayout;
import com.projectpilot.ui.pages.*;
import com.projectpilot.ui.pages.admin.AdminPage;
import com.projectpilot.ui.pages.auth.LoginPage;
import com.projectpilot.ui.pages.auth.SetupAdminPage;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public class Main extends Application {

    private DbManager db;
    private AuthService auth;

    private InMemoryStore store;
    private AppState appState;

    private Scene scene;

    @Override
    public void start(Stage stage) {
        db = DbManager.defaultManager();
        db.init(); // ✅ runs migrations (v3 auth tables included)
        System.out.println("DB PATH = " + db.dbFile());


        auth = new AuthService(db);

        Font.loadFont(getClass().getResourceAsStream("/fonts/Inter-Regular.ttf"), 12);
        Font.loadFont(getClass().getResourceAsStream("/fonts/Inter-SemiBold.ttf"), 12);

        // One scene; we swap roots (auth pages -> main layout)
        scene = new Scene(new StackPane(), 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());

        stage.setTitle("ProjectPilot");
        stage.setScene(scene);

        // ✅ Auth flow
        if (auth.needsInitialAdmin()) {
            showSetup();
        } else {
            showLogin();
        }

        stage.setOnCloseRequest(e -> shutdownDbStore());
        stage.show();
    }

    private void showLogin() {
        var root = new LoginPage(auth, this::onLoginSuccess);
        root.getStyleClass().add("pp-root");
        scene.setRoot(root);
    }

    private void showSetup() {
        var root = new SetupAdminPage(auth, this::onLoginSuccess);
        root.getStyleClass().add("pp-root");
        scene.setRoot(root);
    }

    private void onLoginSuccess(UserSession session) {
        // Build store AFTER login (shared DB, but gated UI)
        store = new DbStore(db);

        appState = new AppState();
        appState.setSession(session);

        // Pick selected project from active projects if available; else from history
        if (!store.getProjects().isEmpty()) {
            appState.setSelectedProject(store.getProjects().get(0));
        } else if (!store.getHistoryProjects().isEmpty()) {
            appState.setSelectedProject(store.getHistoryProjects().get(0));
        }

        Router router = new Router();
        router.register(PageId.DASHBOARD, () -> new DashboardPage(store, appState));
        router.register(PageId.PROJECTS, () -> new ProjectsPage(store, appState));
        router.register(PageId.PROJECT_OVERVIEW, () -> new ProjectOverviewPage(store, appState));
        router.register(PageId.TASKS, () -> new TasksPage(store, appState));
        router.register(PageId.GANTT, () -> new GanttPage(store, appState));
        router.register(PageId.TEAM, () -> new TeamPage(store, appState));
        router.register(PageId.HISTORY, () -> new HistoryPage(store, appState));
        router.register(PageId.EXPORT_REPORT, () -> new ExportReportPage(store, appState));

        // ✅ Admin route only for admins
        if (appState.isAdmin()) {
            router.register(PageId.ADMIN, () -> new AdminPage(db, store, appState));
        }

        MainLayout appRoot = new MainLayout(router, store, appState);
        appRoot.getStyleClass().add("pp-root");
        scene.setRoot(appRoot);
    }

    @Override
    public void stop() {
        shutdownDbStore();
    }

    private void shutdownDbStore() {
        if (store instanceof DbStore ds) ds.shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
