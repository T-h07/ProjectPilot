package com.projectpilot.ui.pages.auth;

import com.projectpilot.lan.LanConfig;
import com.projectpilot.lan.LanDiscovery;
import com.projectpilot.lan.LanHost;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

public final class LanSetupPage extends BorderPane {

    private final LanConfig defaults;
    private final Consumer<LanConfig> onChoose;

    private final ListView<LanHost> hosts = new ListView<>();
    private final TextField manualHost = new TextField();
    private final TextField cloudHost = new TextField();
    private final Label status = new Label("");

    public LanSetupPage(LanConfig defaults, Consumer<LanConfig> onChoose) {
        this.defaults = defaults;
        this.onChoose = onChoose;

        setPadding(new Insets(24));

        Label title = new Label("Connect");
        title.getStyleClass().add("page-title");

        Label sub = new Label("Choose how to start ProjectPilot.");
        sub.getStyleClass().add("muted");

        status.getStyleClass().add("muted");

        VBox header = new VBox(6, title, sub, status);
        setTop(header);

        Button localBtn = new Button("Use local only");
        localBtn.getStyleClass().add("secondary");
        localBtn.setOnAction(e -> chooseLocal());

        Button hostBtn = new Button("Host on this laptop");
        hostBtn.getStyleClass().add("primary");
        hostBtn.setOnAction(e -> chooseHost());

        HBox modeRow = new HBox(10, localBtn, hostBtn);
        modeRow.setAlignment(Pos.CENTER_LEFT);

        Label cloudTitle = new Label("Cloud mode");
        cloudTitle.getStyleClass().add("section-title");

        Label cloudHint = new Label("Connect using a public URL (HTTPS or HTTP with port). Use ?ws=8091 if WS is separate.");
        cloudHint.getStyleClass().add("muted");

        cloudHost.setPromptText("https://pilot.yourdomain.com or http://host:8090");

        Button cloudJoin = new Button("Connect to Cloud");
        cloudJoin.getStyleClass().add("primary");
        cloudJoin.setOnAction(e -> joinCloud());

        VBox cloudCard = new VBox(10, cloudTitle, cloudHint, cloudHost, cloudJoin);
        cloudCard.getStyleClass().add("card");
        cloudCard.setPadding(new Insets(12));

        Label joinTitle = new Label("Join a host");
        joinTitle.getStyleClass().add("section-title");

        hosts.setPrefHeight(180);
        hosts.setPlaceholder(new Label("No hosts found yet"));

        manualHost.setPromptText("Enter LAN host IP (optional) e.g. 192.168.1.50");

        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> refreshHosts());

        Button join = new Button("Join");
        join.getStyleClass().add("primary");
        join.setOnAction(e -> joinSelected());

        HBox joinRow = new HBox(10, join, refresh);
        joinRow.setAlignment(Pos.CENTER_LEFT);

        VBox joinCard = new VBox(10, joinTitle, hosts, manualHost, joinRow);
        joinCard.getStyleClass().add("card");
        joinCard.setPadding(new Insets(12));
        VBox.setVgrow(hosts, Priority.ALWAYS);

        VBox body = new VBox(16, modeRow, cloudCard, joinCard);
        setCenter(body);

        refreshHosts();
    }

    private void refreshHosts() {
        status.setText("Scanning for hosts...");
        Task<List<LanHost>> task = new Task<>() {
            @Override
            protected List<LanHost> call() {
                return LanDiscovery.discover(800);
            }
        };
        task.setOnSucceeded(e -> {
            hosts.getItems().setAll(task.getValue());
            status.setText("");
        });
        task.setOnFailed(e -> status.setText("Discovery failed."));
        new Thread(task, "pp-lan-discover").start();
    }

    private void joinSelected() {
        LanHost selected = hosts.getSelectionModel().getSelectedItem();
        String manual = manualHost.getText() == null ? "" : manualHost.getText().trim();
        String host = selected != null ? selected.baseUrl() : manual;
        if (host == null || host.isBlank()) {
            status.setText("Select a host or enter an IP.");
            return;
        }

        int port = defaults.port();
        int wsPort = defaults.wsPort();
        int poll = defaults.pollMs();

        onChoose.accept(LanConfig.forClient(host, port, wsPort, poll));
    }

    private void joinCloud() {
        String raw = cloudHost.getText() == null ? "" : cloudHost.getText().trim();
        if (raw.isBlank()) {
            status.setText("Enter your cloud URL.");
            return;
        }
        LanConfig cfg = LanConfig.forCloud(raw, defaults.pollMs());
        if (cfg.baseUrl() == null || cfg.baseUrl().isBlank()) {
            status.setText("Invalid cloud URL.");
            return;
        }
        onChoose.accept(cfg);
    }

    private void chooseHost() {
        onChoose.accept(LanConfig.forHost(defaults.port(), defaults.wsPort(), defaults.pollMs()));
    }

    private void chooseLocal() {
        onChoose.accept(LanConfig.forLocal(defaults.port(), defaults.wsPort(), defaults.pollMs()));
    }
}
