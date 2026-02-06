package com.projectpilot;

import com.projectpilot.core.AppState;
import com.projectpilot.core.PageId;
import com.projectpilot.core.Router;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.data.db.DbManager;
import com.projectpilot.data.db.DbStore;
import com.projectpilot.data.db.auth.AuthService;
import com.projectpilot.data.db.auth.UserSession;
import com.projectpilot.security.AccessPolicy;
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

import java.io.InputStream;

public class Main extends Application {

    private DbManager db;
    private AuthService auth;

    private InMemoryStore store;
    private AppState appState;

    private Scene scene;

    private final AccessPolicy policy = new AccessPolicy();

    @Override
    public void start(Stage stage) {
        db = DbManager.defaultManager();
        db.init();
        System.out.println("DB PATH = " + db.dbFile());

        auth = new AuthService(db);

        loadFont("/fonts/Inter-Regular.ttf", 12);
        loadFont("/fonts/Inter-SemiBold.ttf", 12);

        scene = new Scene(new StackPane(), 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());

        stage.setTitle("ProjectPilot");
        stage.setScene(scene);

        if (auth.needsInitialAdmin()) showSetup();
        else showLogin();

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
        store = new DbStore(db);

        appState = new AppState();
        appState.setSession(session);

        // Pick first project the user is allowed to see
        var initial = store.getProjects().stream()
                .filter(p -> policy.canViewProject(appState, p))
                .findFirst()
                .orElse(null);

        appState.setSelectedProject(initial);

        Router router = new Router();
        router.register(PageId.DASHBOARD, () -> new DashboardPage(store, appState));
        router.register(PageId.PROJECTS, () -> new ProjectsPage(store, appState));
        router.register(PageId.PROJECT_OVERVIEW, () -> new ProjectOverviewPage(store, appState));
        router.register(PageId.TASKS, () -> new TasksPage(store, appState));
        router.register(PageId.GANTT, () -> new GanttPage(store, appState));
        router.register(PageId.TEAM, () -> new TeamPage(store, appState));
        router.register(PageId.HISTORY, () -> new HistoryPage(store, appState));
        router.register(PageId.EXPORT_REPORT, () -> new ExportReportPage(store, appState));

        // Create hub: ADMIN only (this is why you saw the Create page before)
        if (appState.isAdmin()) {
            router.register(PageId.ADMIN, () -> new AdminPage(db, store, appState));
        }

        MainLayout appRoot = new MainLayout(router, store, appState, this::logout);
        appRoot.getStyleClass().add("pp-root");
        scene.setRoot(appRoot);
    }

    private void logout() {
        shutdownDbStore();
        if (appState != null) {
            appState.setSession(null);
            appState.setSelectedProject(null);
        }
        showLogin();
    }

    @Override
    public void stop() {
        shutdownDbStore();
    }

    private void shutdownDbStore() {
        if (store instanceof DbStore ds) ds.shutdown();
    }

    private void loadFont(String path, double size) {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                System.err.println("[UI] Missing font resource: " + path);
                return;
            }
            Font.loadFont(in, size);
        } catch (Exception e) {
            System.err.println("[UI] Failed to load font: " + path + " (" + e.getMessage() + ")");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
