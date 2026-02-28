package com.projectpilot.ui.pages.admin.widgets;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.TextAlignment;
import javafx.geometry.VPos;

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
        double top = pad + 4;
        double right = pad;
        double bottom = pad + 22; // room for labels

        double innerW = Math.max(1, w - left - right);
        double innerH = Math.max(1, h - top - bottom);

        if (bars == null || bars.isEmpty()) return;

        double maxRaw = 0;
        for (Bar b : bars) maxRaw = Math.max(maxRaw, b.value());
        double max = Math.max(1.0, maxRaw * 1.15); // headroom for labels

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

        // baseline + guide lines
        g.setStroke(Color.rgb(255, 255, 255, 0.12));
        g.strokeLine(left, top + innerH, left + innerW, top + innerH);
        g.setStroke(Color.rgb(255, 255, 255, 0.06));
        for (int i = 1; i <= 3; i++) {
            double y = top + innerH - (innerH * i / 4.0);
            g.strokeLine(left, y, left + innerW, y);
        }

        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);

        for (int i = 0; i < n; i++) {
            Bar b = bars.get(i);
            double norm = Math.max(0.0, Math.min(1.0, b.value() / max));
            double bh = innerH * norm;

            double x = left + i * (barW + gap);
            double y = top + (innerH - bh);

            Color base = palette[i % palette.length];
            g.setFill(new LinearGradient(
                    x, y, x, y + Math.max(1.0, bh),
                    false,
                    CycleMethod.NO_CYCLE,
                    new Stop(0, base.deriveColor(0, 1.05, 1.15, 0.96)),
                    new Stop(1, base.deriveColor(0, 1.00, 0.78, 0.90))
            ));
            g.fillRoundRect(x, y, barW, bh, 10, 10);

            g.setFill(Color.rgb(232, 238, 249, 0.82));
            g.fillText(Integer.toString((int) Math.round(Math.max(0, b.value()))), x + barW / 2.0, y - 8);

            // label
            g.setFill(Color.rgb(232, 238, 249, 0.72));
            g.fillText(b.label(), x + barW / 2.0, top + innerH + 14);
        }
    }
}
