package com.projectpilot.ui.pages.admin.widgets;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

public final class SparklineView extends Region {

    private final Canvas canvas = new Canvas();
    private final ObservableList<Double> values = FXCollections.observableArrayList();

    public SparklineView() {
        getChildren().add(canvas);
        setMinHeight(46);
        setPrefHeight(46);
        setMaxWidth(Double.MAX_VALUE);

        widthProperty().addListener((o, a, b) -> layoutChildren());
        heightProperty().addListener((o, a, b) -> layoutChildren());
        values.addListener((ListChangeListener<Double>) c -> draw());
    }

    public ObservableList<Double> values() {
        return values;
    }

    @Override
    protected void layoutChildren() {
        double w = Math.max(1, getWidth());
        double h = Math.max(1, getHeight());
        canvas.setWidth(w);
        canvas.setHeight(h);
        draw();
    }

    private void draw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        g.clearRect(0, 0, w, h);

        if (values.size() < 2) return;

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Double d : values) {
            if (d == null || !Double.isFinite(d)) continue;
            min = Math.min(min, d);
            max = Math.max(max, d);
        }
        if (!Double.isFinite(min) || !Double.isFinite(max)) return;

        double range = Math.max(1e-9, max - min);
        double pad = 4;

        g.setStroke(Color.web("#4f8cff"));
        g.setLineWidth(2.0);

        double xStep = (w - 2 * pad) / (values.size() - 1);

        double prevX = pad;
        double prevY = yFor(values.get(0), min, range, h, pad);

        for (int i = 1; i < values.size(); i++) {
            double x = pad + i * xStep;
            double y = yFor(values.get(i), min, range, h, pad);
            g.strokeLine(prevX, prevY, x, y);
            prevX = x;
            prevY = y;
        }
    }

    private double yFor(Double v, double min, double range, double h, double pad) {
        double vv = (v == null || !Double.isFinite(v)) ? min : v;
        double t = (vv - min) / range;        // 0..1
        return pad + (h - 2 * pad) * (1 - t); // invert
    }
}
