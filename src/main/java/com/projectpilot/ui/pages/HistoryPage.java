package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.Member;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.TaskStatus;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;

public class HistoryPage extends BorderPane {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final InMemoryStore store;
    private final AppState appState;

    private final Label header = new Label("History");

    private final ListView<Project> historyList = new ListView<>();

    // Details
    private final Label name = new Label("-");
    private final Label dates = new Label("-");
    private final Label completed = new Label("-");
    private final Label summary = new Label("-");

    private final TableView<Task> tasksTable = new TableView<>();

    private final Button restoreBtn = new Button("Restore");
    private final Button deleteBtn = new Button("Delete");

    public HistoryPage(InMemoryStore store, AppState appState) {
        this.store = store;
        this.appState = appState;

        setPadding(new Insets(16));

        header.getStyleClass().add("page-title");

        // Left (history list)
        historyList.setItems(store.getHistoryProjects());
        historyList.getStyleClass().add("card");
        historyList.setPrefWidth(360);

        historyList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Project p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) {
                    setText(null);
                    return;
                }
                String done = (p.getCompletedDate() == null) ? "" : (" • " + p.getCompletedDate().format(DATE));
                setText(p.getName() + done);
            }
        });

        // Right details card
        VBox detailsCard = new VBox(12);
        detailsCard.getStyleClass().add("card");
        detailsCard.setPadding(new Insets(16));

        Label detailsTitle = new Label("Completed Project Details");
        detailsTitle.getStyleClass().add("panel-title");

        name.getStyleClass().add("muted");
        dates.getStyleClass().add("muted");
        completed.getStyleClass().add("muted");
        summary.getStyleClass().add("muted");

        GridPane info = new GridPane();
        info.setHgap(12);
        info.setVgap(10);

        info.add(labelBold("Name:"), 0, 0);
        info.add(name, 1, 0);

        info.add(labelBold("Dates:"), 0, 1);
        info.add(dates, 1, 1);

        info.add(labelBold("Completed:"), 0, 2);
        info.add(completed, 1, 2);

        info.add(labelBold("Summary:"), 0, 3);
        info.add(summary, 1, 3);

        // Tasks table
        setupTasksTable();
        VBox.setVgrow(tasksTable, Priority.ALWAYS);

        // Buttons
        restoreBtn.getStyleClass().add("secondary");
        deleteBtn.getStyleClass().add("secondary");

        HBox actions = new HBox(10, restoreBtn, deleteBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        detailsCard.getChildren().addAll(detailsTitle, info, new Separator(), labelBold("Tasks"), tasksTable, actions);

        HBox main = new HBox(14, wrapCard(historyList), detailsCard);
        HBox.setHgrow(detailsCard, Priority.ALWAYS);

        setCenter(main);

        // wiring
        historyList.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> showProject(n));

        restoreBtn.setOnAction(e -> {
            Project p = historyList.getSelectionModel().getSelectedItem();
            if (p == null) return;
            store.restoreProject(p);
            appState.setSelectedProject(p); // jump user back into active context if they want
        });

        deleteBtn.setOnAction(e -> {
            Project p = historyList.getSelectionModel().getSelectedItem();
            if (p == null) return;
            if (confirm("Delete project?", "Hard delete: permanently removes this project from the DB.")) {
                store.deleteProject(p);
                showProject(null);
            }
        });

        showProject(null);
    }

    private void setupTasksTable() {
        tasksTable.getStyleClass().add("pp-table");
        tasksTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tasksTable.setFixedCellSize(30);

        TableColumn<Task, String> titleCol = new TableColumn<>("Task");
        titleCol.setCellValueFactory(cd -> cd.getValue().titleProperty());

        TableColumn<Task, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue().getStatus() == null ? "-" : cd.getValue().getStatus().name()
        ));
        statusCol.setMaxWidth(140);

        TableColumn<Task, String> whoCol = new TableColumn<>("Assignee");
        whoCol.setCellValueFactory(cd -> {
            Member m = cd.getValue().getAssignee();
            return new javafx.beans.property.SimpleStringProperty(m == null ? "-" : m.getName());
        });
        whoCol.setMaxWidth(220);

        TableColumn<Task, String> dueCol = new TableColumn<>("Due");
        dueCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue().getDueDate() == null ? "-" : cd.getValue().getDueDate().format(DATE)
        ));
        dueCol.setMaxWidth(140);

        tasksTable.getColumns().setAll(titleCol, statusCol, whoCol, dueCol);
        tasksTable.setPlaceholder(new Label("No tasks."));
    }

    private void showProject(Project p) {
        if (p == null) {
            name.setText("-");
            dates.setText("-");
            completed.setText("-");
            summary.setText("-");
            tasksTable.setItems(null);
            return;
        }

        name.setText(p.getName());

        String ds = (p.getStartDate() == null ? "-" : p.getStartDate().format(DATE));
        String de = (p.getEndDate() == null ? "-" : p.getEndDate().format(DATE));
        dates.setText(ds + " → " + de);

        completed.setText(p.getCompletedDate() == null ? "-" : p.getCompletedDate().format(DATE));

        long total = p.getTasks().size();
        long done = p.getTasks().stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
        summary.setText("Tasks: " + total + " | Done: " + done);

        tasksTable.setItems(p.getTasks());
    }

    private static Label labelBold(String s) {
        Label l = new Label(s);
        l.getStyleClass().add("label-strong");
        return l;
    }

    private static VBox wrapCard(javafx.scene.Node node) {
        VBox v = new VBox(node);
        VBox.setVgrow(node, Priority.ALWAYS);
        v.getStyleClass().add("card");
        v.setPadding(new Insets(12));
        v.setPrefWidth(380);
        return v;
    }

    private boolean confirm(String header, String msg) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("ProjectPilot");
        a.setHeaderText(header);
        a.setContentText(msg);
        return a.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }
}
