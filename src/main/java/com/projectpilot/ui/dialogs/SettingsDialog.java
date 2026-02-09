package com.projectpilot.ui.dialogs;

import com.projectpilot.core.AppState;
import com.projectpilot.util.UserSettingsStore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public final class SettingsDialog {

    private SettingsDialog() {}

    public static void show(Window owner, AppState appState) {
        if (appState == null) return;

        UserSettingsStore store = new UserSettingsStore();
        UserSettingsStore.Settings settings = store.load(appState.getSession() == null ? null : appState.getSession().id());
        appState.applySettings(settings);

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Settings");
        dialog.setHeaderText("Preferences");
        if (owner != null) dialog.initOwner(owner);

        ButtonType close = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(close);

        TabPane tabs = new TabPane();
        tabs.getTabs().addAll(
                buildAppearanceTab(appState, store),
                buildNotificationsTab(),
                buildOwnerTab(appState, store)
        );
        tabs.getTabs().forEach(t -> t.setClosable(false));

        VBox content = new VBox(12, tabs);
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);

        DialogTheme.apply(dialog);
        dialog.showAndWait();
    }

    private static Tab buildAppearanceTab(AppState appState, UserSettingsStore store) {
        Tab tab = new Tab("Appearance");

        ChoiceBox<String> theme = new ChoiceBox<>();
        theme.getItems().addAll("Standard", "White");
        String currentTheme = appState.getTheme() == null ? "" : appState.getTheme().toLowerCase();
        theme.setValue("light".equals(currentTheme) ? "White" : "Standard");

        ChoiceBox<String> density = new ChoiceBox<>();
        density.getItems().addAll("Comfortable", "Compact");
        density.setValue("compact".equalsIgnoreCase(appState.getDensity()) ? "Compact" : "Comfortable");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);

        grid.add(new Label("Theme"), 0, 0);
        grid.add(theme, 1, 0);
        grid.add(new Label("Density"), 0, 1);
        grid.add(density, 1, 1);

        theme.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            String value = "White".equalsIgnoreCase(n) ? "light" : "default";
            appState.setTheme(value);
            persist(appState, store);
        });

        density.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            String value = "Compact".equalsIgnoreCase(n) ? "compact" : "comfortable";
            appState.setDensity(value);
            persist(appState, store);
        });

        VBox wrap = new VBox(12, grid);
        wrap.setPadding(new Insets(8));
        tab.setContent(wrap);
        return tab;
    }

    private static Tab buildNotificationsTab() {
        Tab tab = new Tab("Notifications");

        Label info = new Label("Notification preferences will appear here.");
        info.getStyleClass().add("muted");

        VBox wrap = new VBox(12, info);
        wrap.setPadding(new Insets(8));
        tab.setContent(wrap);
        return tab;
    }

    private static Tab buildOwnerTab(AppState appState, UserSettingsStore store) {
        Tab tab = new Tab("Owner Mode");

        boolean isOwner = appState.isOwnerUser();

        Label info = new Label(isOwner
                ? "Owner-only visual effects and message filters."
                : "Only TaulantHaxhiu can change these settings.");
        info.getStyleClass().add("muted");

        ChoiceBox<String> messageStyle = new ChoiceBox<>();
        messageStyle.getItems().addAll("None", "Prefix", "Uppercase", "Highlight");
        messageStyle.setValue(readOwnerStyle(appState.getOwnerMessageStyle()));

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);

        grid.add(new Label("Message style"), 0, 0);
        grid.add(messageStyle, 1, 0);

        messageStyle.setDisable(!isOwner);
        grid.setDisable(!isOwner);

        messageStyle.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            String value = switch (n == null ? "" : n.toLowerCase()) {
                case "prefix" -> "prefix";
                case "uppercase" -> "uppercase";
                case "highlight" -> "highlight";
                default -> "none";
            };
            appState.setOwnerMessageStyle(value);
            persist(appState, store);
        });

        VBox wrap = new VBox(12, info, grid);
        wrap.setPadding(new Insets(8));
        tab.setContent(wrap);
        return tab;
    }

    private static String readOwnerStyle(String value) {
        String v = value == null ? "" : value.trim().toLowerCase();
        return switch (v) {
            case "prefix" -> "Prefix";
            case "uppercase" -> "Uppercase";
            case "highlight" -> "Highlight";
            default -> "None";
        };
    }

    private static void persist(AppState appState, UserSettingsStore store) {
        String userId = appState.getSession() == null ? null : appState.getSession().id();
        store.save(userId, new UserSettingsStore.Settings(
                appState.getTheme(),
                appState.getDensity(),
                appState.getOwnerMessageStyle()
        ));
    }
}
