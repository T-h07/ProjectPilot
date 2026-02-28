package com.projectpilot.ui.pages.admin.widgets;


import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.scene.shape.Circle;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DonutChartView extends StackPane {

    private final Canvas canvas = new Canvas();
    private final Label centerTitle = new Label();
    private final Label centerSub = new Label();
    private final VBox legend = new VBox(4);
    private boolean legendEnabled = false;

    private Map<String, Integer> data = new LinkedHashMap<>();

    public DonutChartView() {
        getStyleClass().addAll("pp-card", "donut-card");

        centerTitle.getStyleClass().add("donut-center-title");
        centerSub.getStyleClass().add("donut-center-sub");

        VBox center = new VBox(2, centerTitle, centerSub);
        center.setMouseTransparent(true);
        center.setAlignment(Pos.CENTER);
        center.setFillWidth(false);
        center.setMaxWidth(Region.USE_COMPUTED_SIZE);
        centerTitle.setMaxWidth(Region.USE_COMPUTED_SIZE);
        centerSub.setMaxWidth(Region.USE_COMPUTED_SIZE);
        centerTitle.setAlignment(Pos.CENTER);
        centerSub.setAlignment(Pos.CENTER);
        centerTitle.setTextAlignment(TextAlignment.CENTER);
        centerSub.setTextAlignment(TextAlignment.CENTER);

        legend.getStyleClass().add("donut-legend");
        legend.setMouseTransparent(true);
        legend.setVisible(false);
        legend.setManaged(false);

        getChildren().addAll(canvas, center, legend);
        StackPane.setAlignment(center, Pos.CENTER);
        StackPane.setAlignment(legend, Pos.BOTTOM_LEFT);
        StackPane.setMargin(legend, new Insets(0, 0, 10, 10));

        widthProperty().addListener((obs, o, n) -> resizeAndRedraw());
        heightProperty().addListener((obs, o, n) -> resizeAndRedraw());

        setMinHeight(190);
        setMinWidth(240);

        setCenterText("Roles", "");
    }

    public void setCenterText(String title, String sub) {
        centerTitle.setText(title == null ? "" : title);
        centerSub.setText(sub == null ? "" : sub);
    }

    public void setData(Map<String, Integer> data) {
        this.data = (data == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(data);
        rebuildLegend();
        redraw();
    }

    public void setLegendEnabled(boolean enabled) {
        this.legendEnabled = enabled;
        rebuildLegend();
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
        double legendSpace = legend.isVisible() ? 52 : 0;
        double chartH = Math.max(1, h - legendSpace);
        double size = Math.min(w, chartH) - pad * 2;
        double cx = w / 2.0;
        double cy = chartH / 2.0;

        double outerR = size / 2.0;
        double innerR = outerR * 0.58;

        int total = 0;
        for (int v : data.values()) total += Math.max(0, v);

        // base ring
        g.setStroke(Color.rgb(255, 255, 255, 0.10));
        g.setLineWidth(outerR - innerR);
        g.strokeOval(cx - (outerR + innerR) / 2, cy - (outerR + innerR) / 2,
                (outerR + innerR), (outerR + innerR));

        if (total <= 0) return;

        // palette aligned with your theme tokens (hardcoded once; keep lightweight)
        double start = -90; // top
        int idx = 0;
        int nonZero = 0;
        for (int v : data.values()) if (v > 0) nonZero++;
        double gapDeg = nonZero > 1 ? 2.4 : 0.0;

        for (var e : data.entrySet()) {
            int v = Math.max(0, e.getValue());
            if (v == 0) continue;

            double sweepRaw = 360.0 * (v / (double) total);
            double sweep = Math.max(0.8, sweepRaw - gapDeg);
            g.setStroke(colorForIndex(idx));
            g.setLineWidth(outerR - innerR);
            // arc bounds are the midpoint radius circle
            double rMid = (outerR + innerR) / 2.0;
            double d = rMid * 2.0;
            g.strokeArc(cx - rMid, cy - rMid, d, d, start + gapDeg / 2.0, sweep, javafx.scene.shape.ArcType.OPEN);

            start += sweepRaw;
            idx++;
        }
    }

    private void rebuildLegend() {
        legend.getChildren().clear();

        int total = 0;
        for (int v : data.values()) total += Math.max(0, v);
        boolean showLegend = legendEnabled && total > 0;
        legend.setVisible(showLegend);
        legend.setManaged(showLegend);
        if (!showLegend) return;

        int idx = 0;
        for (var e : data.entrySet()) {
            int v = Math.max(0, e.getValue());
            if (v == 0) {
                idx++;
                continue;
            }
            double pct = (v * 100.0) / Math.max(1, total);
            Circle dot = new Circle(4.0, colorForIndex(idx));

            Label text = new Label(e.getKey() + ": " + v + " (" + String.format("%.0f%%", pct) + ")");
            text.getStyleClass().add("donut-legend-item");
            text.setContentDisplay(ContentDisplay.LEFT);

            HBox row = new HBox(6, dot, text);
            row.setAlignment(Pos.CENTER_LEFT);
            legend.getChildren().add(row);
            idx++;
        }
    }

    private static Color colorForIndex(int idx) {
        Color[] palette = new Color[] {
                Color.web("#4f8cff"), // accent
                Color.web("#22c55e"), // success
                Color.web("#f59e0b"), // warning
                Color.web("#ef4444"), // danger
                Color.web("#a78bfa")  // secondary
        };
        return palette[Math.floorMod(idx, palette.length)];
    }
}
