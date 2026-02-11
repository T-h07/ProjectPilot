package com.projectpilot.ui.dialogs;

import com.projectpilot.model.NotificationItem;
import com.projectpilot.service.NotificationService;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class NotificationsDialog {

    private NotificationsDialog() {}

    public static void showLogin(Window owner, NotificationService service) {
        List<NotificationItem> highlights = service.loginHighlights();
        if (highlights.isEmpty()) return;

        Dialog<Void> d = build(owner, "What’s new for you", highlights);
        d.show();
        service.markLoginShown();
    }

    public static void showAll(Window owner, NotificationService service) {
        List<NotificationItem> all = new ArrayList<>(service.items());
        all.sort(Comparator.comparing(NotificationItem::at).reversed());

        Dialog<Void> d = build(owner, "Notifications", all);
        d.show();
    }

    private static Dialog<Void> build(Window owner, String title, List<NotificationItem> items) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.initModality(Modality.NONE);
        dialog.setTitle(title);
        try { DialogTheme.apply(dialog); } catch (Exception e) { com.projectpilot.util.AppLog.warn("notifications-dialog", "Dialog theme apply failed: " + (e == null ? "" : e.getMessage())); }

        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().add(ButtonType.CLOSE);

        // ✅ ensure it uses the same stylesheet as the main app
        try {
            if (owner != null && owner.getScene() != null) {
                pane.getStylesheets().setAll(owner.getScene().getStylesheets());
                pane.getStyleClass().addAll(owner.getScene().getRoot().getStyleClass());
            }
        } catch (Exception e) { com.projectpilot.util.AppLog.warn("notifications-dialog", "Failed to copy owner styles: " + (e == null ? "" : e.getMessage())); }
        pane.getStyleClass().add("pp-root");

        VBox root = new VBox(10);
        root.setPadding(new Insets(12));
        root.getStyleClass().add("notif-dialog");

        if (items.isEmpty()) {
            Label empty = new Label("No notifications.");
            empty.getStyleClass().add("notif-empty");
            root.getChildren().add(empty);
        } else {
            root.getChildren().add(section("Today", itemsForDay(items, 0)));
            root.getChildren().add(section("Yesterday", itemsForDay(items, 1)));
            root.getChildren().add(section("Earlier", itemsForEarlier(items)));
        }

        ScrollPane scroller = new ScrollPane(root);
        scroller.setFitToWidth(true);
        scroller.setPrefViewportHeight(520);
        scroller.getStyleClass().add("notif-scroll");
        root.getStyleClass().add("notif-list");

        pane.setContent(scroller);
        pane.setPrefWidth(720);

        return dialog;
    }

    private static VBox section(String name, List<NotificationItem> items) {
        VBox box = new VBox(8);

        Label h = new Label(name);
        h.getStyleClass().add("notif-section-title");
        box.getChildren().add(h);

        if (items.isEmpty()) {
            Label none = new Label("—");
            none.getStyleClass().add("notif-empty");
            box.getChildren().add(none);
            return box;
        }

        for (NotificationItem it : items) box.getChildren().add(card(it));
        return box;
    }

    private static VBox card(NotificationItem it) {
        VBox card = new VBox(4);
        card.getStyleClass().add("notif-card");
        card.setPadding(new Insets(12));

        Label title = new Label(it.title());
        title.getStyleClass().add("notif-item-title");

        Label detail = new Label(it.detail());
        detail.getStyleClass().add("notif-item-body");
        detail.setWrapText(true);

        Label time = new Label(it.at().format(DateTimeFormatter.ofPattern("HH:mm")));
        time.getStyleClass().add("notif-time");

        HBox top = new HBox(10, title);
        HBox.setHgrow(title, Priority.ALWAYS);
        top.getChildren().add(time);

        card.getChildren().addAll(top, detail);

        if (it.urgent()) card.getStyleClass().add("urgent");
        if (!it.read()) card.getStyleClass().add("unread");

        return card;
    }

    private static List<NotificationItem> itemsForDay(List<NotificationItem> all, int daysAgo) {
        LocalDate target = LocalDate.now().minusDays(daysAgo);
        List<NotificationItem> out = new ArrayList<>();
        for (NotificationItem it : all) {
            if (it.at().toLocalDate().equals(target)) out.add(it);
        }
        out.sort(Comparator.comparing(NotificationItem::at).reversed());
        return out;
    }

    private static List<NotificationItem> itemsForEarlier(List<NotificationItem> all) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        List<NotificationItem> out = new ArrayList<>();
        for (NotificationItem it : all) {
            LocalDate d = it.at().toLocalDate();
            if (!d.equals(today) && !d.equals(yesterday)) out.add(it);
        }
        out.sort(Comparator.comparing(NotificationItem::at).reversed());
        return out;
    }
}
