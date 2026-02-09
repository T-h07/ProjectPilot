package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.UUID;

public final class MeetingsPage extends VBox {

    private final AppState appState;
    private final Label status = new Label();
    private final TextField roomCode = new TextField();
    private final TextField joinCode = new TextField();
    private final TextField linkPreview = new TextField();

    public MeetingsPage(AppState appState) {
        this.appState = appState;

        setPadding(new Insets(16));
        setSpacing(16);

        Label title = new Label("Meetings");
        title.getStyleClass().add("page-title");

        Label subtitle = new Label("LAN-only calls. The host must be running.");
        subtitle.getStyleClass().add("muted");

        status.getStyleClass().add("muted");

        VBox header = new VBox(6, title, subtitle, status);

        VBox startCard = buildStartCard();
        VBox joinCard = buildJoinCard();

        getChildren().addAll(header, startCard, joinCard);

        appState.hostingProperty().addListener((obs, o, n) -> refreshStatus());
        appState.hostModeProperty().addListener((obs, o, n) -> refreshStatus());
        appState.lanBaseUrlProperty().addListener((obs, o, n) -> refreshStatus());
        refreshStatus();
    }

    private VBox buildStartCard() {
        Label title = new Label("Start a meeting");
        title.getStyleClass().add("section-title");

        roomCode.setPromptText("Room code");
        roomCode.setPrefWidth(200);

        Button generateBtn = new Button("Generate");
        generateBtn.getStyleClass().add("secondary");
        generateBtn.setOnAction(e -> roomCode.setText(newRoomCode()));

        Button startBtn = new Button("Start meeting");
        startBtn.getStyleClass().add("primary");
        startBtn.setOnAction(e -> openMeeting(roomCode.getText(), true));

        HBox row = new HBox(10, roomCode, generateBtn, startBtn);
        row.setAlignment(Pos.CENTER_LEFT);

        linkPreview.setEditable(false);
        linkPreview.setPromptText("Meeting link will appear here");
        HBox.setHgrow(linkPreview, Priority.ALWAYS);

        Button copyBtn = new Button("Copy link");
        copyBtn.getStyleClass().add("subtle");
        copyBtn.setOnAction(e -> copyMeetingLink());

        HBox linkRow = new HBox(10, linkPreview, copyBtn);
        linkRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(12, title, row, linkRow);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));
        return card;
    }

    private VBox buildJoinCard() {
        Label title = new Label("Join a meeting");
        title.getStyleClass().add("section-title");

        joinCode.setPromptText("Enter room code");
        joinCode.setPrefWidth(220);

        Button joinBtn = new Button("Join meeting");
        joinBtn.getStyleClass().add("primary");
        joinBtn.setOnAction(e -> openMeeting(joinCode.getText(), false));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(10, joinCode, joinBtn, spacer);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(12, title, row);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));
        return card;
    }

    private void refreshStatus() {
        String mode = appState == null ? "local" : appState.getHostMode();
        boolean hosting = appState != null && appState.isHosting();
        String base = appState == null ? "" : appState.getLanBaseUrl();

        if ("client".equals(mode)) {
            status.setText(base == null || base.isBlank()
                    ? "Client mode: host URL missing."
                    : ("Client mode: connected to " + base));
            return;
        }

        if (hosting) {
            status.setText("Hosting meetings on LAN.");
        } else {
            status.setText("Start hosting to allow LAN meetings.");
        }
    }

    private void openMeeting(String codeRaw, boolean start) {
        String code = safe(codeRaw);
        if (code.isBlank() && start) {
            code = newRoomCode();
            roomCode.setText(code);
        }
        if (code.isBlank()) {
            status.setText("Enter a room code.");
            return;
        }

        String url = buildMeetingUrl(code, start);
        if (url == null) {
            status.setText("Meeting URL unavailable. Start hosting or connect to a host.");
            return;
        }

        linkPreview.setText(url);
        if (start) roomCode.setText(code.toUpperCase(Locale.ROOT));

        try {
            Desktop.getDesktop().browse(new URI(url));
            status.setText("Opening meeting in your browser.");
        } catch (Exception e) {
            status.setText("Failed to open browser: " + e.getMessage());
        }
    }

    private void copyMeetingLink() {
        String url = linkPreview.getText();
        if (url == null || url.isBlank()) {
            status.setText("No meeting link yet.");
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(url);
        Clipboard.getSystemClipboard().setContent(content);
        status.setText("Meeting link copied.");
    }

    private String buildMeetingUrl(String roomCode, boolean host) {
        if (appState == null) return null;

        String base = appState.getLanBaseUrl();
        if (base == null || base.isBlank()) {
            if (appState.isHosting()) {
                base = defaultHostBaseUrl();
                appState.setLanBaseUrl(base);
            }
        }
        if (base == null || base.isBlank()) return null;

        String token = safe(appState.getLanToken());
        if (token.isBlank()) return null;
        String wsPort = String.valueOf(appState.getHostWsPort());
        String name = displayName();

        String url = base + "/meet?room=" + encode(roomCode)
                + "&name=" + encode(name)
                + "&token=" + encode(token)
                + "&wsPort=" + encode(wsPort);
        if (host) {
            url += "&host=1";
        }
        return url;
    }

    private String displayName() {
        if (appState == null || appState.getSession() == null) return "Guest";
        String dn = safe(appState.getSession().displayName());
        if (!dn.isBlank()) return dn;
        String un = safe(appState.getSession().username());
        return un.isBlank() ? "Guest" : un;
    }

    private static String encode(String v) {
        if (v == null) return "";
        return java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String defaultHostBaseUrl() {
        int port = appState == null ? 8090 : Math.max(1, appState.getHostPort());
        List<String> ips = localIpv4Addresses();
        if (!ips.isEmpty()) {
            return "http://" + ips.get(0) + ":" + port;
        }
        return "http://127.0.0.1:" + port;
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
        } catch (Exception ignored) {}
        return new ArrayList<>(out);
    }

    private static String safe(String v) {
        return v == null ? "" : v.trim();
    }

    private static String newRoomCode() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }
}
