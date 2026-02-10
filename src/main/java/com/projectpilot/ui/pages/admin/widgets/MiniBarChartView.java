package com.projectpilot.ui.pages.admin.widgets;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

public final class MiniBarChartView extends StackPane {

    public record Bar(String label, double value) {}

    private final Canvas canvas = new Canvas();
    private List<Bar> bars = new ArrayList<>();

    public MiniBarChartView() {
        getStyleClass().addAll("pp-card", "mini-bar-card");
        getChildren().add(canvas);

        widthProperty().addListener((obs, o, n) -> resizeAndRedraw());
        heightProperty().addListener((obs, o, n) -> resizeAndRedraw());

        setMinHeight(190);
        setMinWidth(240);
    }

    public void setBars(List<Bar> bars) {
        this.bars = (bars == null) ? new ArrayList<>() : new ArrayList<>(bars);
        redraw();
    }

    private void resizeAndRedraw() {
        canvas.setWidth(Math.max(1, getWidth()));
        canvas.setHeight(Math.max(1, getHeight()));
        redraw();
    }

    private void redraw() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 2 || h <= 2) return;

        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);

        double pad = 14;
        double left = pad;
        double top = pad;
        double right = pad;
        double bottom = pad + 18; // room for labels

        double innerW = Math.max(1, w - left - right);
        double innerH = Math.max(1, h - top - bottom);

        if (bars == null || bars.isEmpty()) return;

        double max = 0;
        for (Bar b : bars) max = Math.max(max, b.value());
        max = Math.max(1e-9, max);

        int n = bars.size();
        double gap = 10;
        double barW = Math.max(10, (innerW - gap * (n - 1)) / n);

        // palette aligned with theme
        Color[] palette = new Color[] {
                Color.web("#22c55e"), // online/success
                Color.web("#f59e0b"), // idle/warn
                Color.web("#ef4444"), // blocked/danger
                Color.web("#4f8cff"), // accent
                Color.web("#94a3b8")  // neutral
        };

        for (int i = 0; i < n; i++) {
            Bar b = bars.get(i);
            double norm = b.value() / max;
            double bh = innerH * norm;

            double x = left + i * (barW + gap);
            double y = top + (innerH - bh);

            g.setFill(palette[i % palette.length].deriveColor(0, 1, 1, 0.85));
            g.fillRoundRect(x, y, barW, bh, 10, 10);

            // label
            g.setFill(Color.rgb(232, 238, 249, 0.70));
            g.fillText(b.label(), x, top + innerH + 14);
        }

        // subtle baseline
        g.setStroke(Color.rgb(255, 255, 255, 0.10));
        g.strokeLine(left, top + innerH, left + innerW, top + innerH);
    }
}
