package com.projectpilot.ui.dialogs;

import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

import java.net.URL;

public final class DialogTheme {

    private static final String CSS_PATH = "/css/app.css";

    private DialogTheme() {}

    public static void apply(Dialog<?> dialog) {
        if (dialog == null) return;

        DialogPane pane = dialog.getDialogPane();
        if (pane == null) return;

        // Attach app stylesheet to the dialog graph
        URL css = DialogTheme.class.getResource(CSS_PATH);
        if (css != null) {
            String cssUrl = css.toExternalForm();
            if (!pane.getStylesheets().contains(cssUrl)) {
                pane.getStylesheets().add(cssUrl);
            }
        }

        // Dialogs are not under MainLayout, so add pp-root here
        if (!pane.getStyleClass().contains("pp-root")) {
            pane.getStyleClass().add("pp-root");
        }

        // Optional extra hook for dialog-only styling
        if (!pane.getStyleClass().contains("pp-dialog")) {
            pane.getStyleClass().add("pp-dialog");
        }
    }
}
