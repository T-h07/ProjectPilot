package com.projectpilot;

import com.projectpilot.admin.AdminService;
import com.projectpilot.admin.DbAdminService;
import com.projectpilot.admin.LanAdminClient;
import com.projectpilot.chat.ChatService;
import com.projectpilot.chat.ChatUnreadService;
import com.projectpilot.chat.DbChatService;
import com.projectpilot.chat.LanChatService;
import com.projectpilot.core.AppState;
import com.projectpilot.core.PageId;
import com.projectpilot.core.Router;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.data.SampleData;
import com.projectpilot.data.db.DbManager;
import com.projectpilot.data.db.DbStore;
import com.projectpilot.data.db.auth.AuthProvider;
import com.projectpilot.data.db.auth.AuthService;
import com.projectpilot.data.db.auth.UserSession;
import com.projectpilot.security.AccessPolicy;
import com.projectpilot.ui.MainLayout;
import com.projectpilot.ui.pages.*;
import com.projectpilot.ui.pages.admin.AdminPage;
import com.projectpilot.lan.LanDiscovery;
import com.projectpilot.ui.pages.auth.LanSetupPage;
import com.projectpilot.ui.pages.auth.LoginPage;
import com.projectpilot.ui.pages.auth.SetupAdminPage;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.projectpilot.lan.LanAuthClient;
import com.projectpilot.lan.LanClient;
import com.projectpilot.lan.LanConfig;
import com.projectpilot.lan.LanServer;
import com.projectpilot.lan.LanSessionRegistry;
import com.projectpilot.lan.LanStoreBroadcaster;
import com.projectpilot.lan.LanSyncService;
import com.projectpilot.lan.LanWsClient;
import com.projectpilot.lan.LanWsServer;
import com.projectpilot.lan.RemoteStore;
import com.projectpilot.lan.dto.ServerStatusDto;

public class Main extends Application {

    private DbManager db;
    private AuthService localAuth;
    private AuthProvider auth;

    private InMemoryStore store;
    private AppState appState;

    private Scene scene;

    private final AccessPolicy policy = new AccessPolicy();

    private LanConfig lanConfig;
    private LanClient lanClient;
    private LanServer lanServer;
    private LanWsServer lanWsServer;
    private LanSessionRegistry lanSessions;
    private LanStoreBroadcaster lanBroadcaster;
    private LanSyncService lanSync;
    private LanDiscovery.Responder lanDiscovery;
    private ChatService chatService;
    private ChatUnreadService chatUnread;
    private ScheduledExecutorService hostStatusExec;
    private ScheduledExecutorService cloudStatusExec;

    @Override
    public void start(Stage stage) {
        lanConfig = LanConfig.fromSystem();

        loadFont("/fonts/Inter-Regular.ttf", 12);
        loadFont("/fonts/Inter-SemiBold.ttf", 12);

        scene = new Scene(new StackPane(), 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());

        stage.setTitle("ProjectPilot");
        loadIcon(stage, "/icons/app.png");
        stage.setScene(scene);

        stage.setOnCloseRequest(e -> shutdownServices());
        stage.show();

        if (lanConfig.isClient() || lanConfig.isHost()) {
            bootstrapMode(lanConfig);
        } else {
            showLanSetup();
        }
    }

    private void showLogin() {
        var root = new LoginPage(auth, this::onLoginSuccess);
        root.getStyleClass().add("pp-root");
        scene.setRoot(root);
    }

    private void showLanSetup() {
        var root = new LanSetupPage(lanConfig, this::bootstrapMode);
        root.getStyleClass().add("pp-root");
        scene.setRoot(root);
    }

    private void showSetup() {
        var root = new SetupAdminPage(auth, this::onLoginSuccess);
        root.getStyleClass().add("pp-root");
        scene.setRoot(root);
    }

