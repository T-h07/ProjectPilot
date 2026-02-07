package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.ActivityItem;
import com.projectpilot.model.Member;
import com.projectpilot.model.Milestone;
import com.projectpilot.model.Phase;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.Priority;
import com.projectpilot.model.enums.TaskStatus;
import com.projectpilot.service.CsvReportService;
import com.projectpilot.service.PdfReportService;
import com.projectpilot.service.ReportFilters;
import com.projectpilot.service.ReportOptions;
import com.projectpilot.service.ReportService;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class ExportReportPage extends VBox {

    private final InMemoryStore store;
    private final AppState appState;

    private final PdfReportService pdfService = new PdfReportService();
    private final CsvReportService csvService = new CsvReportService();
    private final ReportService reportService = new ReportService();

    private final Label header = new Label("Export Report");
    private final Label projectLabel = new Label("");
    private final Label previewMeta = new Label("");
    private final Label previewCounts = new Label("");

    private final Button exportPdfBtn = new Button("Export PDF...");
    private final Button exportCsvBtn = new Button("Export CSV...");
    private final Button resetBtn = new Button("Reset to defaults");

    private final CheckBox includeSummary = new CheckBox("Summary");
    private final CheckBox includePhases = new CheckBox("Phases");
    private final CheckBox includeMilestones = new CheckBox("Milestones");
    private final CheckBox includeTasks = new CheckBox("Tasks");
    private final CheckBox includeTeam = new CheckBox("Team");
    private final CheckBox includeActivity = new CheckBox("Activity");

    private final CheckBox onlyOpenTasks = new CheckBox("Only open tasks");

    private final CheckBox statusTodo = new CheckBox("TODO");
    private final CheckBox statusInProgress = new CheckBox("IN_PROGRESS");
    private final CheckBox statusBlocked = new CheckBox("BLOCKED");
    private final CheckBox statusDone = new CheckBox("DONE");

    private final CheckBox priorityLow = new CheckBox("LOW");
    private final CheckBox priorityMedium = new CheckBox("MEDIUM");
    private final CheckBox priorityHigh = new CheckBox("HIGH");

    private final ComboBox<OptionItem> assigneeBox = new ComboBox<>();
    private final ComboBox<OptionItem> phaseBox = new ComboBox<>();

    private final DatePicker dueFrom = new DatePicker();
    private final DatePicker dueTo = new DatePicker();

    private final Spinner<Integer> activityLimit = new Spinner<>(1, 50, 10);

    private final WebView preview = new WebView();

    private Project boundProject;
    private boolean refreshing = false;

    private final ListChangeListener<Task> tasksListener = c -> updatePreview();
    private final ListChangeListener<Milestone> milestoneListener = c -> updatePreview();
    private final ListChangeListener<Member> membersListener = c -> {
        refreshAssigneeOptions();
        updatePreview();
    };
    private final ListChangeListener<Phase> phasesListener = c -> {
        refreshPhaseOptions();
        updatePreview();
    };

    public ExportReportPage(InMemoryStore store, AppState appState) {
        this.store = store;
        this.appState = appState;

        setSpacing(0);

        header.getStyleClass().add("page-title");
        projectLabel.getStyleClass().add("muted");
        previewMeta.getStyleClass().add("muted");
        previewCounts.getStyleClass().add("muted");

        projectLabel.setWrapText(true);
        previewMeta.setWrapText(true);
        previewCounts.setWrapText(true);

        exportPdfBtn.getStyleClass().add("primary");

        includeSummary.setSelected(true);
        includePhases.setSelected(true);
        includeMilestones.setSelected(true);
        includeTasks.setSelected(true);
        includeTeam.setSelected(true);
        includeActivity.setSelected(true);

        activityLimit.setEditable(true);
        activityLimit.getStyleClass().add("pp-spinner");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        VBox titleBox = new VBox(4, header, projectLabel);
        HBox topBar = new HBox(12, titleBox, spacer, exportCsvBtn, exportPdfBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("card");
        topBar.setPadding(new Insets(12));

        VBox optionsCard = buildOptionsCard();
        VBox filtersCard = buildFiltersCard();

        VBox leftCol = new VBox(12, optionsCard, filtersCard);
        leftCol.setPrefWidth(360);
        leftCol.setMinWidth(320);

        VBox previewCard = buildPreviewCard();

        HBox main = new HBox(14, leftCol, previewCard);
        HBox.setHgrow(previewCard, javafx.scene.layout.Priority.ALWAYS);
        VBox.setVgrow(main, javafx.scene.layout.Priority.ALWAYS);

        VBox content = new VBox(12, topBar, main);
        content.setPadding(new Insets(16));
        VBox.setVgrow(main, javafx.scene.layout.Priority.ALWAYS);

        ScrollPane scroll = new ScrollPane(content);
        scroll.getStyleClass().add("pp-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPannable(true);

        getChildren().add(scroll);
        VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);

        wireActions();
        wireListeners();

        store.getActivity().addListener((ListChangeListener<ActivityItem>) c -> updatePreview());

        refresh(appState.getSelectedProject());
        appState.selectedProjectProperty().addListener((obs, oldV, newV) -> refresh(newV));
    }

    private VBox buildOptionsCard() {
        Label title = new Label("Report Sections");
        title.getStyleClass().add("section-title");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);

        grid.add(includeSummary, 0, 0);
        grid.add(includePhases, 1, 0);
        grid.add(includeMilestones, 0, 1);
        grid.add(includeTasks, 1, 1);
        grid.add(includeTeam, 0, 2);
        grid.add(includeActivity, 1, 2);

        Label activityLabel = new Label("Activity limit");
        activityLimit.setPrefWidth(90);
        HBox activityRow = new HBox(10, activityLabel, activityLimit);
        activityRow.setAlignment(Pos.CENTER_LEFT);

        activityLimit.disableProperty().bind(includeActivity.selectedProperty().not());

        VBox box = new VBox(10, title, grid, activityRow, resetBtn);
        box.getStyleClass().add("card");
        box.setPadding(new Insets(12));
        return box;
    }

    private VBox buildFiltersCard() {
        Label title = new Label("Filters");
        title.getStyleClass().add("section-title");

        Label statusLabel = new Label("Status");
        statusLabel.getStyleClass().add("muted");

        FlowPane statusRow = new FlowPane(10, 8, statusTodo, statusInProgress, statusBlocked, statusDone);

        Label statusHint = new Label("Leave empty to include all statuses.");
        statusHint.getStyleClass().add("muted");
        statusHint.setWrapText(true);

        Label priorityLabel = new Label("Priority");
        priorityLabel.getStyleClass().add("muted");

        FlowPane priorityRow = new FlowPane(10, 8, priorityLow, priorityMedium, priorityHigh);

        Label priorityHint = new Label("Leave empty to include all priorities.");
        priorityHint.getStyleClass().add("muted");
        priorityHint.setWrapText(true);

        assigneeBox.setPrefWidth(220);
        phaseBox.setPrefWidth(220);
        dueFrom.setPrefWidth(180);
        dueTo.setPrefWidth(180);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);

        grid.add(new Label("Assignee"), 0, 0);
        grid.add(assigneeBox, 1, 0);

        grid.add(new Label("Phase"), 0, 1);
        grid.add(phaseBox, 1, 1);

        grid.add(new Label("Due from"), 0, 2);
        grid.add(dueFrom, 1, 2);

        grid.add(new Label("Due to"), 0, 3);
        grid.add(dueTo, 1, 3);

        VBox box = new VBox(
                10,
                title,
                onlyOpenTasks,
                statusLabel,
                statusRow,
                statusHint,
                priorityLabel,
                priorityRow,
                priorityHint,
                grid
        );
        box.getStyleClass().add("card");
        box.setPadding(new Insets(12));
        return box;
    }

    private VBox buildPreviewCard() {
        Label title = new Label("Preview");
        title.getStyleClass().add("section-title");

        Label hint = new Label("Exports use the current options and filters.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);

        preview.setContextMenuEnabled(false);
        preview.setPrefHeight(520);

        StackPane previewWrap = new StackPane(preview);
        previewWrap.getStyleClass().add("export-preview");
        VBox.setVgrow(previewWrap, javafx.scene.layout.Priority.ALWAYS);

        VBox box = new VBox(10, title, hint, previewMeta, previewCounts, previewWrap);
        box.getStyleClass().add("card");
        box.setPadding(new Insets(12));
        VBox.setVgrow(box, javafx.scene.layout.Priority.ALWAYS);
        return box;
    }

    private void wireActions() {
        exportPdfBtn.setOnAction(e -> exportPdf());
        exportCsvBtn.setOnAction(e -> exportCsv());
        resetBtn.setOnAction(e -> resetToDefaults());
    }

    private void wireListeners() {
        includeSummary.selectedProperty().addListener((obs, ov, nv) -> updatePreview());
        includePhases.selectedProperty().addListener((obs, ov, nv) -> updatePreview());
        includeMilestones.selectedProperty().addListener((obs, ov, nv) -> updatePreview());
        includeTasks.selectedProperty().addListener((obs, ov, nv) -> updatePreview());
        includeTeam.selectedProperty().addListener((obs, ov, nv) -> updatePreview());
        includeActivity.selectedProperty().addListener((obs, ov, nv) -> updatePreview());

        onlyOpenTasks.selectedProperty().addListener((obs, ov, nv) -> updatePreview());

        statusTodo.selectedProperty().addListener((obs, ov, nv) -> updatePreview());
        statusInProgress.selectedProperty().addListener((obs, ov, nv) -> updatePreview());
        statusBlocked.selectedProperty().addListener((obs, ov, nv) -> updatePreview());
        statusDone.selectedProperty().addListener((obs, ov, nv) -> updatePreview());

        priorityLow.selectedProperty().addListener((obs, ov, nv) -> updatePreview());
        priorityMedium.selectedProperty().addListener((obs, ov, nv) -> updatePreview());
        priorityHigh.selectedProperty().addListener((obs, ov, nv) -> updatePreview());

        assigneeBox.valueProperty().addListener((obs, ov, nv) -> updatePreview());
        phaseBox.valueProperty().addListener((obs, ov, nv) -> updatePreview());

        dueFrom.valueProperty().addListener((obs, ov, nv) -> updatePreview());
        dueTo.valueProperty().addListener((obs, ov, nv) -> updatePreview());

        activityLimit.valueProperty().addListener((obs, ov, nv) -> updatePreview());
    }

    private void refresh(Project p) {
        bindProject(p);

        boolean hasProject = p != null;
        exportPdfBtn.setDisable(!hasProject);
        exportCsvBtn.setDisable(!hasProject);

        refreshAssigneeOptions();
        refreshPhaseOptions();

        if (!hasProject) {
            header.setText("Export Report");
            projectLabel.setText("Select a project to start.");
            previewMeta.setText("Filters: All data");
            previewCounts.setText("");
            loadPlaceholder("No project selected", "Select a project to see the report preview.");
            return;
        }

        header.setText("Export Report - " + p.getName());
        projectLabel.setText("Selected project: " + p.getName());

        updatePreview();
    }

    private void bindProject(Project p) {
        if (boundProject != null) {
            boundProject.getTasks().removeListener(tasksListener);
            boundProject.getMilestones().removeListener(milestoneListener);
            boundProject.getMembers().removeListener(membersListener);
            boundProject.getPhases().removeListener(phasesListener);
        }
        boundProject = p;
        if (p != null) {
            p.getTasks().addListener(tasksListener);
            p.getMilestones().addListener(milestoneListener);
            p.getMembers().addListener(membersListener);
            p.getPhases().addListener(phasesListener);
        }
    }

    private void refreshAssigneeOptions() {
        Project p = appState.getSelectedProject();
        String currentId = assigneeBox.getValue() == null ? "" : assigneeBox.getValue().id();
        List<OptionItem> items = new ArrayList<>();
        items.add(new OptionItem("", "Any"));
        items.add(new OptionItem(ReportOptions.ASSIGNEE_UNASSIGNED, "Unassigned"));

        if (p != null) {
            for (Member m : p.getMembers()) {
                if (m == null) continue;
                String label = safeLabel(m.getName(), m.getId());
                items.add(new OptionItem(m.getId(), label));
            }
        }

        assigneeBox.getItems().setAll(items);
        assigneeBox.setValue(findOption(items, currentId));
    }

    private void refreshPhaseOptions() {
        Project p = appState.getSelectedProject();
        String currentId = phaseBox.getValue() == null ? "" : phaseBox.getValue().id();
        List<OptionItem> items = new ArrayList<>();
        items.add(new OptionItem("", "Any"));
        items.add(new OptionItem(ReportOptions.PHASE_NONE, "None"));

        if (p != null) {
            for (Phase ph : p.getPhases()) {
                if (ph == null) continue;
                String label = safeLabel(ph.getName(), ph.getId());
                items.add(new OptionItem(ph.getId(), label));
            }
        }

        phaseBox.getItems().setAll(items);
        phaseBox.setValue(findOption(items, currentId));
    }

    private void resetToDefaults() {
        if (refreshing) return;
        refreshing = true;
        try {
            includeSummary.setSelected(true);
            includePhases.setSelected(true);
            includeMilestones.setSelected(true);
            includeTasks.setSelected(true);
            includeTeam.setSelected(true);
            includeActivity.setSelected(true);

            onlyOpenTasks.setSelected(false);

            statusTodo.setSelected(false);
            statusInProgress.setSelected(false);
            statusBlocked.setSelected(false);
            statusDone.setSelected(false);

            priorityLow.setSelected(false);
            priorityMedium.setSelected(false);
            priorityHigh.setSelected(false);

            assigneeBox.setValue(findOption(assigneeBox.getItems(), ""));
            phaseBox.setValue(findOption(phaseBox.getItems(), ""));

            dueFrom.setValue(null);
            dueTo.setValue(null);

            activityLimit.getValueFactory().setValue(10);
        } finally {
            refreshing = false;
        }

        updatePreview();
    }

    private void updatePreview() {
        if (refreshing) return;
        refreshing = true;
        try {
            Project p = appState.getSelectedProject();
            if (p == null) {
                loadPlaceholder("No project selected", "Select a project to see the report preview.");
                return;
            }

            ReportOptions opts = buildOptions();
            String html = reportService.generateHtml(p, store.getActivity(), opts);
            preview.getEngine().loadContent(html);

            List<Task> tasks = ReportFilters.filterTasks(p, opts);
            List<Milestone> milestones = ReportFilters.filterMilestones(p, opts);
            List<ActivityItem> activity = ReportFilters.filterActivity(store.getActivity(), opts);

            String filters = ReportFilters.describeFilters(p, opts);
            previewMeta.setText("Filters: " + filters);

            int activityVisible = Math.min(opts.activityLimit(), activity.size());

            previewCounts.setText("Tasks: " + tasks.size() + "/" + p.getTasks().size()
                    + " | Milestones: " + milestones.size() + "/" + p.getMilestones().size()
                    + " | Activity: " + activityVisible + "/" + activity.size());
        } finally {
            refreshing = false;
        }
    }

    private ReportOptions buildOptions() {
        Set<TaskStatus> statuses = EnumSet.noneOf(TaskStatus.class);
        if (statusTodo.isSelected()) statuses.add(TaskStatus.TODO);
        if (statusInProgress.isSelected()) statuses.add(TaskStatus.IN_PROGRESS);
        if (statusBlocked.isSelected()) statuses.add(TaskStatus.BLOCKED);
        if (statusDone.isSelected()) statuses.add(TaskStatus.DONE);

        Set<Priority> priorities = EnumSet.noneOf(Priority.class);
        if (priorityLow.isSelected()) priorities.add(Priority.LOW);
        if (priorityMedium.isSelected()) priorities.add(Priority.MEDIUM);
        if (priorityHigh.isSelected()) priorities.add(Priority.HIGH);

        String assigneeId = assigneeBox.getValue() == null ? "" : assigneeBox.getValue().id();
        String phaseId = phaseBox.getValue() == null ? "" : phaseBox.getValue().id();

        LocalDate from = dueFrom.getValue();
        LocalDate to = dueTo.getValue();
        if (from != null && to != null && from.isAfter(to)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }

        int limit = activityLimit.getValue() == null ? 10 : activityLimit.getValue();

        return new ReportOptions(
                includeSummary.isSelected(),
                includePhases.isSelected(),
                includeMilestones.isSelected(),
                includeTasks.isSelected(),
                includeTeam.isSelected(),
                includeActivity.isSelected(),
                onlyOpenTasks.isSelected(),
                statuses,
                priorities,
                assigneeId,
                phaseId,
                from,
                to,
                limit
        );
    }

    private void exportPdf() {
        Project p = appState.getSelectedProject();
        if (p == null) return;

        FileChooser fc = new FileChooser();
        fc.setTitle("Export ProjectPilot Report (PDF)");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF (*.pdf)", "*.pdf"));
        fc.setInitialFileName(safeFileName(p.getName()) + "-report.pdf");

        File file = fc.showSaveDialog(getScene() == null ? null : getScene().getWindow());
        if (file == null) return;

        try {
            pdfService.exportProjectReport(p, store.getActivity(), file, buildOptions());

            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("Export complete");
            a.setHeaderText("PDF report saved");
            a.setContentText(file.getAbsolutePath());
            a.showAndWait();
        } catch (Exception ex) {
            ex.printStackTrace();

            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setTitle("Export failed");
            a.setHeaderText("Could not create PDF");
            a.setContentText(ex.toString());
            a.showAndWait();
        }
    }

    private void exportCsv() {
        Project p = appState.getSelectedProject();
        if (p == null) return;

        FileChooser fc = new FileChooser();
        fc.setTitle("Export ProjectPilot Tasks (CSV)");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV (*.csv)", "*.csv"));
        fc.setInitialFileName(safeFileName(p.getName()) + "-tasks.csv");

        File file = fc.showSaveDialog(getScene() == null ? null : getScene().getWindow());
        if (file == null) return;

        try {
            ReportOptions opts = buildOptions();
            List<Task> tasks = ReportFilters.filterTasks(p, opts);
            csvService.exportTasksCsv(p.getName(), tasks, file);

            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("Export complete");
            a.setHeaderText("CSV saved");
            a.setContentText(file.getAbsolutePath());
            a.showAndWait();
        } catch (Exception ex) {
            ex.printStackTrace();

            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setTitle("Export failed");
            a.setHeaderText("Could not create CSV");
            a.setContentText(ex.toString());
            a.showAndWait();
        }
    }

    private void loadPlaceholder(String title, String message) {
        String html = String.format("""
                <!doctype html>
                <html>
                <head>
                  <meta charset="utf-8"/>
                  <style>
                    body{font-family:system-ui,Segoe UI,Arial,sans-serif;margin:24px;background:#f8fafc;color:#0f172a;}
                    .card{border:1px solid #e2e8f0;border-radius:12px;padding:16px;background:#ffffff;}
                    h2{margin:0 0 8px 0;}
                  </style>
                </head>
                <body>
                  <div class="card">
                    <h2>%s</h2>
                    <div>%s</div>
                  </div>
                </body>
                </html>
                """, esc(title), esc(message));
        preview.getEngine().loadContent(html);
    }

    private String safeFileName(String s) {
        if (s == null || s.isBlank()) return "project";
        return s.trim().replaceAll("[\\\\/:*?\"<>|]+", "-").replaceAll("\\s+", "_");
    }

    private static OptionItem findOption(List<OptionItem> items, String id) {
        if (items == null || items.isEmpty()) return new OptionItem("", "Any");
        String match = id == null ? "" : id;
        for (OptionItem item : items) {
            if (item == null) continue;
            if (match.equals(item.id())) return item;
        }
        return items.get(0);
    }

    private static String safeLabel(String name, String fallback) {
        if (name == null) return fallback == null ? "-" : fallback;
        String v = name.trim();
        return v.isEmpty() ? (fallback == null ? "-" : fallback) : v;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private record OptionItem(String id, String label) {
        @Override public String toString() { return label == null ? "" : label; }
    }
}
