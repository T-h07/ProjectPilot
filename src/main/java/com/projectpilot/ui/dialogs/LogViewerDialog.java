package com.projectpilot.ui.dialogs;

import com.projectpilot.util.AppLog;
import com.projectpilot.util.LogEntry;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.StringJoiner;

public final class LogViewerDialog {

    private LogViewerDialog() {}

    public static void show() {
        Dialog<Void> dialog = new Dialog<>();
        DialogTheme.apply(dialog);
        dialog.setTitle("Diagnostics");
        dialog.setHeaderText("Application logs");

        ButtonType closeBtn = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(closeBtn);

        ListView<LogEntry> list = new ListView<>(AppLog.entries());
        list.getStyleClass().add("log-list");
        list.setCellFactory(lv -> new LogCell());
        list.setPrefHeight(420);

        Button copyAll = new Button("Copy all");
        copyAll.getStyleClass().add("secondary");
        copyAll.setOnAction(e -> copyAll());

        Button clear = new Button("Clear");
        clear.getStyleClass().add("secondary");
        clear.setOnAction(e -> AppLog.clear());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actions = new HBox(8, spacer, copyAll, clear);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(10, list, actions);
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);

        dialog.show();
    }

    private static void copyAll() {
        StringJoiner joiner = new StringJoiner(System.lineSeparator());
        for (LogEntry entry : AppLog.entries()) {
            if (entry != null) joiner.add(entry.formatLine());
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(joiner.toString());
        Clipboard.getSystemClipboard().setContent(content);
    }

    private static final class LogCell extends ListCell<LogEntry> {
        @Override
        protected void updateItem(LogEntry item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            javafx.scene.control.Label time = new javafx.scene.control.Label(item.timeLabel());
            time.getStyleClass().add("log-time");

            javafx.scene.control.Label level = new javafx.scene.control.Label(item.level().name());
            level.getStyleClass().addAll("log-level", "log-level-" + item.level().name().toLowerCase());

            javafx.scene.control.Label source = new javafx.scene.control.Label(item.source().isBlank() ? "app" : item.source());
            source.getStyleClass().add("log-source");

            javafx.scene.control.Label msg = new javafx.scene.control.Label(item.message());
            msg.getStyleClass().add("log-message");
            msg.setWrapText(true);

            HBox head = new HBox(8, time, level, source);
            head.setAlignment(Pos.CENTER_LEFT);

            VBox body = new VBox(4, head, msg);
            body.getStyleClass().add("log-row");
            body.setPadding(new Insets(6));

            setGraphic(body);
        }
    }
}
