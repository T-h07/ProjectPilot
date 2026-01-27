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
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.lang.reflect.Method;
import java.time.format.DateTimeFormatter;

public class ProjectOverviewPage extends VBox {

    private final InMemoryStore store;
    private final AppState appState;

    private final ProgressService progressService = new ProgressService();

    private final Label header = new Label("Project Overview");
    private final Label sub = new Label("");

    // Info titles
    private final Label descTitle = new Label("Description");
    private final Label stakeholderTitle = new Label("Stakeholders");

    // Read-only labels (view mode)
    private final Label descText = new Label("-");
    private final Label stakeholderText = new Label("-");

    // Editable fields (edit mode)
    private final TextArea descArea = new TextArea();
    private final TextArea stakeholderArea = new TextArea();

    private final Button editSaveBtn = new Button("Edit");
    private final Button cancelBtn = new Button("Cancel");
    private boolean editing = false;

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
    private final ListChangeListener<?> phasesListener = c -> phaseTable.refresh();

    public ProjectOverviewPage(InMemoryStore store, AppState appState) {
        this.store = store;
        this.appState = appState;

        setPadding(new Insets(16));
        setSpacing(14);

        header.getStyleClass().add("page-title");
        sub.getStyleClass().add("muted");

        descTitle.getStyleClass().add("section-title");
        stakeholderTitle.getStyleClass().add("section-title");

        // Wrap and allow full width
        descText.setWrapText(true);
        stakeholderText.setWrapText(true);
        descText.setMaxWidth(Double.MAX_VALUE);
        stakeholderText.setMaxWidth(Double.MAX_VALUE);

        // TextAreas setup
        descArea.setWrapText(true);
        stakeholderArea.setWrapText(true);
        descArea.setPrefRowCount(4);
        stakeholderArea.setPrefRowCount(4);
        descArea.getStyleClass().add("pp-textarea");
        stakeholderArea.getStyleClass().add("pp-textarea");

        // start hidden (only in edit mode)
        setNodeVisible(descArea, false);
        setNodeVisible(stakeholderArea, false);

        // Edit/Save + Cancel controls
        editSaveBtn.getStyleClass().add("primary");
        cancelBtn.setFocusTraversable(false);
        setNodeVisible(cancelBtn, false);

        editSaveBtn.setOnAction(e -> {
            Project p = appState.getSelectedProject();
            if (p == null) return;

            if (!editing) {
                // enter edit mode
                setEditing(true);
                // preload fields from current project values
                descArea.setText(safeText(readDescription(p)));
                stakeholderArea.setText(safeText(readStakeholders(p)));
            } else {
                // save
                String newDesc = normalizeNullable(descArea.getText());
                String newStake = normalizeNullable(stakeholderArea.getText());

                writeDescription(p, newDesc);
                writeStakeholders(p, newStake);

                // exit edit mode and refresh displayed values
                setEditing(false);
                updateProjectInfo(p);
            }
        });

        cancelBtn.setOnAction(e -> {
            Project p = appState.getSelectedProject();
            setEditing(false);
            if (p != null) updateProjectInfo(p);
        });

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        HBox headerRow = new HBox(10, headerSpacer, editSaveBtn, cancelBtn);
        headerRow.setAlignment(Pos.CENTER_RIGHT);

        // Put title on left, buttons on right
        HBox topLine = new HBox(10, header, new Region(), editSaveBtn, cancelBtn);
        HBox.setHgrow(topLine.getChildren().get(1), Priority.ALWAYS);
        topLine.setAlignment(Pos.CENTER_LEFT);

        progress.setStyle("-fx-font-size: 22px; -fx-font-weight: 800;");
        counts.getStyleClass().add("muted");

        // Info layout (description + stakeholders)
        VBox infoBox = new VBox(8,
                descTitle,
                descText,
                descArea,
                stakeholderTitle,
                stakeholderText,
                stakeholderArea
        );
        infoBox.setFillWidth(true);

        VBox topCard = new VBox(10, topLine, sub, infoBox, progress, counts);
        topCard.getStyleClass().add("card");

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

        TableColumn<Phase, Void> phaseActions = new TableColumn<>("");
        phaseActions.setPrefWidth(120);
        phaseActions.setSortable(false);
        phaseActions.setResizable(false);

        phaseActions.setCellFactory(col -> new TableCell<>() {
            private final Button deleteBtn = new Button("Delete");

            {
                deleteBtn.getStyleClass().addAll("sm", "danger-outline");
                deleteBtn.setFocusTraversable(false);

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
                });

                setAlignment(Pos.CENTER_RIGHT);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    setText(null);
                } else {
                    setGraphic(deleteBtn);
                    setText(null);
                }
            }
        });

        phaseTable.getColumns().setAll(phaseName, phaseProg, phaseOpen, phaseActions);
        phaseTable.setPrefHeight(260);
        phaseTable.getStyleClass().add("pp-table");

        Label phasesTitle = new Label("Phases");
        phasesTitle.getStyleClass().add("section-title");

        Button addPhaseBtn = new Button("Add Phase");
        addPhaseBtn.getStyleClass().add("primary");
        addPhaseBtn.setOnAction(e -> addPhase(store, appState));

        Region phasesSpacer = new Region();
        HBox.setHgrow(phasesSpacer, Priority.ALWAYS);

        HBox phasesHeader = new HBox(10, phasesTitle, phasesSpacer, addPhaseBtn);
        phasesHeader.setAlignment(Pos.CENTER_LEFT);

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

    private void setEditing(boolean value) {
        editing = value;

        if (value) {
            editSaveBtn.setText("Save");
            setNodeVisible(cancelBtn, true);

            setNodeVisible(descText, false);
            setNodeVisible(stakeholderText, false);

            setNodeVisible(descArea, true);
            setNodeVisible(stakeholderArea, true);
        } else {
            editSaveBtn.setText("Edit");
            setNodeVisible(cancelBtn, false);

            setNodeVisible(descArea, false);
            setNodeVisible(stakeholderArea, false);

            setNodeVisible(descText, true);
            setNodeVisible(stakeholderText, true);
        }
    }

    private void setNodeVisible(Region node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
    private void setNodeVisible(Control node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
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

            store.addPhase(p, ph);
            phaseTable.getSelectionModel().select(ph);
        });
    }

    private Phase createPhase(String name) {
        try {
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

        // switching projects should never keep edit mode open
        setEditing(false);

        if (p == null) {
            sub.setText("No project selected");
            descText.setText("-");
            stakeholderText.setText("-");
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

        updateProjectInfo(p);

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

    private void updateProjectInfo(Project p) {
        descText.setText(safeText(readDescription(p)));
        stakeholderText.setText(safeText(readStakeholders(p)));
    }

    // ---------------------------
    // Read helpers (reflection)
    // ---------------------------

    private String readDescription(Project p) {
        return readProjectString(p,
                "getDescription",
                "descriptionProperty"
        );
    }

    private String readStakeholders(Project p) {
        return readProjectString(p,
                "getStakeholders",
                "getStakeholder",
                "getStakeholderData",
                "stakeholdersProperty",
                "stakeholderProperty",
                "stakeholderDataProperty"
        );
    }

    private String readProjectString(Project p, String... methodNames) {
        for (String name : methodNames) {
            try {
                Method m = p.getClass().getMethod(name);
                Object v = m.invoke(p);
                if (v == null) continue;

                if (v instanceof ObservableValue<?> ov) {
                    Object vv = ov.getValue();
                    if (vv != null) return vv.toString();
                    continue;
                }

                return v.toString();
            } catch (Exception ignored) { }
        }
        return null;
    }

    // ---------------------------
    // Write helpers (reflection)
    // ---------------------------

    private void writeDescription(Project p, String value) {
        writeProjectString(p, value,
                "setDescription",
                "descriptionProperty"
        );
    }

    private void writeStakeholders(Project p, String value) {
        writeProjectString(p, value,
                "setStakeholders",
                "setStakeholder",
                "setStakeholderData",
                "stakeholdersProperty",
                "stakeholderProperty",
                "stakeholderDataProperty"
        );
    }

    private boolean writeProjectString(Project p, String value, String... names) {
        for (String name : names) {
            // 1) Try setter: setX(String)
            try {
                Method setter = p.getClass().getMethod(name, String.class);
                setter.invoke(p, value);
                return true;
            } catch (Exception ignored) { }

            // 2) Try property: xProperty() returning StringProperty
            try {
                Method prop = p.getClass().getMethod(name);
                Object v = prop.invoke(p);
                if (v instanceof StringProperty sp) {
                    sp.set(value);
                    return true;
                }
            } catch (Exception ignored) { }
        }
        return false;
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

    private String safeText(String s) {
        if (s == null) return "-";
        String t = s.trim();
        return t.isEmpty() ? "-" : t;
    }

    private String normalizeNullable(String s) {
        if (s == null) return "";
        return s.trim();
    }
}
