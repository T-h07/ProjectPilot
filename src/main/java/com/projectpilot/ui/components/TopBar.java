package com.projectpilot.ui.components;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.data.db.DbStore;
import com.projectpilot.data.db.auth.UserAdminService;
import com.projectpilot.data.db.auth.UserSession;
import com.projectpilot.service.NotificationService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.net.URL;

public class TopBar extends HBox {

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

        profileBtn.setOnAction(e -> toggleProfileMenu(profileMenu, profileBtn, appState, store));

        HBox userBox = new HBox(8, userName, profileBtn);
        userBox.setAlignment(Pos.CENTER_RIGHT);
        userBox.getStyleClass().add("topbar-userbox");

        getChildren().addAll(title, picker, bell, spacer, userBox);
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

    private static void ensurePopupStyles(ContextMenu menu) {
        if (menu == null || menu.getScene() == null) return;
        URL css = TopBar.class.getResource("/css/app.css");
        if (css == null) return;
        String url = css.toExternalForm();
        if (!menu.getScene().getStylesheets().contains(url)) {
            menu.getScene().getStylesheets().add(url);
        }
    }
}