    private void bootstrapMode(LanConfig config) {
        lanConfig = config == null ? LanConfig.fromSystem() : config;
        stopDiscovery();
        stopCloudStatusMonitor();
        if (lanSync != null) {
            lanSync.stop();
            lanSync = null;
        }

        safeStopLanHost();
        if (lanConfig.isClient()) {
            lanClient = new LanClient(lanConfig.baseUrl());
            auth = new LanAuthClient(lanClient);
        } else {
            lanClient = null;
            db = DbManager.defaultManager();
            db.init();
            System.out.println("DB = " + db.describe());
            localAuth = new AuthService(db);
            auth = localAuth;
        }

        if (auth.needsInitialAdmin()) showSetup();
        else showLogin();
    }

    private void onLoginSuccess(UserSession session) {
        try {
            if (lanConfig.isClient()) {
                RemoteStore remote = new RemoteStore(lanClient);
                store = remote;

                appState = new AppState();
                appState.setSession(session);

                LanWsClient wsClient = new LanWsClient(lanConfig.wsUrl(), () -> {
                    if (lanSync != null) lanSync.requestRefresh();
                });
                lanSync = new LanSyncService(remote, lanClient, appState, lanConfig.pollMs(), wsClient);
                lanSync.start();
                chatService = new LanChatService(lanClient);
            } else {
                initHostStoreIfNeeded();

                appState = new AppState();
                appState.setSession(session);

                seedSampleDataIfEmpty(session);

                // Pick first project the user is allowed to see
                var initial = store.getProjects().stream()
                        .filter(p -> policy.canViewProject(appState, p))
                        .findFirst()
                        .orElse(null);

                appState.setSelectedProject(initial);
            }

            updateHostStatusForMode();
            startCloudStatusMonitor();

            if (chatUnread != null) {
                chatUnread.stop();
                chatUnread = null;
            }
            if (chatService != null && appState != null) {
                chatUnread = new ChatUnreadService(chatService, appState, 4000);
                chatUnread.start();
            }

            Router router = new Router();
            router.register(PageId.DASHBOARD, () -> new DashboardPage(store, appState));
            router.register(PageId.PROJECTS, () -> new ProjectsPage(store, appState));
            router.register(PageId.PROJECT_OVERVIEW, () -> new ProjectOverviewPage(store, appState));
            router.register(PageId.TASKS, () -> new TasksPage(store, appState));
            router.register(PageId.GANTT, () -> new GanttPage(store, appState));
            router.register(PageId.TEAM, () -> new TeamPage(store, appState));
            router.register(PageId.MESSAGES, () -> new MessagesPage(chatService, appState));
            router.register(PageId.HISTORY, () -> new HistoryPage(store, appState));
            router.register(PageId.EXPORT_REPORT, () -> new ExportReportPage(store, appState));

            AdminService adminService = appState.isAdmin()
                    ? (store instanceof DbStore
                        ? new DbAdminService(db, (DbStore) store)
                        : (lanClient != null ? new LanAdminClient(lanClient) : null))
                    : null;

            if (adminService != null) {
                router.register(PageId.ADMIN, () -> new AdminPage(adminService, store, appState));
            }

            MainLayout appRoot = new MainLayout(router, store, appState, this::logout);
            appRoot.getStyleClass().add("pp-root");
            scene.setRoot(appRoot);

            if (lanConfig.isHost() && lanServer == null && appState.isAdmin()) {
                startLanHostServices();
            }
        } catch (Exception e) {
            System.err.println("[UI] Login init failed: " + e.getMessage());
            e.printStackTrace();
            showStartupError("Login failed", "Could not open the dashboard.", e);
            showLogin();
        }
    }

    private void seedSampleDataIfEmpty(UserSession session) {
        if (store == null) return;
        if (!store.getProjects().isEmpty() || !store.getHistoryProjects().isEmpty()) return;

        String id = session == null ? null : session.id();
        String name = session == null ? null : session.displayName();
        if (name == null || name.isBlank()) name = session == null ? null : session.username();

        java.util.List<SampleData.UserSeed> users = java.util.List.of();
        if (store instanceof DbStore ds) {
            users = ds.listUsers().stream()
                    .map(u -> {
                        String display = (u.name() == null || u.name().isBlank()) ? u.username() : u.name();
                        return new SampleData.UserSeed(u.id(), display);
                    })
                    .toList();
        }

        SampleData.seed(store, id, name, users);
    }

