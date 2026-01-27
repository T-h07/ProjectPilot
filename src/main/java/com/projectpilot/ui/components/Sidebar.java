package com.projectpilot.ui.components;

import com.projectpilot.core.AppState;
import com.projectpilot.core.PageId;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public class Sidebar extends VBox {

    public Sidebar(Consumer<PageId> onNavigate, AppState appState) {
        setSpacing(8);
        setPadding(new Insets(12));
        getStyleClass().add("sidebar");

        getChildren().addAll(
                navButton("Dashboard", PageId.DASHBOARD, onNavigate),
                navButton("Projects", PageId.PROJECTS, onNavigate),
                navButton("Project Overview", PageId.PROJECT_OVERVIEW, onNavigate),
                navButton("Tasks", PageId.TASKS, onNavigate),
                navButton("Gantt", PageId.GANTT, onNavigate),
                navButton("Team", PageId.TEAM, onNavigate),

                // NEW:
                navButton("History", PageId.HISTORY, onNavigate),

                navButton("Export", PageId.EXPORT_REPORT, onNavigate)

        );
    }



    private Button navButton(String text, PageId page, Consumer<PageId> onNavigate) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.getStyleClass().add("nav-button");
        b.setOnAction(e -> onNavigate.accept(page));
        return b;
    }
}
