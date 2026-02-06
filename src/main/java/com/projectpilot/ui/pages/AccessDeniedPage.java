package com.projectpilot.ui.pages;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public final class AccessDeniedPage extends VBox {

    public AccessDeniedPage(String message) {
        setPadding(new Insets(18));
        setSpacing(8);

        Label title = new Label("Access denied");
        title.getStyleClass().add("page-title");

        Label msg = new Label(message == null ? "" : message);
        msg.getStyleClass().add("muted");
        msg.setWrapText(true);

        getChildren().addAll(title, msg);
    }
}
