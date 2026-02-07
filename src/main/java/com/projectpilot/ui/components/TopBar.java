package com.projectpilot.ui.components;

import com.projectpilot.cloud.CloudConfig;
import com.projectpilot.cloud.DuckDnsAutoUpdater;
import com.projectpilot.cloud.DuckDnsClient;
import com.projectpilot.cloud.PublicIpService;
import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.data.db.DbStore;
import com.projectpilot.data.db.auth.UserAdminService;
import com.projectpilot.data.db.auth.UserSession;
import com.projectpilot.service.NotificationService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeSet;
import java.net.URL;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class TopBar extends HBox {

    private static final DuckDnsAutoUpdater DUCKDNS_UPDATER = DuckDnsAutoUpdater.instance();

    public TopBar(InMemoryStore store, AppState appState, NotificationService notifications) {
        setPadding(new Insets(12));
        setSpacing(12);
        getStyleClass().add("topbar");

        Label title = new Label("ProjectPilot");
        title.getStyleClass().add("app-title");

        ProjectPicker picker = new ProjectPicker(store, appState);

        NotificationBellButton bell = new NotificationBellButton(notifications);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label userName = new Label();
        userName.getStyleClass().add("topbar-user");

        Label initials = new Label();
        initials.getStyleClass().add("profile-initials");

        StackPane avatar = new StackPane(initials);
        avatar.getStyleClass().add("profile-avatar");

        Button profileBtn = new Button();
        profileBtn.getStyleClass().add("profile-btn");
        profileBtn.setGraphic(avatar);

        Label hostLabel = new Label("Hosting");
        hostLabel.getStyleClass().add("host-label");

        Region hostDot = new Region();
        hostDot.getStyleClass().add("host-dot");

        HBox hostGraphic = new HBox(6, hostDot, hostLabel);
        hostGraphic.setAlignment(Pos.CENTER);

        Button hostBtn = new Button();
        hostBtn.getStyleClass().add("host-btn");
        hostBtn.setGraphic(hostGraphic);

        Tooltip hostTip = new Tooltip();
        hostBtn.setTooltip(hostTip);

        Label cloudLabel = new Label("Cloud");
        cloudLabel.getStyleClass().add("cloud-label");

        Region cloudDot = new Region();
        cloudDot.getStyleClass().add("cloud-dot");

        HBox cloudGraphic = new HBox(6, cloudDot, cloudLabel);
        cloudGraphic.setAlignment(Pos.CENTER);

        Button cloudBtn = new Button();
        cloudBtn.getStyleClass().add("cloud-btn");
        cloudBtn.setGraphic(cloudGraphic);

        Tooltip cloudTip = new Tooltip();
        cloudBtn.setTooltip(cloudTip);

        ContextMenu cloudMenu = new ContextMenu();
        cloudMenu.getStyleClass().addAll("pp-root", "profile-menu-popup");
        cloudMenu.setOnShowing(e -> ensurePopupStyles(cloudMenu));
        cloudMenu.setOnShown(e -> ensurePopupStyles(cloudMenu));

        ContextMenu hostMenu = new ContextMenu();
        hostMenu.getStyleClass().addAll("pp-root", "profile-menu-popup");
        hostMenu.setOnShowing(e -> ensurePopupStyles(hostMenu));
        hostMenu.setOnShown(e -> ensurePopupStyles(hostMenu));

        ContextMenu profileMenu = new ContextMenu();
        profileMenu.getStyleClass().addAll("pp-root", "profile-menu-popup");
        profileMenu.setOnShowing(e -> ensurePopupStyles(profileMenu));
        profileMenu.setOnShown(e -> ensurePopupStyles(profileMenu));

        Runnable syncUser = () -> {
            UserSession s = appState.getSession();
            String display = displayName(s);
            userName.setText(display);
            initials.setText(initials(display));
        };
        syncUser.run();
        appState.sessionProperty().addListener((obs, o, n) -> syncUser.run());

        Runnable syncHost = () -> {
            boolean hosting = appState != null && appState.isHosting();
            hostDot.getStyleClass().removeAll("host-dot-online", "host-dot-offline");
            hostDot.getStyleClass().add(hosting ? "host-dot-online" : "host-dot-offline");
            hostTip.setText(hosting ? "Hosting on LAN" : "Local only");
        };
        syncHost.run();
        appState.hostingProperty().addListener((obs, o, n) -> syncHost.run());

        Runnable syncCloud = () -> {
            boolean show = appState != null && "client".equalsIgnoreCase(appState.getHostMode());
            cloudBtn.setVisible(show);
            cloudBtn.setManaged(show);
            boolean connected = appState != null && appState.isCloudConnected();
            cloudDot.getStyleClass().removeAll("cloud-dot-online", "cloud-dot-offline");
            cloudDot.getStyleClass().add(connected ? "cloud-dot-online" : "cloud-dot-offline");
            cloudTip.setText(connected ? "Cloud connected" : "Cloud offline");
        };
        syncCloud.run();
        appState.hostModeProperty().addListener((obs, o, n) -> syncCloud.run());
        appState.cloudConnectedProperty().addListener((obs, o, n) -> syncCloud.run());

        Runnable syncAdmin = () -> {
            boolean show = appState != null && appState.isAdmin();
            hostBtn.setVisible(show);
            hostBtn.setManaged(show);
        };
        syncAdmin.run();
        appState.sessionProperty().addListener((obs, o, n) -> syncAdmin.run());

        hostBtn.setOnAction(e -> toggleHostMenu(hostMenu, hostBtn, appState));
        cloudBtn.setOnAction(e -> toggleCloudMenu(cloudMenu, cloudBtn, appState));
        profileBtn.setOnAction(e -> toggleProfileMenu(profileMenu, profileBtn, appState, store));

        HBox userBox = new HBox(8, userName, profileBtn);
        userBox.setAlignment(Pos.CENTER_RIGHT);
        userBox.getStyleClass().add("topbar-userbox");

        getChildren().addAll(title, picker, bell, spacer, cloudBtn, hostBtn, userBox);

        Runnable syncDuckDns = () -> syncDuckDnsAuto(appState);
        syncDuckDns.run();
        appState.hostingProperty().addListener((obs, o, n) -> syncDuckDns.run());
    }

    // Backward compatible constructor (optional)
    public TopBar(InMemoryStore store, AppState appState) {
        this(store, appState, new NotificationService(store, appState));
    }

    private static void toggleProfileMenu(ContextMenu menu, Button anchor, AppState appState, InMemoryStore store) {
        if (menu.isShowing()) {
            menu.hide();
            return;
        }

        menu.getItems().clear();

        UserSession s = appState == null ? null : appState.getSession();
        if (s == null) return;

        String display = displayName(s);
        String username = safe(s.username());
        String role = s.globalRole() == null ? "" : s.globalRole().name();
        String id = safe(s.id());
        String email = lookupEmail(store, id);

        VBox box = new VBox(6);
        box.getStyleClass().add("profile-menu-card");

        Label title = new Label(display);
        title.getStyleClass().add("profile-title");
        box.getChildren().add(title);

        if (!username.isBlank()) box.getChildren().add(profileLine("Username", username));
        if (!email.isBlank()) box.getChildren().add(profileLine("Email", email));
        if (!role.isBlank()) box.getChildren().add(profileLine("Role", role));
        if (!id.isBlank()) {
            String shortId = shortId(id);
            Label idLine = profileLine("ID", shortId);
            if (!shortId.equals(id)) idLine.setTooltip(new Tooltip(id));
            box.getChildren().add(idLine);
        }

        CustomMenuItem info = new CustomMenuItem(box, false);
        menu.getItems().add(info);

        menu.show(anchor, Side.BOTTOM, 0, 6);
    }

    private static void toggleHostMenu(ContextMenu menu, Button anchor, AppState appState) {
        if (menu.isShowing()) {
            menu.hide();
            return;
        }

        menu.getItems().clear();

        String mode = appState == null ? "local" : appState.getHostMode();
        String status = switch (mode) {
            case "host" -> "Hosting";
            case "client" -> "Client";
            default -> "Local only";
        };

        VBox box = new VBox(6);
        box.getStyleClass().add("profile-menu-card");

        Label title = new Label("LAN Hosting");
        title.getStyleClass().add("profile-title");
        box.getChildren().add(title);

        box.getChildren().add(profileLine("Status", status));

        if ("host".equals(mode) && appState != null) {
            String ips = String.join(", ", localIpv4Addresses());
            if (ips.isBlank()) ips = "Unknown";
            box.getChildren().add(profileLine("IP", ips));
            box.getChildren().add(profileLine("HTTP port", String.valueOf(appState.getHostPort())));
            box.getChildren().add(profileLine("WS port", String.valueOf(appState.getHostWsPort())));
            box.getChildren().add(profileLine("Connected", String.valueOf(appState.getHostConnections())));
            box.getChildren().add(profileLine("Uptime", formatUptime(appState.getHostStartedAt())));
        } else {
            box.getChildren().add(profileLine("Hosting", "Off"));
        }

        box.getChildren().add(new Label(""));
        Label cloudTitle = new Label("Cloud Tools");
        cloudTitle.getStyleClass().add("profile-subtitle");
        box.getChildren().add(cloudTitle);

        CloudConfig cfg = CloudConfig.load();

        Label publicIpLine = profileLine("Public IP", "Loading...");
        Button refreshIp = new Button("Refresh");
        refreshIp.getStyleClass().add("secondary");
        HBox publicRow = new HBox(8, publicIpLine, refreshIp);
        publicRow.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().add(publicRow);

        TextField duckDomain = new TextField(cfg.duckDomain());
        duckDomain.setPromptText("DuckDNS domain (projectpilot)");
        duckDomain.getStyleClass().add("profile-input");

        PasswordField duckToken = new PasswordField();
        duckToken.setText(cfg.duckToken());
        duckToken.setPromptText("DuckDNS token");
        duckToken.getStyleClass().add("profile-input");

        Label urlLine = profileLine("Cloud URL", cloudUrl(duckDomain.getText(), appState));

        Button copyUrl = new Button("Copy URL");
        copyUrl.getStyleClass().add("secondary");
        HBox urlRow = new HBox(8, urlLine, copyUrl);
        urlRow.setAlignment(Pos.CENTER_LEFT);

        CheckBox autoUpdate = new CheckBox("Auto-update every 5 min");
        autoUpdate.setSelected(cfg.autoUpdate());

        Label hint = new Label("Public test may fail on same Wi-Fi (NAT loopback).");
        hint.getStyleClass().add("profile-hint");

        Label statusLine = new Label("");
        statusLine.getStyleClass().add("profile-line");

        Button saveBtn = new Button("Save");
        saveBtn.getStyleClass().add("secondary");
        Button updateBtn = new Button("Update DuckDNS");
        updateBtn.getStyleClass().add("secondary");
        Button testBtn = new Button("Test public health");
        testBtn.getStyleClass().add("secondary");

        HBox actionRow = new HBox(8, saveBtn, updateBtn, testBtn);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        box.getChildren().addAll(duckDomain, duckToken, urlRow, autoUpdate, actionRow, hint, statusLine);

        Runnable saveConfig = () -> {
            cfg.setDuckDomain(duckDomain.getText());
            cfg.setDuckToken(duckToken.getText());
            cfg.setAutoUpdate(autoUpdate.isSelected());
            cfg.save();
            syncDuckDnsAuto(appState);
            statusLine.setText("Saved.");
        };

        refreshIp.setOnAction(e -> runAsync("pp-public-ip", () -> {
            String ip = PublicIpService.fetch();
            Platform.runLater(() -> publicIpLine.setText("Public IP: " + (ip.isBlank() ? "Unavailable" : ip)));
        }));

        duckDomain.textProperty().addListener((obs, o, n) -> urlLine.setText("Cloud URL: " + cloudUrl(n, appState)));

        copyUrl.setOnAction(e -> {
            String url = cloudUrl(duckDomain.getText(), appState);
            if (url.isBlank()) {
                statusLine.setText("Set DuckDNS domain first.");
                return;
            }
            ClipboardContent content = new ClipboardContent();
            content.putString(url);
            Clipboard.getSystemClipboard().setContent(content);
            statusLine.setText("Copied URL.");
        });

        saveBtn.setOnAction(e -> saveConfig.run());
        autoUpdate.setOnAction(e -> saveConfig.run());

        updateBtn.setOnAction(e -> runAsync("pp-duckdns-update", () -> {
            String result = DuckDnsClient.update(duckDomain.getText(), duckToken.getText());
            Platform.runLater(() -> statusLine.setText("DuckDNS: " + result));
        }));

        testBtn.setOnAction(e -> runAsync("pp-cloud-test", () -> {
            String url = cloudUrl(duckDomain.getText(), appState);
            String result = testHealth(url);
            Platform.runLater(() -> statusLine.setText(result));
        }));

        refreshIp.fire();

        CustomMenuItem info = new CustomMenuItem(box, false);
        menu.getItems().add(info);

        menu.show(anchor, Side.BOTTOM, 0, 6);
    }

    private static void toggleCloudMenu(ContextMenu menu, Button anchor, AppState appState) {
        if (menu.isShowing()) {
            menu.hide();
            return;
        }

        menu.getItems().clear();

        boolean connected = appState != null && appState.isCloudConnected();
        String url = appState == null ? "" : safe(appState.getCloudUrl());
        String uptime = connected && appState != null ? formatUptime(appState.getCloudStartedAt()) : "-";

        VBox box = new VBox(6);
        box.getStyleClass().add("profile-menu-card");

        Label title = new Label("Cloud Status");
        title.getStyleClass().add("profile-title");
        box.getChildren().add(title);

        box.getChildren().add(profileLine("Status", connected ? "Connected" : "Offline"));
        box.getChildren().add(profileLine("URL", url.isBlank() ? "-" : url));
        box.getChildren().add(profileLine("Uptime", connected ? uptime : "-"));

        CustomMenuItem info = new CustomMenuItem(box, false);
        menu.getItems().add(info);

        menu.show(anchor, Side.BOTTOM, 0, 6);
    }

    private static Label profileLine(String label, String value) {
        Label out = new Label(label + ": " + value);
        out.getStyleClass().add("profile-line");
        return out;
    }

    private static String displayName(UserSession s) {
        if (s == null) return "User";
        String dn = safe(s.displayName());
        if (!dn.isBlank()) return dn;
        String un = safe(s.username());
        return un.isBlank() ? "User" : un;
    }

    private static String initials(String name) {
        String s = safe(name);
        if (s.isBlank()) return "?";
        String[] parts = s.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase();
        String first = parts[0].substring(0, 1);
        String last = parts[parts.length - 1].substring(0, 1);
        return (first + last).toUpperCase();
    }

    private static String lookupEmail(InMemoryStore store, String userId) {
        if (userId == null || userId.isBlank()) return "";
        if (!(store instanceof DbStore ds)) return "";

        try {
            for (UserAdminService.UserRow row : ds.listUsers()) {
                if (userId.equals(row.id())) {
                    String email = row.email();
                    return email == null ? "" : email.trim();
                }
            }
        } catch (Exception ignored) {}

        return "";
    }

    private static String shortId(String id) {
        if (id == null) return "";
        String s = id.trim();
        if (s.length() <= 12) return s;
        return s.substring(0, 8) + "..." + s.substring(s.length() - 4);
    }

    private static String safe(String v) {
        return v == null ? "" : v.trim();
    }

    private static List<String> localIpv4Addresses() {
        TreeSet<String> out = new TreeSet<>();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        out.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return new ArrayList<>(out);
    }

    private static String formatUptime(long startedAt) {
        if (startedAt <= 0) return "0m";
        Duration d = Duration.between(Instant.ofEpochMilli(startedAt), Instant.now());
        long hours = d.toHours();
        long minutes = d.toMinutes() % 60;
        long seconds = d.getSeconds() % 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }

    private static void ensurePopupStyles(ContextMenu menu) {
        if (menu == null || menu.getScene() == null) return;
        URL css = TopBar.class.getResource("/css/app.css");
        if (css == null) return;
        String url = css.toExternalForm();
        if (!menu.getScene().getStylesheets().contains(url)) {
            menu.getScene().getStylesheets().add(url);
        }
    }

    private static void syncDuckDnsAuto(AppState appState) {
        CloudConfig cfg = CloudConfig.load();
        boolean shouldRun = appState != null && appState.isHosting()
                && cfg.autoUpdate() && cfg.hasDuckCredentials();
        if (shouldRun) {
            DUCKDNS_UPDATER.start(cfg.duckDomain(), cfg.duckToken());
        } else {
            DUCKDNS_UPDATER.stop();
        }
    }

    private static void runAsync(String name, Runnable work) {
        Thread t = new Thread(work, name);
        t.setDaemon(true);
        t.start();
    }

    private static String cloudUrl(String domain, AppState appState) {
        int port = appState == null ? 8090 : appState.getHostPort();
        if (port <= 0) port = 8090;
        return DuckDnsClient.buildUrl(domain, port);
    }

    private static String testHealth(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) return "Set DuckDNS domain first.";
        try {
            String url = baseUrl.endsWith("/") ? baseUrl + "api/health" : baseUrl + "/api/health";
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return "Health: HTTP " + resp.statusCode();
            String body = resp.body() == null ? "" : resp.body().trim();
            if ("ok".equalsIgnoreCase(body)) return "Health: OK";
            return "Health: " + body;
        } catch (Exception e) {
            return "Health check failed";
        }
    }
}
