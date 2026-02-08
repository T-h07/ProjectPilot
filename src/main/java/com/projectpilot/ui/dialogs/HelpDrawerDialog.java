package com.projectpilot.ui.dialogs;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;

public class HelpDrawerDialog extends Dialog<Void> {

    public HelpDrawerDialog() {
        DialogTheme.apply(this);
        setTitle("Help");
        setHeaderText(null);
        getDialogPane().getStyleClass().add("help-drawer");
        getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);

        VBox content = new VBox(12);
        content.setPadding(new Insets(12));

        content.getChildren().add(section("Shortcuts", List.of(
                "Ctrl+K: Command palette",
                "Esc: Close dialogs"
        )));

        content.getChildren().add(section("Tips", List.of(
                "Use the Project picker in the top bar to switch projects.",
                "Files and Links keep specs and assets in one place.",
                "Notes are private to you and tied to your assigned tasks."
        )));

        getDialogPane().setContent(content);
    }

    public static void show(Window owner) {
        HelpDrawerDialog dialog = new HelpDrawerDialog();
        if (owner != null) dialog.initOwner(owner);
        dialog.showAndWait();
    }

    private Node section(String title, List<String> lines) {
        VBox box = new VBox(6);
        box.getStyleClass().add("help-section");

        Label header = new Label(title == null ? "" : title);
        header.getStyleClass().add("help-title");
        box.getChildren().add(header);

        if (lines != null) {
            for (String line : lines) {
                Label label = new Label(line == null ? "" : line);
                label.getStyleClass().add("help-line");
                label.setWrapText(true);
                box.getChildren().add(label);
            }
        }

        return box;
    }
}
