package com.projectpilot;

import com.projectpilot.core.AppState;
import com.projectpilot.core.PageId;
import com.projectpilot.core.Router;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.data.PersistenceService;
import com.projectpilot.data.SampleData;
import com.projectpilot.ui.MainLayout;
import com.projectpilot.ui.pages.*;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public class Main extends Application {

    private InMemoryStore store;

    @Override
    public void start(Stage stage) {
        store = new InMemoryStore();

        // ✅ Load saved data if present; otherwise seed once and save
        PersistenceService.loadOrSeed(store, () -> SampleData.seed(store));

        Font.loadFont(getClass().getResourceAsStream("/fonts/Inter-Regular.ttf"), 12);
        Font.loadFont(getClass().getResourceAsStream("/fonts/Inter-SemiBold.ttf"), 12);

        AppState appState = new AppState();

        // Pick selected project from active projects if available; else from history (optional)
        if (!store.getProjects().isEmpty()) {
            appState.setSelectedProject(store.getProjects().get(0));
        } else if (!store.getHistoryProjects().isEmpty()) {
            appState.setSelectedProject(store.getHistoryProjects().get(0));
        }

        Router router = new Router();
        router.register(PageId.DASHBOARD, () -> new DashboardPage(store, appState));
        router.register(PageId.PROJECTS, () -> new ProjectsPage(store, appState));
        router.register(PageId.PROJECT_OVERVIEW, () -> new ProjectOverviewPage(store, appState));
        router.register(PageId.TASKS, () -> new TasksPage(store, appState));
        router.register(PageId.GANTT, () -> new GanttPage(store, appState));
        router.register(PageId.TEAM, () -> new TeamPage(store, appState));
        router.register(PageId.HISTORY, () -> new HistoryPage(store, appState));
        router.register(PageId.EXPORT_REPORT, () -> new ExportReportPage(store, appState));

        MainLayout root = new MainLayout(router, store, appState);
        root.getStyleClass().add("pp-root");


        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());

        stage.setTitle("ProjectPilot");
        stage.setScene(scene);

        // ✅ Save on window close
        stage.setOnCloseRequest(e -> PersistenceService.safeSave(store));

        stage.show();
    }

    @Override
    public void stop() {
        // ✅ Final save (covers normal shutdown)
        PersistenceService.safeSave(store);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
