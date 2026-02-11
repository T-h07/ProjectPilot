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
import com.projectpilot.ui.components.WindowChrome;
import com.projectpilot.ui.pages.*;
import com.projectpilot.ui.pages.admin.AdminPage;
import com.projectpilot.lan.LanDiscovery;
import com.projectpilot.ui.pages.auth.LanSetupPage;
import com.projectpilot.ui.pages.auth.LoginPage;
import com.projectpilot.ui.pages.auth.SetupAdminPage;
import com.projectpilot.util.OwnerProfile;
import com.projectpilot.util.UserSettingsStore;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.InputStream;
import com.projectpilot.util.AppLog;
import java.util.concurrent.Executors;
import javafx.collections.ListChangeListener;
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
import com.projectpilot.model.Project;



public class Main extends Application {

    private DbManager db;
    private AuthService localAuth;
    private AuthProvider auth;

    private InMemoryStore store;
    private AppState appState;

    private Scene scene;
    private WindowChrome chrome;

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
    private final UserSettingsStore settingsStore = new UserSettingsStore();
    private boolean uiBindingsReady;

    @Override
    public void start(Stage stage) {
        lanConfig = LanConfig.fromSystem();

        loadFont("/fonts/Inter-Regular.ttf", 12);
        loadFont("/fonts/Inter-SemiBold.ttf", 12);

        stage.initStyle(StageStyle.UNDECORATED);

        chrome = new WindowChrome(stage);
        scene = new Scene(chrome, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());

        stage.setTitle("ProjectPilot");
        stage.setResizable(true);
        loadIcon(stage, "/icons/app.png");
        stage.setScene(scene);

        stage.setOnCloseRequest(e -> shutdownServices());
        stage.show();

        if (lanConfig.isClient() || lanConfig.isHost()) bootstrapMode(lanConfig);
        else showLanSetup();
    }

    private void showLogin() {
        var root = new LoginPage(auth, this::onLoginSuccess);
        root.getStyleClass().add("pp-root");
        chrome.setContent(root);
    }

    private void showLanSetup() {
        var root = new LanSetupPage(lanConfig, this::bootstrapMode);
        root.getStyleClass().add("pp-root");
        chrome.setContent(root);
    }

    private void showSetup() {
        var root = new SetupAdminPage(auth, this::onLoginSuccess);
        root.getStyleClass().add("pp-root");
        chrome.setContent(root);
    }

    private void bootstrapMode(LanConfig config) {
        lanConfig = config == null ? LanConfig.fromSystem() : config;
        stopDiscovery();
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
            AppLog.warn("db", "DB = " + db.describe());
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
                uiBindingsReady = false;
                appState.setSession(session);
                appState.setLanToken(lanClient.token());
                appState.setLanBaseUrl(lanConfig.baseUrl());

                    // Clear selected project if it is removed from the store (active list)
                    ListChangeListener<Project> clearSelectedProjectOnRemove = ch -> {
                        while (ch.next()) {
                            if (ch.wasRemoved()) {
                                for (Project removed : ch.getRemoved()) {
                                    try {
                                        if (removed != null && removed == appState.getSelectedProject()) appState.setSelectedProject(null);
                                    } catch (Exception e) {
                                        AppLog.warn("main", "Failed clearing selectedProject after removal: " + (e == null ? "" : e.getMessage()));
                                    }
                                }
                            }
                        }
                    };

                    // Attach same listener to both active and history project lists
                    store.getProjects().addListener(clearSelectedProjectOnRemove);
                    store.getHistoryProjects().addListener(clearSelectedProjectOnRemove);

                LanWsClient wsClient = new LanWsClient(lanConfig.wsUrl(), () -> {
                    if (lanSync != null) lanSync.requestRefresh();
                });
                lanSync = new LanSyncService(remote, lanClient, appState, lanConfig.pollMs(), wsClient);
                lanSync.start();
                chatService = new LanChatService(lanClient);
            } else {
                initHostStoreIfNeeded();

                appState = new AppState();
                uiBindingsReady = false;
                appState.setSession(session);

                seedSampleDataIfEmpty(session);

                // Pick first project the user is allowed to see
                var initial = store.getProjects().stream()
                        .filter(p -> policy.canViewProject(appState, p))
                        .findFirst()
                        .orElse(null);

                appState.setSelectedProject(initial);
            }

            applyUserSettings(session);
            bindUiPreferences();
            updateHostStatusForMode();
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
            router.register(PageId.ACTIVITY, () -> new ActivityTimelinePage(store, appState));
            router.register(PageId.PROJECTS, () -> new ProjectsPage(store, appState));
            router.register(PageId.PROJECT_OVERVIEW, () -> new ProjectOverviewPage(store, appState));
            router.register(PageId.TASKS, () -> new TasksPage(store, appState));
            router.register(PageId.GANTT, () -> new GanttPage(store, appState));
            router.register(PageId.CALENDAR, () -> new CalendarPage(store, appState));
            router.register(PageId.RESOURCES, () -> new ResourcesPage(store, appState));
            router.register(PageId.NOTES, () -> new NotesPage(store, appState));
            router.register(PageId.TEAM, () -> new TeamPage(store, appState));
            router.register(PageId.MEETINGS, () -> new MeetingsPage(appState));
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
            chrome.setContent(appRoot);

