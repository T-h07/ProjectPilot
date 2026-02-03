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
import javafx.beans.binding.BooleanBinding;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;

public class ProjectOverviewPage extends VBox {

    private final InMemoryStore store;
    private final AppState appState;
    private final ProgressService progressService = new ProgressService();

    private final Label header = new Label("Project Overview");
    private final Label sub = new Label("");

    private final Label descTitle = new Label("Description");
    private final Label stakeholderTitle = new Label("Stakeholders");

    private final Label descText = new Label("-");
    private final Label stakeholderText = new Label("-");

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

    private final BooleanBinding canEdit;

    public ProjectOverviewPage(InMemoryStore store, AppState appState) {
        this.store = store;
        this.appState = appState;

        this.canEdit = Bindings.createBooleanBinding(
                () -> appState.sessionProperty().get() != null && appState.isAdmin(),
                appState.sessionProperty()
        );

        setPadding(new Insets(16));
        setSpacing(14);

        header.getStyleClass().add("page-title");
        sub.getStyleClass().add("muted");

        descTitle.getStyleClass().add("section-title");
        stakeholderTitle.getStyleClass().add("section-title");

        descText.setWrapText(true);
        stakeholderText.setWrapText(true);

        descArea.setWrapText(true);
        stakeholderArea.setWrapText(true);
        descArea.setPrefRowCount(4);
        stakeholderArea.setPrefRowCount(4);

        setNodeVisible(descArea, false);
        setNodeVisible(stakeholderArea, false);

        editSaveBtn.getStyleClass().add("primary");

        // ✅ Edit button: admin-only via binding
        editSaveBtn.visibleProperty().bind(canEdit);
        editSaveBtn.managedProperty().bind(editSaveBtn.visibleProperty());

        // ✅ Cancel button: NO visible binding (manual control)
        cancelBtn.disableProperty().bind(canEdit.not());
        setNodeVisible(cancelBtn, false);

        editSaveBtn.setOnAction(e -> onEditSave());
        cancelBtn.setOnAction(e -> {
            setEditing(false);
            Project p = appState.getSelectedProject();
            if (p != null) updateProjectInfo(p);
        });

        HBox topLine = new HBox(10, header, new Region(), editSaveBtn, cancelBtn);
        HBox.setHgrow(topLine.getChildren().get(1), Priority.ALWAYS);
        topLine.setAlignment(Pos.CENTER_LEFT);

        VBox infoBox = new VBox(8,
                descTitle, descText, descArea,
                stakeholderTitle, stakeholderText, stakeholderArea
        );

        VBox topCard = new VBox(10, topLine, sub, infoBox, progress, counts);
        topCard.getStyleClass().add("card");

        buildPhaseTable();
        buildMilestones();

        HBox bottom = new HBox(14, buildPhasesCard(), buildMilestoneCard());
        HBox.setHgrow(bottom.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(bottom.getChildren().get(1), Priority.ALWAYS);

        getChildren().addAll(topCard, bottom);

        refresh(appState.getSelectedProject());
        appState.selectedProjectProperty().addListener((obs, o, n) -> refresh(n));
    }

    // -------------------------------------------------

    private void onEditSave() {
        if (!canEdit.get()) return;

        Project p = appState.getSelectedProject();
        if (p == null) return;

        if (!editing) {
            setEditing(true);
            descArea.setText(p.getDescription());
            stakeholderArea.setText(p.getStakeholders());
        } else {
            p.setDescription(normalize(descArea.getText()));
            p.setStakeholders(normalize(stakeholderArea.getText()));
            setEditing(false);
            updateProjectInfo(p);
        }
    }

    private void setEditing(boolean value) {
        editing = value;

        editSaveBtn.setText(value ? "Save" : "Edit");

        setNodeVisible(cancelBtn, value);
        setNodeVisible(descText, !value);
        setNodeVisible(stakeholderText, !value);
        setNodeVisible(descArea, value);
        setNodeVisible(stakeholderArea, value);
    }

    private void setNodeVisible(Region n, boolean v) {
        n.setVisible(v);
        n.setManaged(v);
    }

    private void setNodeVisible(Control n, boolean v) {
        n.setVisible(v);
        n.setManaged(v);
    }

    // -------------------------------------------------
    // Phase / Milestone setup (unchanged logic)
    // -------------------------------------------------

    private void buildPhaseTable() {
        TableColumn<Phase, String> name = new TableColumn<>("Phase");
        name.setCellValueFactory(c -> c.getValue().nameProperty());

        TableColumn<Phase, String> progressCol = new TableColumn<>("Progress");
        progressCol.setCellValueFactory(c ->
                Bindings.createStringBinding(() -> {
                    Project p = appState.getSelectedProject();
                    return p == null ? "-" : phaseProgressPercent(p, c.getValue()) + "%";
                })
        );

        phaseTable.getColumns().setAll(name, progressCol);
        phaseTable.getStyleClass().add("pp-table");
    }

    private VBox buildPhasesCard() {
        Label title = new Label("Phases");
        title.getStyleClass().add("section-title");
        VBox box = new VBox(10, title, phaseTable);
        box.getStyleClass().add("card");
        return box;
    }

    private void buildMilestones() {
        milestoneList.setCellFactory(lv -> new ListCell<>() {
            private final CheckBox cb = new CheckBox();
            @Override protected void updateItem(Milestone m, boolean empty) {
                super.updateItem(m, empty);
                if (empty || m == null) {
                    setGraphic(null);
                    return;
                }
                cb.setText(m.nameProperty().get());
                cb.selectedProperty().bindBidirectional(m.completedProperty());
                setGraphic(cb);
            }
        });
    }

    private VBox buildMilestoneCard() {
        Label title = new Label("Milestones");
        title.getStyleClass().add("section-title");
        VBox box = new VBox(10, title, milestoneList);
        box.getStyleClass().add("card");
        return box;
    }

    // -------------------------------------------------

    private void refresh(Project p) {
        setEditing(false);

        if (p == null) {
            sub.setText("No project selected");
            return;
        }

        sub.setText(p.getName());
        updateProjectInfo(p);

        int pct = progressService.projectProgressPercent(p);
        progress.setText("Progress: " + pct + "%");

        phaseTable.setItems(p.getPhases());
        milestoneList.setItems(p.getMilestones());
    }

    private void updateProjectInfo(Project p) {
        descText.setText(safeText(p.getDescription()));
        stakeholderText.setText(safeText(p.getStakeholders()));
    }

    private int phaseProgressPercent(Project p, Phase ph) {
        var tasks = p.getTasks().stream().filter(t -> t.getPhase() == ph).toList();
        if (tasks.isEmpty()) return 0;
        double sum = tasks.stream().mapToDouble(t ->
                t.getStatus() == TaskStatus.DONE ? 1 :
                        t.getStatus() == TaskStatus.IN_PROGRESS ? 0.5 :
                                t.getStatus() == TaskStatus.BLOCKED ? 0.25 : 0
        ).sum();
        return (int) Math.round((sum / tasks.size()) * 100);
    }

    private String safeText(String s) {
        return (s == null || s.trim().isEmpty()) ? "-" : s.trim();
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim();
    }
}
