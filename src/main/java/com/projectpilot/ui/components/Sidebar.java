package com.projectpilot.ui.components;

import com.projectpilot.core.AppState;
import com.projectpilot.core.PageId;
import com.projectpilot.security.AccessPolicy;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.Node;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Sidebar extends VBox {

    private final AccessPolicy policy = new AccessPolicy();
    private final List<NavItem> navItems = new ArrayList<>();
    private final Label headerLabel = new Label("Navigation");
    private final Button toggleBtn = new Button();
    private final VBox navList = new VBox(6);
    private boolean collapsed;

    public Sidebar(Consumer<PageId> onNavigate, Runnable onLogout, AppState appState) {
        setPadding(new Insets(12));
        setSpacing(8);
        getStyleClass().add("sidebar");

        headerLabel.getStyleClass().add("sidebar-header-label");
        toggleBtn.getStyleClass().add("sidebar-toggle");
        toggleBtn.setText("<<");
        toggleBtn.setOnAction(e -> setCollapsed(!collapsed));

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(8, headerLabel, headerSpacer, toggleBtn);
        header.getStyleClass().add("sidebar-header");
        header.setAlignment(Pos.CENTER_LEFT);

        Button dashboard = nav("Dashboard", "Dashboard", PageId.DASHBOARD, onNavigate);
        Button activity  = nav("Activity", "Activity", PageId.ACTIVITY, onNavigate);
        Button projects  = nav("Projects", "Projects", PageId.PROJECTS, onNavigate);
        Button overview  = nav("Project Overview", "ProjectOverview", PageId.PROJECT_OVERVIEW, onNavigate);
        Button tasks     = nav("Tasks", "Tasks", PageId.TASKS, onNavigate);
        Button gantt     = nav("Gantt", "Gantt", PageId.GANTT, onNavigate);
        Button calendar  = nav("Calendar", "Calendar", PageId.CALENDAR, onNavigate);
        Button resources = nav("Files & Links", "FilesLinks", PageId.RESOURCES, onNavigate);
        Button notes     = nav("Notes", "Notes", PageId.NOTES, onNavigate);
        Button team      = nav("Team", "Team", PageId.TEAM, onNavigate);
        Button meetings  = nav("Meetings", "Meetings", PageId.MEETINGS, onNavigate);
        Button messages  = nav("Messages", "Messages", PageId.MESSAGES, onNavigate);
        Button history   = nav("History", "History", PageId.HISTORY, onNavigate);
        Button export    = nav("Export", "Export", PageId.EXPORT_REPORT, onNavigate);

        StackPane messagesWrap = wrapWithDot(messages, appState);

        bindActive(dashboard, PageId.DASHBOARD, appState);
        bindActive(activity, PageId.ACTIVITY, appState);
        bindActive(projects, PageId.PROJECTS, appState);
        bindActive(overview, PageId.PROJECT_OVERVIEW, appState);
        bindActive(tasks, PageId.TASKS, appState);
        bindActive(gantt, PageId.GANTT, appState);
        bindActive(calendar, PageId.CALENDAR, appState);
        bindActive(resources, PageId.RESOURCES, appState);
        bindActive(notes, PageId.NOTES, appState);
        bindActive(team, PageId.TEAM, appState);
        bindActive(meetings, PageId.MEETINGS, appState);
        bindActive(messages, PageId.MESSAGES, appState);
        bindActive(history, PageId.HISTORY, appState);
        bindActive(export, PageId.EXPORT_REPORT, appState);

        bindVisible(dashboard, canShow(appState, PageId.DASHBOARD));
        bindVisible(activity,  canShow(appState, PageId.ACTIVITY));
        bindVisible(projects,   canShow(appState, PageId.PROJECTS));
        bindVisible(overview,   canShow(appState, PageId.PROJECT_OVERVIEW));
        bindVisible(tasks,      canShow(appState, PageId.TASKS));
        bindVisible(gantt,      canShow(appState, PageId.GANTT));
        bindVisible(calendar,   canShow(appState, PageId.CALENDAR));
        bindVisible(resources,  canShow(appState, PageId.RESOURCES));
        bindVisible(notes,      canShow(appState, PageId.NOTES));
        bindVisible(team,       canShow(appState, PageId.TEAM));
        bindVisible(meetings,   canShow(appState, PageId.MEETINGS));
        bindVisible(messagesWrap, canShow(appState, PageId.MESSAGES));
        bindVisible(history,    canShow(appState, PageId.HISTORY));
        bindVisible(export,     canShow(appState, PageId.EXPORT_REPORT));

        navList.getStyleClass().add("sidebar-list");
        navList.getChildren().addAll(
                dashboard, activity, projects, overview, tasks, gantt, calendar, resources, notes, team, meetings, messagesWrap, history, export
        );

        ScrollPane navScroll = new ScrollPane(navList);
        navScroll.getStyleClass().add("sidebar-scroll");
        navScroll.setFitToWidth(true);
        navScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        navScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(navScroll, Priority.ALWAYS);

        Button admin = nav("Admin", "Admin", PageId.ADMIN, onNavigate);
        bindVisible(admin, canShow(appState, PageId.ADMIN));
        bindActive(admin, PageId.ADMIN, appState);

        Button logout = action("Logout", "Logout", onLogout);
        logout.getStyleClass().add("primary");

        getChildren().addAll(
                header,
                navScroll,
                admin,
                logout
        );

        setCollapsed(false);
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

    private void bindActive(Button button, PageId id, AppState appState) {
        updateActive(button, appState.getCurrentPage() == id);
        appState.currentPageProperty().addListener((obs, oldValue, newValue) ->
                updateActive(button, newValue == id)
        );
    }

    private void updateActive(Button button, boolean active) {
        if (active) {
            if (!button.getStyleClass().contains("nav-button-active")) {
                button.getStyleClass().add("nav-button-active");
            }
        } else {
            button.getStyleClass().remove("nav-button-active");
        }
    }

    private Button nav(String text, String iconName, PageId id, Consumer<PageId> onNavigate) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.getStyleClass().add("nav-button");
        b.setAlignment(Pos.CENTER_LEFT);
        b.setTooltip(new Tooltip(text));
        navItems.add(new NavItem(b, text, loadIcon(iconName, text)));
        b.setOnAction(e -> onNavigate.accept(id));
        return b;
    }

    private Button action(String text, String iconName, Runnable action) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.getStyleClass().add("nav-button");
        b.setAlignment(Pos.CENTER_LEFT);
        b.setTooltip(new Tooltip(text));
        navItems.add(new NavItem(b, text, loadIcon(iconName, text)));
        b.setOnAction(e -> action.run());
        return b;
    }

    private Node loadIcon(String iconName, String label) {
        if (iconName != null && !iconName.isBlank()) {
            String[] paths = new String[] {
                    "/icons/Sidebar/" + iconName + ".png",
                    "/icons/sidebar/" + iconName.toLowerCase() + ".png"
            };
            for (String path : paths) {
                try (InputStream in = Sidebar.class.getResourceAsStream(path)) {
                    if (in != null) {
                        ImageView view = new ImageView(new Image(in));
                        view.setFitWidth(56);
                        view.setFitHeight(56);
                        view.setPreserveRatio(true);
                        view.setSmooth(true);
                        view.getStyleClass().add("sidebar-icon");

                        ColorAdjust adjust = new ColorAdjust();
                        adjust.setBrightness(0.35);
                        adjust.setContrast(0.2);
                        DropShadow glow = new DropShadow(8, Color.rgb(255, 255, 255, 0.35));
                        glow.setInput(adjust);
                        view.setEffect(glow);

                        StackPane wrap = new StackPane(view);
                        wrap.getStyleClass().add("sidebar-icon-wrap");
                        return wrap;
                    }
                } catch (Exception e) { com.projectpilot.util.AppLog.warn("sidebar", "Failed to load icon " + path + ": " + (e == null ? "" : e.getMessage())); }
            }
        }

        String fallback = (label == null || label.isBlank()) ? "?" : label.substring(0, 1).toUpperCase();
        Label text = new Label(fallback);
        text.getStyleClass().add("sidebar-icon-fallback");
        StackPane wrap = new StackPane(text);
        wrap.getStyleClass().add("sidebar-icon-wrap");
        return wrap;
    }

    private void setCollapsed(boolean value) {
        collapsed = value;
        if (collapsed) {
            if (!getStyleClass().contains("sidebar-collapsed")) getStyleClass().add("sidebar-collapsed");
        } else {
            getStyleClass().remove("sidebar-collapsed");
        }

        headerLabel.setVisible(!collapsed);
        headerLabel.setManaged(!collapsed);
        toggleBtn.setText(collapsed ? ">>" : "<<");
        navList.setAlignment(collapsed ? Pos.TOP_CENTER : Pos.TOP_LEFT);

        for (NavItem item : navItems) {
            Button b = item.button();
            if (collapsed) {
                b.setText("");
                b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                b.setGraphic(item.icon());
                b.setAlignment(Pos.CENTER);
            } else {
                b.setText(item.label());
                b.setGraphic(null);
                b.setContentDisplay(ContentDisplay.TEXT_ONLY);
                b.setAlignment(Pos.CENTER_LEFT);
            }
        }
    }

    private record NavItem(Button button, String label, Node icon) {}
}
