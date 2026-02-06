package com.projectpilot.ui.components;

import com.projectpilot.model.NotificationItem;
import com.projectpilot.service.NotificationService;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

public class NotificationBellButton extends StackPane {

    private final NotificationService notifications;

    private final Button bell = new Button("🔔");
    private final Label badge = new Label();

    private final NotificationCenterPopover popover;

    public NotificationBellButton(NotificationService notifications) {
        this.notifications = notifications;
        this.popover = new NotificationCenterPopover(notifications);

        getStyleClass().add("notif-wrap");
        setAlignment(Pos.TOP_RIGHT);

        bell.getStyleClass().add("notif-bell");
        badge.getStyleClass().add("notif-badge");
        badge.setMouseTransparent(true);

        getChildren().addAll(bell, badge);

        updateBadge();

        // ✅ Correct generic type
        this.notifications.items().addListener((ListChangeListener<NotificationItem>) c -> updateBadge());

        bell.setOnAction(e -> {
            // rebuild right before showing so it reflects latest state
            notifications.rebuildNow();
            popover.toggle(bell);
        });
    }

    private void updateBadge() {
        int unread = notifications.unreadCount();
        badge.setText(String.valueOf(unread));
        badge.setVisible(unread > 0);
        badge.setManaged(unread > 0);
    }
}
