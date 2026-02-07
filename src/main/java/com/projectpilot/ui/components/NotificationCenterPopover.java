package com.projectpilot.ui.components;

import com.projectpilot.model.NotificationItem;
import com.projectpilot.service.NotificationService;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Popup;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class NotificationCenterPopover {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final NotificationService notifications;
    private final Popup popup = new Popup();

    private final VBox root = new VBox(10);
    private final VBox listBox = new VBox(8);

    private boolean unreadOnly = false;

    // filter buttons (the ones you asked to fix)
    private final ToggleButton allBtn = new ToggleButton("All");
    private final ToggleButton unreadBtn = new ToggleButton("Unread");

    public NotificationCenterPopover(NotificationService notifications) {
        this.notifications = Objects.requireNonNull(notifications);

        root.getStyleClass().add("notif-popover");
        root.setPadding(new Insets(12));
        root.setPrefWidth(420);

        Node header = buildHeader();

        ScrollPane scroller = new ScrollPane(listBox);
        scroller.setFitToWidth(true);
        scroller.setPrefViewportHeight(420);
        scroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        // ✅ kill the default white ScrollPane background (so it matches dark theme)
        scroller.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-background: transparent;" +
                        "-fx-control-inner-background: transparent;" +
                        "-fx-padding: 0;"
        );

        listBox.setFillWidth(true);
        listBox.setStyle("-fx-background-color: transparent;");

        root.getChildren().addAll(header, scroller);

        popup.getContent().add(root);
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);

        // rebuild on list changes
        this.notifications.items().addListener((ListChangeListener<NotificationItem>) c -> rebuild());

        rebuild();
    }

    public void toggle(Node anchor) {
        if (popup.isShowing()) popup.hide();
        else show(anchor);
    }

    public void show(Node anchor) {
        rebuild();
        Point2D p = anchor.localToScreen(0, anchor.getBoundsInLocal().getHeight());
        // right-align-ish under the bell
        popup.show(anchor, p.getX() - root.getPrefWidth() + 28, p.getY() + 8);
    }

    // ------------------------------------------------------------
    // Header + Filters
    // ------------------------------------------------------------

    private Node buildHeader() {
        Label title = new Label("Notifications");
        title.getStyleClass().add("notif-title");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: 800;");

        // ✅ filter buttons (styled to match dark theme)
        ToggleGroup group = new ToggleGroup();
        allBtn.setToggleGroup(group);
        unreadBtn.setToggleGroup(group);
        allBtn.setSelected(true);

        allBtn.setFocusTraversable(false);
        unreadBtn.setFocusTraversable(false);

        // apply initial styles
        applyFilterStyle(allBtn, true);
        applyFilterStyle(unreadBtn, false);

        // keep style synced with selection
        allBtn.selectedProperty().addListener((obs, ov, nv) -> applyFilterStyle(allBtn, nv));
        unreadBtn.selectedProperty().addListener((obs, ov, nv) -> applyFilterStyle(unreadBtn, nv));

        allBtn.setOnAction(e -> { unreadOnly = false; rebuild(); });
        unreadBtn.setOnAction(e -> { unreadOnly = true; rebuild(); });

        Button markAll = new Button("Mark all read");
        markAll.setFocusTraversable(false);
        markAll.getStyleClass().add("notif-markall");
        // keep it consistent with your theme (pill, subtle)
        markAll.setStyle(
                "-fx-background-radius: 12;" +
                        "-fx-border-radius: 12;" +
                        "-fx-background-color: rgba(255,255,255,0.10);" +
                        "-fx-border-color: rgba(120,90,255,0.65);" +
                        "-fx-border-width: 1;" +
                        "-fx-text-fill: white;" +
                        "-fx-padding: 8 14;"
        );
        markAll.setOnAction(e -> notifications.markAllRead());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row1 = new HBox(10, title, spacer, markAll);
        row1.setAlignment(Pos.CENTER_LEFT);

        HBox row2 = new HBox(8, allBtn, unreadBtn);
        row2.setAlignment(Pos.CENTER_LEFT);

        return new VBox(10, row1, row2);
    }

    private void applyFilterStyle(ToggleButton btn, boolean active) {
        btn.setMinHeight(30);
        btn.setPadding(new Insets(6, 12, 6, 12));

        // ✅ dark theme segmented look
        if (active) {
            btn.setStyle(
                    "-fx-background-radius: 8;" +
                            "-fx-border-radius: 8;" +
                            "-fx-background-color: rgba(120,90,255,0.30);" +
                            "-fx-border-color: rgba(120,90,255,0.85);" +
                            "-fx-border-width: 1;" +
                            "-fx-text-fill: white;"
            );
        } else {
            btn.setStyle(
                    "-fx-background-radius: 8;" +
                            "-fx-border-radius: 8;" +
                            "-fx-background-color: rgba(255,255,255,0.06);" +
                            "-fx-border-color: rgba(255,255,255,0.18);" +
                            "-fx-border-width: 1;" +
                            "-fx-text-fill: rgba(255,255,255,0.90);"
            );
        }
    }

    // ------------------------------------------------------------
    // List rendering
    // ------------------------------------------------------------

    private void rebuild() {
        List<NotificationItem> src = new ArrayList<>(notifications.items());
        if (unreadOnly) src.removeIf(NotificationItem::read);

        src.sort(Comparator.comparing(NotificationItem::at).reversed());

        listBox.getChildren().clear();

        if (src.isEmpty()) {
            Label empty = new Label(unreadOnly ? "No unread notifications." : "No notifications yet.");
            empty.getStyleClass().add("notif-empty");
            empty.setStyle("-fx-text-fill: rgba(255,255,255,0.65); -fx-padding: 12 4 0 4;");
            listBox.getChildren().add(empty);
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        List<NotificationItem> todayList = new ArrayList<>();
        List<NotificationItem> yesterdayList = new ArrayList<>();
        Map<LocalDate, List<NotificationItem>> earlier = new TreeMap<>(Comparator.reverseOrder());

        for (NotificationItem it : src) {
            LocalDate d = it.at().toLocalDate();
            if (d.equals(today)) todayList.add(it);
            else if (d.equals(yesterday)) yesterdayList.add(it);
            else earlier.computeIfAbsent(d, k -> new ArrayList<>()).add(it);
        }

        addSection("Today", todayList);
        addSection("Yesterday", yesterdayList);
        for (var e : earlier.entrySet()) addSection(e.getKey().toString(), e.getValue());
    }

    private void addSection(String title, List<NotificationItem> items) {
        if (items == null || items.isEmpty()) return;

        Label h = new Label(title);
        h.getStyleClass().add("notif-section-title");
        h.setStyle("-fx-text-fill: rgba(255,255,255,0.55); -fx-font-size: 11px; -fx-padding: 10 0 0 0;");
        listBox.getChildren().add(h);

        for (NotificationItem it : items) {
            listBox.getChildren().add(renderItem(it));
        }
    }

    private Node renderItem(NotificationItem it) {
        Label icon = new Label(it.urgent() ? "⏰" : "🧩");
        icon.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");

        Label t = new Label(safe(it.title()));
        t.setStyle("-fx-text-fill: white; -fx-font-weight: 800;");

        Label d = new Label(safe(it.detail()));
        d.setWrapText(true);
        d.setStyle("-fx-text-fill: rgba(255,255,255,0.75);");

        Label ts = new Label(it.at().toLocalTime().format(TIME_FMT));
        ts.setStyle("-fx-text-fill: rgba(255,255,255,0.45); -fx-font-size: 11px;");

        Button mark = new Button("✓");
        mark.setFocusTraversable(false);
        mark.setStyle(
                "-fx-background-radius: 10;" +
                        "-fx-background-color: rgba(255,255,255,0.10);" +
                        "-fx-text-fill: white;" +
                        "-fx-padding: 6 10;"
        );
        mark.setMinWidth(Region.USE_PREF_SIZE);
        mark.setMaxWidth(Region.USE_PREF_SIZE);
        mark.setOnAction(e -> markRead(it));

        Button view = new Button("View");
        view.setFocusTraversable(false);
        view.setStyle(
                "-fx-background-radius: 10;" +
                        "-fx-background-color: rgba(120,90,255,0.35);" +
                        "-fx-text-fill: white;" +
                        "-fx-padding: 6 12;"
        );
        view.setMinWidth(Region.USE_PREF_SIZE);
        view.setMaxWidth(Region.USE_PREF_SIZE);
        view.setOnAction(e -> {
            markRead(it);
            popup.hide();
            // navigation hook can be added later
        });

        HBox actions = new HBox(6, view);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setMinWidth(Region.USE_PREF_SIZE);
        if (!it.read()) actions.getChildren().add(mark);

        VBox text = new VBox(2, t, d);
        text.setMaxWidth(Double.MAX_VALUE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox row = new HBox(10, icon, text, spacer, ts, actions);
        row.setAlignment(Pos.TOP_LEFT);
        row.setPadding(new Insets(10));

        // ✅ card look (dark)
        row.setStyle(
                "-fx-background-radius: 12;" +
                        "-fx-border-radius: 12;" +
                        "-fx-border-color: rgba(255,255,255,0.10);" +
                        "-fx-border-width: 1;" +
                        (it.read()
                                ? "-fx-background-color: rgba(255,255,255,0.04);"
                                : "-fx-background-color: rgba(120,90,255,0.18);")
        );

        return row;
    }

    private void markRead(NotificationItem it) {
        if (it == null || it.read()) return;
        notifications.markRead(it.key());
    }

    private static String safe(String s) {
        return (s == null) ? "" : s;
    }
}
