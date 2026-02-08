package com.projectpilot.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class WindowChrome extends BorderPane {

    private static final double RESIZE_MARGIN = 6;

    private final Stage stage;
    private double dragOffsetX;
    private double dragOffsetY;
    private double resizeStartX;
    private double resizeStartY;
    private double resizeStartW;
    private double resizeStartH;
    private double resizeStartStageX;
    private double resizeStartStageY;
    private ResizeMode resizeMode = ResizeMode.NONE;

    public WindowChrome(Stage stage) {
        this.stage = stage;
        getStyleClass().addAll("pp-root", "window-chrome");

        Label title = new Label("ProjectPilot");
        title.getStyleClass().add("window-title");

        Button minBtn = new Button("-");
        minBtn.getStyleClass().addAll("window-btn", "window-min");
        minBtn.setFocusTraversable(false);
        minBtn.setOnAction(e -> stage.setIconified(true));

        Button maxBtn = new Button();
        maxBtn.getStyleClass().addAll("window-btn", "window-max");
        maxBtn.setFocusTraversable(false);
        maxBtn.setOnAction(e -> stage.setMaximized(!stage.isMaximized()));
        Region maxIcon = new Region();
        maxIcon.getStyleClass().add("window-max-icon");
        maxBtn.setGraphic(maxIcon);

        Button closeBtn = new Button("X");
        closeBtn.getStyleClass().addAll("window-btn", "window-close");
        closeBtn.setFocusTraversable(false);
        closeBtn.setOnAction(e -> stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST)));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox controls = new HBox(6, minBtn, maxBtn, closeBtn);
        controls.setAlignment(Pos.CENTER_RIGHT);
        controls.getStyleClass().add("window-controls");

        HBox titleBar = new HBox(10, title, spacer, controls);
        titleBar.getStyleClass().add("window-titlebar");
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(6, 10, 6, 10));

        titleBar.setOnMousePressed(e -> {
            if (stage.isMaximized()) return;
            dragOffsetX = e.getScreenX() - stage.getX();
            dragOffsetY = e.getScreenY() - stage.getY();
        });

        titleBar.setOnMouseDragged(e -> {
            if (stage.isMaximized()) return;
            stage.setX(e.getScreenX() - dragOffsetX);
            stage.setY(e.getScreenY() - dragOffsetY);
        });

        titleBar.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                stage.setMaximized(!stage.isMaximized());
            }
        });

        setTop(titleBar);
        attachResizeHandlers();
    }

    public void setContent(Node content) {
        setCenter(content);
    }

    private void attachResizeHandlers() {
        setOnMouseMoved(e -> {
            if (stage.isMaximized()) {
                setCursor(Cursor.DEFAULT);
                resizeMode = ResizeMode.NONE;
                return;
            }

            double x = e.getX();
            double y = e.getY();
            double w = getWidth();
            double h = getHeight();

            boolean left = x < RESIZE_MARGIN;
            boolean right = x > w - RESIZE_MARGIN;
            boolean top = y < RESIZE_MARGIN;
            boolean bottom = y > h - RESIZE_MARGIN;

            if (left && top) {
                setCursor(Cursor.NW_RESIZE);
                resizeMode = ResizeMode.NW;
            } else if (right && top) {
                setCursor(Cursor.NE_RESIZE);
                resizeMode = ResizeMode.NE;
            } else if (left && bottom) {
                setCursor(Cursor.SW_RESIZE);
                resizeMode = ResizeMode.SW;
            } else if (right && bottom) {
                setCursor(Cursor.SE_RESIZE);
                resizeMode = ResizeMode.SE;
            } else if (left) {
                setCursor(Cursor.W_RESIZE);
                resizeMode = ResizeMode.W;
            } else if (right) {
                setCursor(Cursor.E_RESIZE);
                resizeMode = ResizeMode.E;
            } else if (top) {
                setCursor(Cursor.N_RESIZE);
                resizeMode = ResizeMode.N;
            } else if (bottom) {
                setCursor(Cursor.S_RESIZE);
                resizeMode = ResizeMode.S;
            } else {
                setCursor(Cursor.DEFAULT);
                resizeMode = ResizeMode.NONE;
            }
        });

        setOnMousePressed(e -> {
            if (resizeMode == ResizeMode.NONE || stage.isMaximized()) return;
            resizeStartX = e.getScreenX();
            resizeStartY = e.getScreenY();
            resizeStartW = stage.getWidth();
            resizeStartH = stage.getHeight();
            resizeStartStageX = stage.getX();
            resizeStartStageY = stage.getY();
        });

        setOnMouseDragged(e -> {
            if (resizeMode == ResizeMode.NONE || stage.isMaximized()) return;

            double dx = e.getScreenX() - resizeStartX;
            double dy = e.getScreenY() - resizeStartY;

            double minW = Math.max(stage.getMinWidth(), 860);
            double minH = Math.max(stage.getMinHeight(), 600);

            double newX = resizeStartStageX;
            double newY = resizeStartStageY;
            double newW = resizeStartW;
            double newH = resizeStartH;

            if (resizeMode.hasEast()) {
                newW = resizeStartW + dx;
            }
            if (resizeMode.hasSouth()) {
                newH = resizeStartH + dy;
            }
            if (resizeMode.hasWest()) {
                newW = resizeStartW - dx;
                newX = resizeStartStageX + dx;
            }
            if (resizeMode.hasNorth()) {
                newH = resizeStartH - dy;
                newY = resizeStartStageY + dy;
            }

            if (newW < minW) {
                if (resizeMode.hasWest()) newX += newW - minW;
                newW = minW;
            }
            if (newH < minH) {
                if (resizeMode.hasNorth()) newY += newH - minH;
                newH = minH;
            }

            stage.setX(newX);
            stage.setY(newY);
            stage.setWidth(newW);
            stage.setHeight(newH);
        });

        setOnMouseReleased(e -> resizeMode = ResizeMode.NONE);
    }

    private enum ResizeMode {
        NONE(false, false, false, false),
        N(false, true, false, false),
        S(false, false, true, false),
        E(true, false, false, false),
        W(false, false, false, true),
        NE(true, true, false, false),
        NW(false, true, false, true),
        SE(true, false, true, false),
        SW(false, false, true, true);

        private final boolean east;
        private final boolean north;
        private final boolean south;
        private final boolean west;

        ResizeMode(boolean east, boolean north, boolean south, boolean west) {
            this.east = east;
            this.north = north;
            this.south = south;
            this.west = west;
        }

        boolean hasEast() { return east; }
        boolean hasNorth() { return north; }
        boolean hasSouth() { return south; }
        boolean hasWest() { return west; }
    }
}
