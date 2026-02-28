package com.projectpilot.ui.pages.admin.widgets;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;

public final class MetricTile extends StackPane {

    private final Label title = new Label();
    private final Label value = new Label();
    private final Label sub = new Label();

    private final Polygon sparkArea = new Polygon();
    private final Polyline spark = new Polyline();
    private final Pane sparkPane = new Pane(sparkArea, spark);

    private final Rectangle clip = new Rectangle();

    private List<Double> series = List.of();

    public MetricTile(String titleText) {
        getStyleClass().addAll("pp-card", "metric-tile");

        title.getStyleClass().add("metric-title");
        value.getStyleClass().add("metric-value");
        sub.getStyleClass().add("metric-subtitle");

        title.setText(titleText);

        spark.getStyleClass().add("metric-sparkline");
        sparkArea.getStyleClass().add("metric-sparkline-area");
        sparkPane.getStyleClass().add("metric-sparkline-wrap");
        sparkPane.setMinHeight(28);
        sparkPane.setPrefHeight(28);
        sparkPane.setMaxHeight(28);

        // clip sparklines so they never overflow
        sparkPane.setClip(clip);
        sparkPane.widthProperty().addListener((obs, o, n) -> redraw());
        sparkPane.heightProperty().addListener((obs, o, n) -> redraw());
        clip.widthProperty().bind(sparkPane.widthProperty());
        clip.heightProperty().bind(sparkPane.heightProperty());

        VBox text = new VBox(2, title, value, sub);
        text.setAlignment(Pos.TOP_LEFT);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        VBox root = new VBox(8, text, spacer, sparkPane);
        root.setPadding(new Insets(12));
        root.setFillWidth(true);

        getChildren().add(root);
        setMinHeight(110);
    }

    public void setTitleText(String t) {
        title.setText(t == null ? "" : t);
    }

    public void setValueText(String v) {
        value.setText(v == null ? "" : v);
    }

    public void setSubText(String s) {
        sub.setText(s == null ? "" : s);
    }

    public void clearSeries() {
        this.series = List.of();
        spark.getPoints().clear();
        sparkArea.getPoints().clear();
    }

    public void setSeries(List<? extends Number> points) {
        if (points == null || points.isEmpty()) {
            clearSeries();
            return;
        }
        List<Double> out = new ArrayList<>(points.size());
        for (Number n : points) out.add(n == null ? 0.0 : n.doubleValue());
        this.series = out;
        redraw();
    }

    private void redraw() {
        double w = sparkPane.getWidth();
        double h = sparkPane.getHeight();
        if (w <= 2 || h <= 2 || series == null || series.size() < 2) {
            spark.getPoints().clear();
            sparkArea.getPoints().clear();
            return;
        }

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double d : series) {
            if (d < min) min = d;
            if (d > max) max = d;
        }
        double range = Math.max(1e-9, max - min);

        int n = series.size();
        double leftPad = 2;
        double rightPad = 2;
        double topPad = 2;
        double bottomPad = 2;

        double innerW = Math.max(1, w - leftPad - rightPad);
        double innerH = Math.max(1, h - topPad - bottomPad);

        spark.getPoints().clear();
        sparkArea.getPoints().clear();

        double firstX = leftPad;
        double baselineY = topPad + innerH;
        for (int i = 0; i < n; i++) {
            double x = leftPad + (innerW * i) / (n - 1.0);
            double norm = (series.get(i) - min) / range;
            double y = topPad + (innerH * (1.0 - norm));
            spark.getPoints().addAll(x, y);
            if (i == 0) firstX = x;
        }

        double lastX = leftPad + innerW;
        sparkArea.getPoints().add(firstX);
        sparkArea.getPoints().add(baselineY);
        sparkArea.getPoints().addAll(spark.getPoints());
        sparkArea.getPoints().add(lastX);
        sparkArea.getPoints().add(baselineY);
        if (n > 1) {
            sparkArea.getPoints().add(firstX);
            sparkArea.getPoints().add(baselineY);
        }
    }
}