            if (lanConfig.isHost() && lanServer == null && appState.isAdmin()) {
                startLanHostServices();
            }
        } catch (Exception e) {
            AppLog.warn("ui", "Login init failed: " + (e == null ? "" : e.getMessage()));
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
                AppLog.warn("lan", "Host start skipped: store not ready");
                return;
            }
            if (localAuth == null) {
                AppLog.warn("lan", "Host start skipped: auth not ready");
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
            registerHostToken();
            updateLanBaseUrl();
            if (appState != null) {
                appState.updateHostingStatus(true, lanConfig.port(), lanConfig.wsPort(),
                        System.currentTimeMillis(), 0, "host");
            }
            AppLog.warn("lan", "LAN HOST listening on port " + lanConfig.port() + " (ws " + lanConfig.wsPort() + ")");
        } catch (Throwable e) {
            AppLog.warn("lan", "Host startup failed: " + (e == null ? "" : e.getMessage()));
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
        } catch (Exception e) { AppLog.warn("lan", "Failed to stop lanServer: " + (e == null ? "" : e.getMessage())); }
        try {
            if (lanBroadcaster != null) lanBroadcaster.stop();
        } catch (Exception e) { AppLog.warn("lan", "Failed to stop lanBroadcaster: " + (e == null ? "" : e.getMessage())); }
        try {
            if (lanWsServer != null) lanWsServer.stop();
        } catch (Exception e) { AppLog.warn("lan", "Failed to stop lanWsServer: " + (e == null ? "" : e.getMessage())); }
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
            appState.setLanToken("");
            appState.setLanBaseUrl("");
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
            } catch (Exception e) {
                AppLog.warn("lan", "Discovery stop failed: " + (e == null ? "" : e.getMessage()));
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
            updateLanBaseUrl();
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
        updateLanBaseUrl();
    }

    private void startLanDiscovery() {
        try {
            if (lanDiscovery == null) {
                lanDiscovery = LanDiscovery.startResponder(lanConfig.port(), lanConfig.wsPort());
            }
        } catch (Exception e) {
            AppLog.warn("lan", "Discovery responder failed: " + (e == null ? "" : e.getMessage()));
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

    private void registerHostToken() {
        if (lanSessions == null || appState == null || appState.getSession() == null) return;
        String current = appState.getLanToken();
        if (current != null && !current.isBlank() && lanSessions.get(current) != null) return;
        String token = java.util.UUID.randomUUID().toString();
        lanSessions.put(token, appState.getSession());
        appState.setLanToken(token);
    }

    private void updateLanBaseUrl() {
        if (appState == null) return;
        if (lanConfig.isClient()) {
            appState.setLanBaseUrl(lanConfig.baseUrl());
            return;
        }
        if (!lanConfig.isHost()) {
            appState.setLanBaseUrl("");
            return;
        }
        String host = localIpv4Addresses().stream().findFirst().orElse("127.0.0.1");
        appState.setLanBaseUrl("http://" + host + ":" + lanConfig.port());
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
                AppLog.warn("ui", "Missing font resource: " + path);
                return;
            }
            Font.loadFont(in, size);
        } catch (Exception e) {
            AppLog.warn("ui", "Failed to load font: " + path + " (" + (e == null ? "" : e.getMessage()) + ")");
        }
    }

    private void loadIcon(Stage stage, String path) {
        if (stage == null || path == null) return;
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                AppLog.warn("ui", "Missing icon resource: " + path);
                return;
            }
            stage.getIcons().add(new Image(in));
        } catch (Exception e) {
            AppLog.warn("ui", "Failed to load icon: " + path + " (" + (e == null ? "" : e.getMessage()) + ")");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    private void applyUserSettings(UserSession session) {
        if (appState == null) return;
        String userId = session == null ? null : session.id();
        appState.applySettings(settingsStore.load(userId));
        applyThemeClass(appState.getTheme());
        applyDensityClass(appState.getDensity());
        applyOwnerClass();
    }

    private void bindUiPreferences() {
        if (uiBindingsReady || appState == null) return;
        uiBindingsReady = true;

        appState.themeProperty().addListener((obs, o, n) -> applyThemeClass(n));
        appState.densityProperty().addListener((obs, o, n) -> applyDensityClass(n));
        appState.sessionProperty().addListener((obs, o, n) -> applyOwnerClass());

        applyThemeClass(appState.getTheme());
        applyDensityClass(appState.getDensity());
        applyOwnerClass();
    }

    private void applyThemeClass(String value) {
        if (chrome == null) return;
        chrome.getStyleClass().removeAll("theme-default", "theme-graphite", "theme-light");
        String v = value == null ? "default" : value.trim().toLowerCase();
        if ("light".equals(v)) {
            chrome.getStyleClass().add("theme-light");
        } else if ("graphite".equals(v)) {
            chrome.getStyleClass().add("theme-graphite");
        } else {
            chrome.getStyleClass().add("theme-default");
        }
    }

    private void applyDensityClass(String value) {
        if (chrome == null) return;
        chrome.getStyleClass().removeAll("density-compact", "density-comfortable");
        String v = value == null ? "comfortable" : value.trim().toLowerCase();
        chrome.getStyleClass().add("compact".equals(v) ? "density-compact" : "density-comfortable");
    }

    private void applyOwnerClass() {
        if (chrome == null || appState == null) return;
        boolean owner = OwnerProfile.isOwnerUser(appState.getSession());
        if (owner) {
            if (!chrome.getStyleClass().contains("owner-mode")) chrome.getStyleClass().add("owner-mode");
        } else {
            chrome.getStyleClass().remove("owner-mode");
        }
    }

    private static java.util.List<String> localIpv4Addresses() {
        java.util.TreeSet<String> out = new java.util.TreeSet<>();
        try {
            java.util.Enumeration<java.net.NetworkInterface> ifaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                java.net.NetworkInterface ni = ifaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                java.util.Enumeration<java.net.InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress addr = addrs.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                        out.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception e) { AppLog.warn("main", "localIpv4Addresses failed: " + (e == null ? "" : e.getMessage())); }
        return new java.util.ArrayList<>(out);
    }
}