    private void startLanHostServices() {
        try {
            if (lanServer != null || lanWsServer != null) return;
            if (store == null) {
                System.err.println("[LAN] Host start skipped: store not ready");
                return;
            }
            if (localAuth == null) {
                System.err.println("[LAN] Host start skipped: auth not ready");
                return;
            }
            lanSessions = new LanSessionRegistry();
            lanWsServer = new LanWsServer(lanConfig.wsPort(), lanSessions);
            lanWsServer.start();

            lanServer = new LanServer(store, localAuth, chatService, lanSessions, lanWsServer, lanConfig.port());
            lanServer.start();
            lanBroadcaster = new LanStoreBroadcaster(store, lanWsServer);
            lanBroadcaster.start();
            startLanDiscovery();
            startHostStatusMonitor();
            if (appState != null) {
                appState.updateHostingStatus(true, lanConfig.port(), lanConfig.wsPort(),
                        System.currentTimeMillis(), 0, "host");
            }
            System.out.println("LAN HOST listening on port " + lanConfig.port() + " (ws " + lanConfig.wsPort() + ")");
        } catch (Throwable e) {
            System.err.println("[LAN] Host startup failed: " + e.getMessage());
            e.printStackTrace();
            safeStopLanHost();
            showStartupError("LAN host failed",
                    "Dashboard opened, but LAN hosting couldn't start. Check ports 8090/8091 or firewall.",
                    e);
        }
    }

    private void initHostStoreIfNeeded() {
        if (db == null) {
            db = DbManager.defaultManager();
            db.init();
        }
        if (store instanceof DbStore ds && ds.isShutdown()) {
            store = null;
        }
        if (!(store instanceof DbStore)) {
            store = new DbStore(db);
        }
        if (!(chatService instanceof DbChatService)) {
            chatService = new DbChatService(db);
        }
    }

    private void safeStopLanHost() {
        try {
            if (lanServer != null) lanServer.stop();
        } catch (Exception ignored) {
        }
        try {
            if (lanBroadcaster != null) lanBroadcaster.stop();
        } catch (Exception ignored) {
        }
        try {
            if (lanWsServer != null) lanWsServer.stop();
        } catch (Exception ignored) {
        }
        lanServer = null;
        lanWsServer = null;
        lanBroadcaster = null;
        lanSessions = null;
        stopHostStatusMonitor();
        stopDiscovery();
        if (appState != null) {
            appState.updateHostingStatus(false, lanConfig.port(), lanConfig.wsPort(), 0L, 0, "local");
        }
    }

    private void showStartupError(String title, String message, Throwable error) {
        String detail = (error == null || error.getMessage() == null) ? "" : error.getMessage();
        String text = (message == null ? "" : message);
        if (!detail.isBlank()) {
            text = text.isBlank() ? detail : text + "\n\nDetails: " + detail;
        }

        final String finalTitle = title;
        final String finalText = text;
        Runnable show = () -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(finalTitle);
            alert.setHeaderText(finalTitle);
            alert.setContentText(finalText);
            alert.show();
        };

