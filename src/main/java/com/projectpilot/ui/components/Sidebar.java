package com.projectpilot.ui.components;

import com.projectpilot.core.AppState;
import com.projectpilot.core.PageId;
import com.projectpilot.security.AccessPolicy;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public class Sidebar extends VBox {

    private final AccessPolicy policy = new AccessPolicy();

    public Sidebar(Consumer<PageId> onNavigate, Runnable onLogout, AppState appState) {
        setPadding(new Insets(16));
        setSpacing(10);
        getStyleClass().add("sidebar");

        Button dashboard = nav("Dashboard", PageId.DASHBOARD, onNavigate);
        Button projects   = nav("Projects", PageId.PROJECTS, onNavigate);
        Button overview   = nav("Project Overview", PageId.PROJECT_OVERVIEW, onNavigate);
        Button tasks      = nav("Tasks", PageId.TASKS, onNavigate);
        Button gantt      = nav("Gantt", PageId.GANTT, onNavigate);
        Button team       = nav("Team", PageId.TEAM, onNavigate);
        Button messages   = nav("Messages", PageId.MESSAGES, onNavigate);
        Button history    = nav("History", PageId.HISTORY, onNavigate);
        Button export     = nav("Export", PageId.EXPORT_REPORT, onNavigate);

        StackPane messagesWrap = wrapWithDot(messages, appState);

        bindVisible(dashboard, canShow(appState, PageId.DASHBOARD));
        bindVisible(projects,   canShow(appState, PageId.PROJECTS));
        bindVisible(overview,   canShow(appState, PageId.PROJECT_OVERVIEW));
        bindVisible(tasks,      canShow(appState, PageId.TASKS));
        bindVisible(gantt,      canShow(appState, PageId.GANTT));
        bindVisible(team,       canShow(appState, PageId.TEAM));
        bindVisible(messagesWrap, canShow(appState, PageId.MESSAGES));
        bindVisible(history,    canShow(appState, PageId.HISTORY));
        bindVisible(export,     canShow(appState, PageId.EXPORT_REPORT));

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button admin = nav("Admin", PageId.ADMIN, onNavigate);
        bindVisible(admin, canShow(appState, PageId.ADMIN));

        Button logout = new Button("Logout");
        logout.getStyleClass().add("primary");
        logout.setMaxWidth(Double.MAX_VALUE);
        logout.setOnAction(e -> onLogout.run());

        getChildren().addAll(
                dashboard, projects, overview, tasks, gantt, team, messagesWrap, history, export,
                spacer,
                admin,
                logout
        );
    }

    /** Keep it because other code may call sidebar.rebuild(). */
    public void rebuild() {
        requestLayout();
    }

    private BooleanBinding canShow(AppState appState, PageId id) {
        return Bindings.createBooleanBinding(
                () -> policy.canAccessPage(appState, id),
                appState.sessionProperty(),
                appState.selectedProjectProperty(),
                appState.currentProjectRoleProperty()
        );
    }

    private void bindVisible(Button b, BooleanBinding show) {
        b.visibleProperty().bind(show);
        b.managedProperty().bind(b.visibleProperty());
    }

    private void bindVisible(StackPane p, BooleanBinding show) {
        p.visibleProperty().bind(show);
        p.managedProperty().bind(p.visibleProperty());
    }

    private StackPane wrapWithDot(Button button, AppState appState) {
        Region dot = new Region();
        dot.getStyleClass().add("sidebar-dot");
        dot.visibleProperty().bind(appState.unreadMessagesProperty().greaterThan(0));
        dot.managedProperty().bind(dot.visibleProperty());

        StackPane wrap = new StackPane(button, dot);
        wrap.setMaxWidth(Double.MAX_VALUE);
        StackPane.setAlignment(dot, Pos.TOP_RIGHT);
        StackPane.setMargin(dot, new Insets(8, 8, 0, 0));
        return wrap;
    }

    private static Button nav(String text, PageId id, Consumer<PageId> onNavigate) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.getStyleClass().add("secondary");
        b.setOnAction(e -> onNavigate.accept(id));
        return b;
    }
}
