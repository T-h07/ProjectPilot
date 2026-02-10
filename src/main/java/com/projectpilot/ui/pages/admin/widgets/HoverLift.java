package com.projectpilot.ui.pages.admin.widgets;

import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.util.Duration;

public final class HoverLift {
    private HoverLift() {}

    public static void install(Region card) {
        install(card, null);
    }

    public static void install(Region card, Node hoverDetails) {
        if (card == null) return;

        card.setPickOnBounds(true);

        DropShadow normal = new DropShadow(16, Color.rgb(0, 0, 0, 0.18));
        normal.setOffsetY(6);

        DropShadow hover = new DropShadow(22, Color.rgb(0, 0, 0, 0.30));
        hover.setOffsetY(10);

        card.setEffect(normal);

        TranslateTransition up = new TranslateTransition(Duration.millis(140), card);
        up.setToY(-4);

        TranslateTransition down = new TranslateTransition(Duration.millis(140), card);
        down.setToY(0);

        if (hoverDetails != null) {
            hoverDetails.setVisible(false);
            hoverDetails.setManaged(false);
            hoverDetails.setMouseTransparent(true);
        }

        card.setOnMouseEntered(e -> {
            down.stop();
            up.playFromStart();
            card.setEffect(hover);
            if (hoverDetails != null) hoverDetails.setVisible(true);
        });

        card.setOnMouseExited(e -> {
            up.stop();
            down.playFromStart();
            card.setEffect(normal);
            if (hoverDetails != null) hoverDetails.setVisible(false);
        });
    }
}