        if (Platform.isFxApplicationThread()) show.run();
        else Platform.runLater(show);
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
        stopDiscovery();
        safeStopLanHost();
        stopCloudStatusMonitor();
        if (lanSync != null) {
            lanSync.stop();
            lanSync = null;
        }
        if (chatUnread != null) {
            chatUnread.stop();
            chatUnread = null;
        }
        if (store instanceof DbStore ds) ds.shutdown();
        store = null;
        chatService = null;
    }

    private void stopDiscovery() {
        if (lanDiscovery != null) {
            try {
                lanDiscovery.stop();
            } catch (Exception ignored) {
            }
            lanDiscovery = null;
        }
    }

    private void updateHostStatusForMode() {
        if (appState == null) return;
        if (lanConfig.isHost() && !appState.isAdmin()) {
            safeStopLanHost();
            lanConfig = LanConfig.forLocal(lanConfig.port(), lanConfig.wsPort(), lanConfig.pollMs());
            appState.updateHostingStatus(false, lanConfig.port(), lanConfig.wsPort(), 0L, 0, "local");
            showStartupNotice("Hosting disabled",
                    "Only admins can host on LAN. You're running in local mode.");
            return;
        }

        if (lanConfig.isHost()) {
            appState.updateHostingStatus(true, lanConfig.port(), lanConfig.wsPort(), System.currentTimeMillis(), 0, "host");
        } else if (lanConfig.isClient()) {
            appState.updateHostingStatus(false, lanConfig.port(), lanConfig.wsPort(), 0L, 0, "client");
        } else {
            appState.updateHostingStatus(false, lanConfig.port(), lanConfig.wsPort(), 0L, 0, "local");
        }
    }

    private void startLanDiscovery() {
        try {
            if (lanDiscovery == null) {
                lanDiscovery = LanDiscovery.startResponder(lanConfig.port(), lanConfig.wsPort());
            }
        } catch (Exception e) {
            System.err.println("[LAN] Discovery responder failed: " + e.getMessage());
        }
    }

    private void startHostStatusMonitor() {
        stopHostStatusMonitor();
        hostStatusExec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pp-host-status");
            t.setDaemon(true);
            return t;
        });
        hostStatusExec.scheduleAtFixedRate(() -> {
            if (appState == null) return;
            if (lanWsServer == null || !appState.isHosting()) {
                appState.setHostConnections(0);
                return;
            }
            appState.setHostConnections(lanWsServer.connectedCount());
        }, 0, 2, TimeUnit.SECONDS);
    }

    private void stopHostStatusMonitor() {
        if (hostStatusExec != null) {
            hostStatusExec.shutdownNow();
            hostStatusExec = null;
        }
    }

    private void startCloudStatusMonitor() {
        stopCloudStatusMonitor();
        if (appState == null || lanClient == null || lanConfig == null || !lanConfig.isClient()) {
            if (appState != null) appState.updateCloudStatus(false, "", 0L);
            return;
        }

        appState.updateCloudStatus(false, lanClient.baseUrl(), 0L);
        cloudStatusExec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pp-cloud-status");
            t.setDaemon(true);
            return t;
        });
        cloudStatusExec.scheduleAtFixedRate(() -> {
            try {
                ServerStatusDto status = lanClient.fetchStatus();
                boolean ok = status != null && "ok".equalsIgnoreCase(status.status());
                long startedAt = status == null ? 0L : status.startedAt();
                String url = status == null ? lanClient.baseUrl() : status.publicUrl();
                if (url == null || url.isBlank()) url = lanClient.baseUrl();
                appState.updateCloudStatus(ok, url, startedAt);
            } catch (Exception e) {
                appState.updateCloudStatus(false, lanClient.baseUrl(), 0L);
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    private void stopCloudStatusMonitor() {
        if (cloudStatusExec != null) {
            cloudStatusExec.shutdownNow();
            cloudStatusExec = null;
        }
    }

    private void showStartupNotice(String title, String message) {
        Runnable show = () -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(title);
            alert.setContentText(message == null ? "" : message);
            alert.show();
        };
        if (Platform.isFxApplicationThread()) show.run();
        else Platform.runLater(show);
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

    private void loadIcon(Stage stage, String path) {
        if (stage == null || path == null) return;
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                System.err.println("[UI] Missing icon resource: " + path);
                return;
            }
            stage.getIcons().add(new Image(in));
        } catch (Exception e) {
            System.err.println("[UI] Failed to load icon: " + path + " (" + e.getMessage() + ")");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
