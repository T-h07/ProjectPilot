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
        if (boundProject != null) refresh(boundProject);
        phaseTable.refresh();
    };

    private final ListChangeListener<?> milestonesListener = c -> milestoneList.refresh();

    // ✅ react to phases changing (so table updates after add/remove)
    private final ListChangeListener<?> phasesListener = c -> phaseTable.refresh();

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

        // ✅ Actions column (delete phase)
        TableColumn<Phase, Void> phaseActions = new TableColumn<>("");
        phaseActions.setPrefWidth(120);
        phaseActions.setCellFactory(col -> new TableCell<>() {
            private final Button deleteBtn = new Button("Delete");

            {
                deleteBtn.getStyleClass().add("danger");
                deleteBtn.setOnAction(e -> {
                    Project p = appState.getSelectedProject();
                    if (p == null) return;

                    int idx = getIndex();
                    if (idx < 0 || idx >= getTableView().getItems().size()) return;

                    Phase ph = getTableView().getItems().get(idx);
                    if (ph == null) return;

                    long used = p.getTasks().stream().filter(t -> t.getPhase() == ph).count();
                    if (used > 0) {
                        Alert a = new Alert(Alert.AlertType.WARNING);
                        a.setTitle("Cannot delete phase");
                        a.setHeaderText("This phase is used by tasks");
                        a.setContentText("Unassign or move tasks out of this phase before deleting it.");
                        a.showAndWait();
                        return;
                    }

                    p.getPhases().remove(ph);
                    // If you want autosave for this too, add a store.removePhase(...) later.
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : deleteBtn);
            }
        });

        phaseTable.getColumns().setAll(phaseName, phaseProg, phaseOpen, phaseActions);
        phaseTable.setPrefHeight(260);
        phaseTable.getStyleClass().add("pp-table");

        Label phasesTitle = new Label("Phases");
        phasesTitle.getStyleClass().add("section-title");

        // ✅ Add Phase button
        Button addPhaseBtn = new Button("Add Phase");
        addPhaseBtn.getStyleClass().add("primary");
        addPhaseBtn.setOnAction(e -> addPhase(store, appState));

        HBox phasesHeader = new HBox(10, phasesTitle, new Region(), addPhaseBtn);
        HBox.setHgrow(phasesHeader.getChildren().get(1), Priority.ALWAYS);

        VBox phasesCard = new VBox(10, phasesHeader, phaseTable);
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
        HBox.setHgrow(phasesCard, Priority.ALWAYS);
        HBox.setHgrow(milestoneCard, Priority.ALWAYS);

        getChildren().addAll(topCard, bottom);

        refresh(appState.getSelectedProject());
        appState.selectedProjectProperty().addListener((obs, o, n) -> refresh(n));
    }

    private void addPhase(InMemoryStore store, AppState appState) {
        Project p = appState.getSelectedProject();
        if (p == null) return;

        TextInputDialog d = new TextInputDialog();
        d.setTitle("Add Phase");
        d.setHeaderText("Add Phase");
        d.setContentText("Phase name:");

        d.showAndWait().ifPresent(raw -> {
            String n = raw == null ? "" : raw.trim();
            if (n.isBlank()) return;

            Phase ph = createPhase(n);
            if (ph == null) return;

            store.addPhase(p, ph); // ✅ autosave/logging via store
            phaseTable.getSelectionModel().select(ph);
        });
    }

    private Phase createPhase(String name) {
        try {
            // Your Phase requires 1 argument (String)
            return new Phase(name);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void refresh(Project p) {
        if (boundProject != null) {
            boundProject.getTasks().removeListener((ListChangeListener) tasksListener);
            boundProject.getMilestones().removeListener((ListChangeListener) milestonesListener);
            boundProject.getPhases().removeListener((ListChangeListener) phasesListener);
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

        p.getTasks().addListener((ListChangeListener) tasksListener);
        p.getMilestones().addListener((ListChangeListener) milestonesListener);
        p.getPhases().addListener((ListChangeListener) phasesListener);

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
