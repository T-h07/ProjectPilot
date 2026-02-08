package com.projectpilot.ui.components;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class EmptyStatePane extends VBox {

    public EmptyStatePane(String title, String message, Node... actions) {
        setAlignment(Pos.CENTER);
        setSpacing(10);
        getStyleClass().add("empty-state");

        Label titleLabel = new Label(title == null ? "" : title);
        titleLabel.getStyleClass().add("empty-title");

        Label messageLabel = new Label(message == null ? "" : message);
        messageLabel.getStyleClass().add("empty-text");
        messageLabel.setWrapText(true);

        getChildren().addAll(titleLabel, messageLabel);

        if (actions != null) {
            for (Node n : actions) {
                if (n != null) getChildren().add(n);
            }
        }
    }
}
