package com.projectpilot.ui.pages.admin.widgets;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public final class MiniTrendCard extends StackPane {

    private final Label title = new Label();
    private final Label value = new Label();
    private final Label subtitle = new Label();

    private final SparklineView sparkline = new SparklineView();

    private final VBox detail = new VBox(4);
    private final Label d1 = new Label();
    private final Label d2 = new Label();
    private final Label d3 = new Label();

    private final DecimalFormat df = new DecimalFormat("#,##0.##");

    public MiniTrendCard(String titleText, String valueText, String subtitleText, List<Double> series) {
        getStyleClass().addAll("pp-hover-card", "mini-trend-card");
        setMinHeight(110);
        setPrefHeight(110);
        setMaxWidth(Double.MAX_VALUE);

        title.getStyleClass().add("mini-trend-title");
        value.getStyleClass().add("mini-trend-value");
        subtitle.getStyleClass().add("mini-trend-sub");

        title.setText(titleText);
        value.setText(valueText);
        subtitle.setText(subtitleText);

        sparkline.setPrefHeight(46);
        sparkline.setMaxWidth(Double.MAX_VALUE);

        VBox content = new VBox(6,
                title,
                value,
                subtitle,
                sparkline
        );
        content.setAlignment(Pos.TOP_LEFT);

        // Detail overlay
        detail.getStyleClass().add("pp-detail");
        d1.getStyleClass().add("muted");
        d2.getStyleClass().add("muted");
        d3.getStyleClass().add("muted");
        detail.getChildren().addAll(d1, d2, d3);

        StackPane.setAlignment(detail, Pos.BOTTOM_LEFT);
        StackPane.setMargin(detail, new Insets(10));

        getChildren().addAll(content, detail);

        setSeries(series);

        HoverLift.install(this, detail);
    }

    public void setSeries(List<Double> series) {
        List<Double> cleaned = new ArrayList<>();
        if (series != null) {
            for (Double d : series) if (d != null && Double.isFinite(d)) cleaned.add(d);
        }
        if (cleaned.size() < 2) {
            cleaned = List.of(0.0, 0.0);
        }
        sparkline.values().setAll(cleaned);
        updateDetail(cleaned);
    }

    private void updateDetail(List<Double> s) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double sum = 0;

        for (double v : s) {
            min = Math.min(min, v);
            max = Math.max(max, v);
            sum += v;
        }
        double avg = sum / s.size();
        double last = s.get(s.size() - 1);

        d1.setText("Last: " + df.format(last) + "   Avg: " + df.format(avg));
        d2.setText("Min:  " + df.format(min)  + "   Max: " + df.format(max));
        d3.setText("Points: " + s.size());
    }
}
