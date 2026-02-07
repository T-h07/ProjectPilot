package com.projectpilot;

import com.projectpilot.core.AppState;
import com.projectpilot.core.PageId;
import com.projectpilot.core.Router;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.data.db.DbManager;
import com.projectpilot.data.db.DbStore;
import com.projectpilot.data.db.auth.AuthProvider;
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
import com.projectpilot.lan.LanAuthClient;
import com.projectpilot.lan.LanClient;
import com.projectpilot.lan.LanConfig;
import com.projectpilot.lan.LanServer;
import com.projectpilot.lan.LanSyncService;
import com.projectpilot.lan.RemoteStore;

public class Main extends Application {

    private DbManager db;
    private AuthService localAuth;
    private AuthProvider auth;

    private InMemoryStore store;
    private AppState appState;

    private Scene scene;

    private final AccessPolicy policy = new AccessPolicy();

    @Override
    public void start(Stage stage) {
        lanConfig = LanConfig.fromSystem();

        if (lanConfig.isClient()) {
            lanClient = new LanClient(lanConfig.baseUrl());
            auth = new LanAuthClient(lanClient);
        } else {
            db = DbManager.defaultManager();
            db.init();
            System.out.println("DB PATH = " + db.dbFile());
            localAuth = new AuthService(db);
            auth = localAuth;
        }

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
        if (lanConfig.isClient()) {
            RemoteStore remote = new RemoteStore(lanClient);
            store = remote;

            appState = new AppState();
            appState.setSession(session);

            lanSync = new LanSyncService(remote, lanClient, appState, lanConfig.pollMs());
            lanSync.start();
        } else {
            store = new DbStore(db);

            appState = new AppState();
            appState.setSession(session);

            // Pick first project the user is allowed to see
            var initial = store.getProjects().stream()
                    .filter(p -> policy.canViewProject(appState, p))
                    .findFirst()
                    .orElse(null);

            appState.setSelectedProject(initial);

            if (lanConfig.isHost()) {
                lanServer = new LanServer(store, localAuth, lanConfig.port());
                lanServer.start();
                System.out.println("LAN HOST listening on port " + lanConfig.port());
            }
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

        // Create hub: ADMIN only (this is why you saw the Create page before)
        if (appState.isAdmin() && store instanceof DbStore) {
            router.register(PageId.ADMIN, () -> new AdminPage(db, store, appState));
        }

        MainLayout appRoot = new MainLayout(router, store, appState, this::logout);
        appRoot.getStyleClass().add("pp-root");
        scene.setRoot(appRoot);
    }

    private void logout() {
        shutdownServices();
        if (appState != null) {
            appState.setSession(null);
            appState.setSelectedProject(null);
        }
        showLogin();
    }

    @Override
    public void stop() {
        shutdownServices();
    }

    private void shutdownServices() {
        if (lanSync != null) {
            lanSync.stop();
            lanSync = null;
        }
        if (lanServer != null) {
            lanServer.stop();
            lanServer = null;
        }
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
    private LanConfig lanConfig;
    private LanClient lanClient;
    private LanServer lanServer;
    private LanSyncService lanSync;
