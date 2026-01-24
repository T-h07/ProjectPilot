package com.projectpilot;

import com.projectpilot.core.AppState;
import com.projectpilot.core.PageId;
import com.projectpilot.core.Router;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.data.SampleData;
import com.projectpilot.ui.MainLayout;
import com.projectpilot.ui.pages.*;
import javafx.scene.text.Font;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;


public class Main extends Application {

    @Override
    public void start(Stage stage) {
        InMemoryStore store = new InMemoryStore();
        SampleData.seed(store);
        Font.loadFont(getClass().getResourceAsStream("/fonts/Inter-Regular.ttf"), 12);
        Font.loadFont(getClass().getResourceAsStream("/fonts/Inter-SemiBold.ttf"), 12);

        AppState appState = new AppState();
        if (!store.getProjects().isEmpty()) appState.setSelectedProject(store.getProjects().get(0));

        Router router = new Router();
        router.register(PageId.DASHBOARD, () -> new DashboardPage(store, appState));
        router.register(PageId.PROJECTS, () -> new ProjectsPage(store, appState));
        router.register(PageId.PROJECT_OVERVIEW, () -> new ProjectOverviewPage(store, appState));
        router.register(PageId.TASKS, () -> new TasksPage(store, appState));
        router.register(PageId.GANTT, () -> new GanttPage(store, appState));
        router.register(PageId.TEAM, () -> new TeamPage(store, appState));
        router.register(PageId.EXPORT_REPORT, () -> new ExportReportPage(store, appState));

        MainLayout root = new MainLayout(router, store, appState);

        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());

        stage.setTitle("ProjectPilot");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
