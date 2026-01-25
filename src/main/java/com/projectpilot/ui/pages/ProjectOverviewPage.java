package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.Milestone;
import com.projectpilot.model.Phase;
import com.projectpilot.model.Project;
import com.projectpilot.model.enums.TaskStatus;
import com.projectpilot.service.ProgressService;
import com.projectpilot.ui.dialogs.AddMilestoneDialog;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;

public class ProjectOverviewPage extends VBox {

    private final ProgressService progressService = new ProgressService();

    private final Label header = new Label("Project Overview");
    private final Label sub = new Label("");
    private final Label progress = new Label("-");
    private final Label counts = new Label("-");

    private final TableView<Phase> phaseTable = new TableView<>();
    private final ListView<Milestone> milestoneList = new ListView<>();

    private Project boundProject;

    private final ListChangeListener<?> tasksListener = c -> {
        // Update computed values + phase table computed columns
        if (boundProject != null) refresh(boundProject);
        phaseTable.refresh();
    };

    private final ListChangeListener<?> milestonesListener = c -> {
        // list is already bound, but refresh text/selection if needed
        milestoneList.refresh();
    };

    public ProjectOverviewPage(InMemoryStore store, AppState appState) {
        setPadding(new Insets(16));
        setSpacing(14);

        header.getStyleClass().add("page-title");
        sub.getStyleClass().add("muted");

        VBox topCard = new VBox(8, header, sub, progress, counts);
        topCard.getStyleClass().add("card");

        progress.setStyle("-fx-font-size: 22px; -fx-font-weight: 800;");
        counts.getStyleClass().add("muted");

        // ---------- Phase table ----------
        TableColumn<Phase, String> phaseName = new TableColumn<>("Phase");
        phaseName.setCellValueFactory(c -> c.getValue().nameProperty());
        phaseName.setPrefWidth(260);

        TableColumn<Phase, String> phaseProg = new TableColumn<>("Progress");
        phaseProg.setPrefWidth(100);
        phaseProg.setCellValueFactory(c ->
                Bindings.createStringBinding(() -> {
                    Project p = appState.getSelectedProject();
                    if (p == null) return "-";
                    int pct = phaseProgressPercent(p, c.getValue());
                    return pct + "%";
                })
        );

        TableColumn<Phase, String> phaseOpen = new TableColumn<>("Open Tasks");
        phaseOpen.setPrefWidth(110);
        phaseOpen.setCellValueFactory(c ->
                Bindings.createStringBinding(() -> {
                    Project p = appState.getSelectedProject();
                    if (p == null) return "-";
                    long open = p.getTasks().stream()
                            .filter(t -> t.getPhase() == c.getValue())
                            .filter(t -> t.getStatus() != TaskStatus.DONE)
                            .count();
                    return String.valueOf(open);
                })
        );

        phaseTable.getColumns().setAll(phaseName, phaseProg, phaseOpen);
        phaseTable.setPrefHeight(260);
        phaseTable.getStyleClass().add("pp-table");

        Label phasesTitle = new Label("Phases");
        phasesTitle.getStyleClass().add("section-title");

        VBox phasesCard = new VBox(10, phasesTitle, phaseTable);
        phasesCard.getStyleClass().add("card");

        // ---------- Milestones ----------
        milestoneList.setPrefHeight(220);
        milestoneList.setCellFactory(lv -> new ListCell<>() {
            private final CheckBox cb = new CheckBox();
            private Milestone bound;

            @Override
            protected void updateItem(Milestone item, boolean empty) {
                super.updateItem(item, empty);

                if (bound != null) {
                    cb.selectedProperty().unbindBidirectional(bound.completedProperty());
                    bound = null;
                }

                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }

                bound = item;
                cb.selectedProperty().bindBidirectional(item.completedProperty());

                String name = safe(item.nameProperty().get());
                String due = (item.dueDateProperty().get() == null)
                        ? "-"
                        : item.dueDateProperty().get().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

                cb.setText(name + "  •  due " + due);
                setGraphic(cb);
            }
        });

        Button addMilestone = new Button("Add Milestone");
        addMilestone.getStyleClass().add("primary");
        addMilestone.setOnAction(e -> {
            Project p = appState.getSelectedProject();
            if (p == null) return;

            AddMilestoneDialog d = new AddMilestoneDialog();
            d.showAndWait().ifPresent(m -> store.addMilestone(p, m));
        });

        Label msTitle = new Label("Milestones");
        msTitle.getStyleClass().add("section-title");

        VBox milestoneCard = new VBox(10, msTitle, milestoneList, addMilestone);
        milestoneCard.getStyleClass().add("card");

        HBox bottom = new HBox(14, phasesCard, milestoneCard);
        HBox.setHgrow(phasesCard, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(milestoneCard, javafx.scene.layout.Priority.ALWAYS);

        getChildren().addAll(topCard, bottom);

        refresh(appState.getSelectedProject());
        appState.selectedProjectProperty().addListener((obs, o, n) -> refresh(n));
    }

    private void refresh(Project p) {
        // Unbind old project listeners
        if (boundProject != null) {
            boundProject.getTasks().removeListener((ListChangeListener) tasksListener);
            boundProject.getMilestones().removeListener((ListChangeListener) milestonesListener);
        }
        boundProject = p;

        if (p == null) {
            sub.setText("No project selected");
            progress.setText("-");
            counts.setText("-");
            phaseTable.setItems(null);
            milestoneList.setItems(null);
            return;
        }

        // Bind listeners for live updates
        p.getTasks().addListener((ListChangeListener) tasksListener);
        p.getMilestones().addListener((ListChangeListener) milestonesListener);

        sub.setText(safe(p.getName()) + "  •  " + safe(p.getStartDate()) + " → " + safe(p.getEndDate()));

        int pct = progressService.projectProgressPercent(p);
        progress.setText("Progress: " + pct + "%");

        long todo = p.getTasks().stream().filter(t -> t.getStatus() == TaskStatus.TODO).count();
        long ip = p.getTasks().stream().filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS).count();
        long blocked = p.getTasks().stream().filter(t -> t.getStatus() == TaskStatus.BLOCKED).count();
        long done = p.getTasks().stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();

        counts.setText("TODO: " + todo + "  |  IN PROGRESS: " + ip + "  |  BLOCKED: " + blocked + "  |  DONE: " + done);

        phaseTable.setItems(p.getPhases());
        milestoneList.setItems(p.getMilestones());

        phaseTable.refresh();
        milestoneList.refresh();
    }

    private int phaseProgressPercent(Project p, Phase phase) {
        var tasks = p.getTasks().stream().filter(t -> t.getPhase() == phase).toList();
        if (tasks.isEmpty()) return 0;

        double total = 0;
        for (var t : tasks) {
            total += switch (t.getStatus()) {
                case TODO -> 0.0;
                case IN_PROGRESS -> 0.5;
                case BLOCKED -> 0.25;
                case DONE -> 1.0;
            };
        }
        return (int) Math.round((total / tasks.size()) * 100.0);
    }

    private String safe(Object o) {
        return (o == null) ? "-" : o.toString();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
