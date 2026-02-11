package com.projectpilot.ui.dialogs;

import com.projectpilot.model.NotificationItem;
import com.projectpilot.service.NotificationService;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public final class WhatsNewDialog {

    private WhatsNewDialog() {}

    public static void showAfterLogin(Window owner,
                                      NotificationService notifications,
                                      Consumer<NotificationItem> onOpen) {

        List<NotificationItem> fresh = new ArrayList<>(notifications.loginHighlights());
        fresh.sort(Comparator.comparing(NotificationItem::at).reversed());

        if (fresh.isEmpty()) return;

        Dialog<Void> d = new Dialog<>();
        d.setTitle("What’s new");
        d.initOwner(owner);

        try { DialogTheme.apply(d); } catch (Exception e) { com.projectpilot.util.AppLog.warn("whatsnew", "Dialog theme apply failed: " + (e == null ? "" : e.getMessage())); }

        DialogPane pane = d.getDialogPane();
        pane.getButtonTypes().add(new ButtonType("Close", ButtonBar.ButtonData.OK_DONE));

        try {
            if (owner != null && owner.getScene() != null) {
                pane.getStylesheets().setAll(owner.getScene().getStylesheets());
                pane.getStyleClass().addAll(owner.getScene().getRoot().getStyleClass());
            }
        } catch (Exception e) { com.projectpilot.util.AppLog.warn("whatsnew", "Failed to copy owner styles: " + (e == null ? "" : e.getMessage())); }
        pane.getStyleClass().add("pp-root");

        VBox box = new VBox(10);
        box.setPadding(new Insets(12));

        Label head = new Label("Updates since your last check");
        head.getStyleClass().add("whatsnew-title");

        VBox items = new VBox(8);

        for (NotificationItem n : fresh) {
            HBox row = new HBox(10);
            row.getStyleClass().addAll("whatsnew-item");
            row.setPadding(new Insets(8));

            Label title = new Label("• " + safe(n.title()));
            title.setWrapText(true);

            Label body = new Label(safe(n.detail()));
            body.setWrapText(true);
            body.getStyleClass().add("whatsnew-body");

            VBox text = new VBox(2, title, body);

            Button view = new Button("View");
            view.getStyleClass().add("secondary");
            view.setOnAction(e -> {
                notifications.markRead(n.key());
                d.close();
                if (onOpen != null) onOpen.accept(n);
            });

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            row.getChildren().addAll(text, spacer, view);
            items.getChildren().add(row);
        }

        box.getChildren().addAll(head, items);
        pane.setContent(box);

        d.showAndWait();
    }

    private static String safe(String s) {
        return (s == null) ? "" : s;
    }
}
