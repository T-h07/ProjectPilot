package com.projectpilot.ui.components;

import com.projectpilot.core.PageId;
import com.projectpilot.core.AppState;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public class Sidebar extends VBox {

    public Sidebar(Consumer<PageId> onNavigate, Runnable onLogout, AppState appState) {
        setPadding(new Insets(14));
        setSpacing(10);

        Button dashboard = nav("Dashboard", PageId.DASHBOARD, onNavigate);
        Button projects = nav("Projects", PageId.PROJECTS, onNavigate);
        Button overview = nav("Project Overview", PageId.PROJECT_OVERVIEW, onNavigate);
        Button tasks = nav("Tasks", PageId.TASKS, onNavigate);
        Button gantt = nav("Gantt", PageId.GANTT, onNavigate);
        Button team = nav("Team", PageId.TEAM, onNavigate);
        Button history = nav("History", PageId.HISTORY, onNavigate);
        Button export = nav("Export", PageId.EXPORT_REPORT, onNavigate);

        // spacer -> pushes admin/logout to bottom
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button admin = nav("Admin", PageId.ADMIN, onNavigate);
        admin.setVisible(appState != null && appState.isAdmin());
        admin.managedProperty().bind(admin.visibleProperty());

        Button logout = new Button("Logout");
        logout.getStyleClass().add("primary");
        logout.setMaxWidth(Double.MAX_VALUE);
        logout.setOnAction(e -> onLogout.run());

        getChildren().addAll(
                dashboard, projects, overview, tasks, gantt, team, history, export,
                spacer,
                admin,
                logout
        );
    }

    private static Button nav(String text, PageId id, Consumer<PageId> onNavigate) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.getStyleClass().add("secondary");
        b.setOnAction(e -> onNavigate.accept(id));
        return b;
    }
}
