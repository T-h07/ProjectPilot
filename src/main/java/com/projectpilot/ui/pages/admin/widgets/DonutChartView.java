package com.projectpilot.ui.pages.admin.widgets;


import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DonutChartView extends StackPane {

    private final Canvas canvas = new Canvas();
    private final Label centerTitle = new Label();
    private final Label centerSub = new Label();

    private Map<String, Integer> data = new LinkedHashMap<>();

    public DonutChartView() {
        getStyleClass().addAll("pp-card", "donut-card");

        centerTitle.getStyleClass().add("donut-center-title");
        centerSub.getStyleClass().add("donut-center-sub");

        VBox center = new VBox(2, centerTitle, centerSub);
        center.setMouseTransparent(true);
        center.setFillWidth(true);
        center.setMaxWidth(Double.MAX_VALUE);
        centerTitle.setMaxWidth(Double.MAX_VALUE);
        centerSub.setMaxWidth(Double.MAX_VALUE);
        centerTitle.setTextAlignment(TextAlignment.CENTER);
        centerSub.setTextAlignment(TextAlignment.CENTER);

        getChildren().addAll(canvas, center);

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
        double size = Math.min(w, h) - pad * 2;
        double cx = w / 2.0;
        double cy = h / 2.0;

        double outerR = size / 2.0;
        double innerR = outerR * 0.62;

        int total = 0;
        for (int v : data.values()) total += Math.max(0, v);

        // base ring
        g.setStroke(Color.rgb(255, 255, 255, 0.10));
        g.setLineWidth(outerR - innerR);
        g.strokeOval(cx - (outerR + innerR) / 2, cy - (outerR + innerR) / 2,
                (outerR + innerR), (outerR + innerR));

        if (total <= 0) return;

        // palette aligned with your theme tokens (hardcoded once; keep lightweight)
        Color[] palette = new Color[] {
                Color.web("#4f8cff"), // accent
                Color.web("#22c55e"), // success
                Color.web("#f59e0b"), // warning
                Color.web("#ef4444"), // danger
                Color.web("#f5c542")  // gold
        };

        double start = -90; // top
        int idx = 0;

        for (var e : data.entrySet()) {
            int v = Math.max(0, e.getValue());
            if (v == 0) continue;

            double sweep = 360.0 * (v / (double) total);
            g.setStroke(palette[idx % palette.length]);
            g.setLineWidth(outerR - innerR);
            // arc bounds are the midpoint radius circle
            double rMid = (outerR + innerR) / 2.0;
            double d = rMid * 2.0;
            g.strokeArc(cx - rMid, cy - rMid, d, d, start, sweep, javafx.scene.shape.ArcType.OPEN);

            start += sweep;
            idx++;
        }
    }
}
