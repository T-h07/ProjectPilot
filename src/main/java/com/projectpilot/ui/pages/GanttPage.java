package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.Member;
import com.projectpilot.model.Phase;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.Priority;
import com.projectpilot.model.enums.TaskStatus;
import com.projectpilot.security.AccessPolicy;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.shape.Line;
import javafx.scene.shape.Path;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.LineTo;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class GanttPage extends VBox {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter DAY_SHORT = DateTimeFormatter.ofPattern("dd");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MMM");
    private static final double ROW_GAP = 12;

    private final InMemoryStore store;
    private final AppState appState;
    private final AccessPolicy policy = new AccessPolicy();

    private final Label header = new Label("Gantt");
    private final Label sub = new Label("");

    private final Label rangeStart = new Label("-");
    private final Label rangeEnd = new Label("-");
    private final Label rangeMeta = new Label("");
    private final Region rangeLine = new Region();
    private final HBox rangeRow = new HBox(10);

    private final TextField search = new TextField();
    private final CheckBox hideDone = new CheckBox("Hide DONE");

    private final CheckBox statusTodo = new CheckBox("TODO");
    private final CheckBox statusInProgress = new CheckBox("IN_PROGRESS");
    private final CheckBox statusBlocked = new CheckBox("BLOCKED");
    private final CheckBox statusDone = new CheckBox("DONE");

    private final CheckBox priorityLow = new CheckBox("LOW");
    private final CheckBox priorityMedium = new CheckBox("MEDIUM");
    private final CheckBox priorityHigh = new CheckBox("HIGH");

    private final CheckBox groupByPhase = new CheckBox("Group by phase");
    private final CheckBox showDependencies = new CheckBox("Dependencies");
    private final CheckBox highlightCritical = new CheckBox("Critical path");
    private final CheckBox showDetails = new CheckBox("Details panel");
    private final CheckBox showWorkload = new CheckBox("Workload panel");

    private final ComboBox<LabelMode> scaleBox = new ComboBox<>();
    private final DatePicker viewStartPicker = new DatePicker();
    private final DatePicker viewEndPicker = new DatePicker();
    private final Button autoRangeBtn = new Button("Auto range");
    private final Button todayBtn = new Button("Today");
    private final Button fitDates = new Button("Fit width");

    private final ScrollPane scroll = new ScrollPane();
    private final VBox sheet = new VBox(10);
    private final VBox rowsBox = new VBox(6);
    private final StackPane rowsStack = new StackPane();
    private final Pane connectors = new Pane();

    private final VBox sidePanel = new VBox(12);
    private final ObjectProperty<Task> selectedTask = new SimpleObjectProperty<>();

    private final TextField taskTitle = new TextField();
    private final TextArea taskDescription = new TextArea();
    private final ComboBox<TaskStatus> taskStatus = new ComboBox<>();
    private final ComboBox<Priority> taskPriority = new ComboBox<>();
    private final ComboBox<MemberOption> taskAssignee = new ComboBox<>();
    private final DatePicker taskDue = new DatePicker();
    private final Label taskMeta = new Label("-");

    private final VBox workloadList = new VBox(8);
    private final Label workloadMeta = new Label("");

    private boolean syncingDetails = false;
    private boolean syncingRange = false;

    private double dayWidth = 22;
    private double labelColWidth = 320;
    private int defaultDurationDays = 7;
    private double timelineWidth = 0;

    private LocalDate minDate;
    private LocalDate maxDate;
    private LocalDate autoMinDate;
    private LocalDate autoMaxDate;
    private LocalDate rangeStartDate;
    private LocalDate rangeEndDate;
    private LocalDate viewStartOverride;
    private LocalDate viewEndOverride;

    private final Set<String> collapsedPhases = new HashSet<>();
    private final Map<Task, Region> barByTask = new HashMap<>();
    private final Map<Task, HBox> rowByTask = new HashMap<>();
    private final List<Dependency> dependencies = new ArrayList<>();
    private final Set<Task> criticalTasks = new HashSet<>();

    private LabelMode labelMode = LabelMode.WEEK;

    private final InvalidationListener taskPropsListener = obs -> requestRebuild();
    private final PauseTransition rebuildDelay = new PauseTransition(Duration.millis(80));
    private final BooleanBinding canEditSelected;
    private final BooleanBinding canEditMeta;

    private void hookTask(Task t) {
        if (t == null) return;
        t.titleProperty().addListener(taskPropsListener);
        t.statusProperty().addListener(taskPropsListener);
        t.priorityProperty().addListener(taskPropsListener);
        t.dueDateProperty().addListener(taskPropsListener);
        t.assigneeProperty().addListener(taskPropsListener);
    }

    private void unhookTask(Task t) {
        if (t == null) return;
        t.titleProperty().removeListener(taskPropsListener);
        t.statusProperty().removeListener(taskPropsListener);
        t.priorityProperty().removeListener(taskPropsListener);
        t.dueDateProperty().removeListener(taskPropsListener);
        t.assigneeProperty().removeListener(taskPropsListener);
    }

    public GanttPage(InMemoryStore store, AppState appState) {
        this.store = store;
        this.appState = appState;
        this.membersListener = c -> {
            refreshAssignees(this.appState.getSelectedProject());
            syncDetailsFromTask(selectedTask.get());
        };
        this.canEditSelected = Bindings.createBooleanBinding(
                () -> policy.canEditTask(appState, selectedTask.get()),
                appState.sessionProperty(),
                appState.selectedProjectProperty(),
                appState.currentProjectRoleProperty(),
                selectedTask
        );
        this.canEditMeta = Bindings.createBooleanBinding(
                () -> policy.canEditTaskMeta(appState),
                appState.sessionProperty(),
                appState.selectedProjectProperty(),
                appState.currentProjectRoleProperty()
        );

        setSpacing(0);
        rebuildDelay.setOnFinished(e -> rebuild());

        header.getStyleClass().add("page-title");
        sub.getStyleClass().add("muted");

        rangeStart.getStyleClass().add("muted");
        rangeEnd.getStyleClass().add("muted");
        rangeMeta.getStyleClass().add("muted");
        rangeLine.getStyleClass().add("gantt-range-line");

        HBox.setHgrow(rangeLine, javafx.scene.layout.Priority.ALWAYS);
        rangeRow.setAlignment(Pos.CENTER_LEFT);
        rangeRow.getChildren().addAll(rangeStart, rangeLine, rangeEnd, rangeMeta);

        search.setPromptText("Search tasks...");
        search.setPrefWidth(280);

        statusTodo.setSelected(true);
        statusInProgress.setSelected(true);
        statusBlocked.setSelected(true);
        statusDone.setSelected(true);

        priorityLow.setSelected(true);
        priorityMedium.setSelected(true);
        priorityHigh.setSelected(true);

        groupByPhase.setSelected(true);
        showDependencies.setSelected(true);
        highlightCritical.setSelected(true);
        showDetails.setSelected(true);
        showWorkload.setSelected(true);

        scaleBox.getItems().setAll(LabelMode.values());
        scaleBox.setValue(labelMode);
        scaleBox.setPrefWidth(120);

        viewStartPicker.setPrefWidth(130);
        viewEndPicker.setPrefWidth(130);

        FlowPane legendRow = buildLegendRow();
        FlowPane statusRow = buildStatusFilterRow();
        FlowPane priorityRow = buildPriorityFilterRow();
        FlowPane togglesRow = buildTogglesRow();
        VBox viewRow = buildViewRow();

        VBox topCard = new VBox(10, header, sub, rangeRow, legendRow, statusRow, priorityRow, togglesRow, viewRow);
        topCard.getStyleClass().add("card");
        topCard.setPadding(new Insets(16));

        scroll.getStyleClass().add("gantt-scroll");
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setPannable(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        sheet.getStyleClass().add("gantt-sheet");
        scroll.setContent(sheet);

        rowsStack.getChildren().addAll(rowsBox, connectors);
        rowsStack.setAlignment(Pos.TOP_LEFT);

        connectors.setMouseTransparent(true);
        connectors.setPickOnBounds(false);
        StackPane.setAlignment(connectors, Pos.TOP_LEFT);

        sidePanel.getChildren().addAll(buildDetailsCard(), buildWorkloadCard());
        sidePanel.setFillWidth(true);
        sidePanel.setMaxWidth(Double.MAX_VALUE);

        BooleanBinding showSide = Bindings.createBooleanBinding(
                () -> showDetails.isSelected() || showWorkload.isSelected(),
                showDetails.selectedProperty(),
                showWorkload.selectedProperty()
        );
        sidePanel.visibleProperty().bind(showSide);
        sidePanel.managedProperty().bind(showSide);

        VBox ganttBody = new VBox(12, scroll, sidePanel);
        VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);

        VBox ganttCard = new VBox(ganttBody);
        ganttCard.getStyleClass().add("gantt-card");
        VBox.setVgrow(ganttCard, javafx.scene.layout.Priority.ALWAYS);

        VBox content = new VBox(14, topCard, ganttCard);
        content.setPadding(new Insets(16));

        ScrollPane pageScroll = new ScrollPane(content);
        pageScroll.getStyleClass().add("pp-scroll");
        pageScroll.setFitToWidth(true);
        pageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        pageScroll.setPannable(true);

        getChildren().setAll(pageScroll);
        VBox.setVgrow(pageScroll, javafx.scene.layout.Priority.ALWAYS);

        wireActions();
        wireDetailsBindings();

        store.getProjects().addListener((ListChangeListener<Project>) c -> requestRebuild());
        appState.selectedProjectProperty().addListener((obs, oldP, newP) -> {
            if (oldP != null) {
                oldP.getTasks().removeListener(tasksListener);
                oldP.getMembers().removeListener(membersListener);
                for (Task t : oldP.getTasks()) unhookTask(t);
            }
            if (newP != null) {
                newP.getTasks().addListener(tasksListener);
                newP.getMembers().addListener(membersListener);
                for (Task t : newP.getTasks()) hookTask(t);
            }
            refreshAssignees(newP);
            requestRebuild();
        });

        if (appState.getSelectedProject() != null) {
            Project p = appState.getSelectedProject();
            p.getTasks().addListener(tasksListener);
            p.getMembers().addListener(membersListener);
            for (Task t : p.getTasks()) hookTask(t);
        }

        refreshAssignees(appState.getSelectedProject());
        rebuild();
        Platform.runLater(this::fitDatesToViewport);
    }

    private final ListChangeListener<Task> tasksListener = c -> {
        while (c.next()) {
            if (c.wasRemoved()) {
                for (Task t : c.getRemoved()) {
                    unhookTask(t);
                    if (selectedTask.get() == t) selectedTask.set(null);
                }
            }
            if (c.wasAdded()) {
                for (Task t : c.getAddedSubList()) hookTask(t);
            }
        }
        requestRebuild();
    };

    private final ListChangeListener<Member> membersListener;

    private void wireActions() {
        search.textProperty().addListener((obs, o, n) -> requestRebuild());
        hideDone.selectedProperty().addListener((obs, o, n) -> requestRebuild());

        statusTodo.selectedProperty().addListener((obs, o, n) -> requestRebuild());
        statusInProgress.selectedProperty().addListener((obs, o, n) -> requestRebuild());
        statusBlocked.selectedProperty().addListener((obs, o, n) -> requestRebuild());
        statusDone.selectedProperty().addListener((obs, o, n) -> requestRebuild());

        priorityLow.selectedProperty().addListener((obs, o, n) -> requestRebuild());
        priorityMedium.selectedProperty().addListener((obs, o, n) -> requestRebuild());
        priorityHigh.selectedProperty().addListener((obs, o, n) -> requestRebuild());

        groupByPhase.selectedProperty().addListener((obs, o, n) -> requestRebuild());
        showDependencies.selectedProperty().addListener((obs, o, n) -> renderDependencies());
        highlightCritical.selectedProperty().addListener((obs, o, n) -> requestRebuild());

        showDetails.selectedProperty().addListener((obs, o, n) -> updateDetailsVisibility());
        showWorkload.selectedProperty().addListener((obs, o, n) -> updateWorkloadVisibility());

        scaleBox.valueProperty().addListener((obs, o, n) -> {
            if (n != null) labelMode = n;
            requestRebuild();
        });

        viewStartPicker.valueProperty().addListener((obs, o, n) -> {
            if (syncingRange) return;
            viewStartOverride = n;
            requestRebuild();
        });
        viewEndPicker.valueProperty().addListener((obs, o, n) -> {
            if (syncingRange) return;
            viewEndOverride = n;
            requestRebuild();
        });

        autoRangeBtn.setOnAction(e -> {
            viewStartOverride = null;
            viewEndOverride = null;
            requestRebuild();
        });

        todayBtn.setOnAction(e -> scrollToDate(LocalDate.now()));
        fitDates.setOnAction(e -> fitDatesToViewport());
    }

    private void wireDetailsBindings() {
        BooleanBinding noTask = selectedTask.isNull();
        taskTitle.disableProperty().bind(noTask.or(canEditMeta.not()));
        taskPriority.disableProperty().bind(noTask.or(canEditMeta.not()));
        taskAssignee.disableProperty().bind(noTask.or(canEditMeta.not()));
        taskDue.disableProperty().bind(noTask.or(canEditMeta.not()));
        taskDescription.disableProperty().bind(noTask.or(canEditSelected.not()));
        taskStatus.disableProperty().bind(noTask.or(canEditSelected.not()));

        selectedTask.addListener((obs, oldV, newV) -> {
            syncDetailsFromTask(newV);
            updateSelectionStyles();
        });

        taskTitle.textProperty().addListener((obs, o, n) -> {
            if (syncingDetails) return;
            Task t = selectedTask.get();
            if (t != null) t.setTitle(n == null ? "" : n);
        });
        taskDescription.textProperty().addListener((obs, o, n) -> {
            if (syncingDetails) return;
            Task t = selectedTask.get();
            if (t != null) t.setDescription(n == null ? "" : n);
        });
        taskStatus.valueProperty().addListener((obs, o, n) -> {
            if (syncingDetails) return;
            Task t = selectedTask.get();
            if (t != null && n != null) t.setStatus(n);
        });
        taskPriority.valueProperty().addListener((obs, o, n) -> {
            if (syncingDetails) return;
            Task t = selectedTask.get();
            if (t != null && n != null) t.setPriority(n);
        });
        taskAssignee.valueProperty().addListener((obs, o, n) -> {
            if (syncingDetails) return;
            Task t = selectedTask.get();
            if (t != null) t.setAssignee(n == null ? null : n.member());
        });
        taskDue.valueProperty().addListener((obs, o, n) -> {
            if (syncingDetails) return;
            Task t = selectedTask.get();
            if (t != null) t.setDueDate(n);
        });
    }

    private void updateDetailsVisibility() {
        Node details = sidePanel.getChildren().get(0);
        details.setVisible(showDetails.isSelected());
        details.setManaged(showDetails.isSelected());
    }

    private void updateWorkloadVisibility() {
        Node workload = sidePanel.getChildren().get(1);
        workload.setVisible(showWorkload.isSelected());
        workload.setManaged(showWorkload.isSelected());
    }

    private void refreshAssignees(Project p) {
        List<MemberOption> items = new ArrayList<>();
        items.add(new MemberOption(null, "Unassigned"));
        if (p != null) {
            for (Member m : p.getMembers()) {
                if (m == null) continue;
                items.add(new MemberOption(m, safe(m.getName())));
            }
        }
        taskAssignee.getItems().setAll(items);
        Task t = selectedTask.get();
        if (t != null) {
            taskAssignee.setValue(findAssigneeOption(t.getAssignee()));
        }
    }

    private void syncDetailsFromTask(Task t) {
        syncingDetails = true;
        try {
            if (t == null) {
                taskTitle.setText("");
                taskDescription.setText("");
                taskStatus.setValue(null);
                taskPriority.setValue(null);
                taskAssignee.setValue(null);
                taskDue.setValue(null);
                taskMeta.setText("-");
                return;
            }

            taskTitle.setText(safe(t.getTitle()));
            taskDescription.setText(safe(t.getDescription()));
            taskStatus.setValue(t.getStatus());
            taskPriority.setValue(t.getPriority());
            taskDue.setValue(t.getDueDate());

            Member assignee = t.getAssignee();
            taskAssignee.setValue(findAssigneeOption(assignee));

            taskMeta.setText("Task ID: " + safe(t.getId()));
        } finally {
            syncingDetails = false;
        }
    }

    private void rebuild() {
        Project p = appState.getSelectedProject();
        barByTask.clear();
        rowByTask.clear();
        dependencies.clear();
        criticalTasks.clear();

        if (p == null) {
            sub.setText("No project selected - timeline based on task due dates");
            setRangeStrip(null, null);
            Label msg = new Label("Select a project to view its Gantt.");
            msg.getStyleClass().add("muted");
            sheet.getChildren().setAll(msg);
            updateWorkload(p);
            renderDependencies();
            return;
        }

        sub.setText(p.getName() + " - timeline based on task due dates");

        List<Task> tasks = filteredTasks(p);
        computeDateRange(p, tasks);
        applyRangeOverrides();
        setRangeStrip(rangeStartDate, rangeEndDate);
        syncRangePickers();

        if (selectedTask.get() != null && !tasks.contains(selectedTask.get())) {
            selectedTask.set(null);
        }

        sheet.getChildren().clear();
        sheet.getChildren().add(buildHeaderRow());

        rowsBox.getChildren().clear();
        rowsBox.setFillWidth(true);

        List<PhaseGroup> groups = groupByPhase.isSelected()
                ? groupTasksByPhase(p, tasks)
                : List.of(new PhaseGroup(null, "All Tasks", tasks));

        for (PhaseGroup group : groups) {
            rowsBox.getChildren().add(buildPhaseRow(group));
            if (isCollapsed(group)) continue;
            for (Task t : group.tasks()) {
                rowsBox.getChildren().add(buildTaskRow(t, groupByPhase.isSelected()));
            }
        }

        rowsStack.getChildren().setAll(rowsBox, connectors);
        sheet.getChildren().add(rowsStack);

        dependencies.addAll(buildDependencies(groups, tasks));
        criticalTasks.addAll(computeCriticalTasks(groups, tasks));

        updateSelectionStyles();
        updateWorkload(p);

        Platform.runLater(this::renderDependencies);
    }

    private void setRangeStrip(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            rangeStart.setText("-");
            rangeEnd.setText("-");
            rangeMeta.setText("");
            return;
        }

        rangeStart.setText(start.toString());
        rangeEnd.setText(end.toString());

        int days = (int) (ChronoUnit.DAYS.between(start, end) + 1);
        rangeMeta.setText(days > 0 ? " (" + days + " days)" : "");
    }

    private FlowPane buildLegendRow() {
        FlowPane legend = new FlowPane(12, 6);
        legend.getChildren().addAll(
                legendItem("TODO", "legend-todo"),
                legendItem("IN_PROGRESS", "legend-inprogress"),
                legendItem("BLOCKED", "legend-blocked"),
                legendItem("DONE", "legend-done"),
                legendItem("LOW", "legend-prio-low"),
                legendItem("MEDIUM", "legend-prio-medium"),
                legendItem("HIGH", "legend-prio-high"),
                legendItem("CRITICAL", "legend-critical")
        );
        return legend;
    }

    private FlowPane buildStatusFilterRow() {
        FlowPane row = new FlowPane(10, 6);
        Label label = new Label("Status:");
        label.getStyleClass().add("muted");
        row.getChildren().addAll(label, statusTodo, statusInProgress, statusBlocked, statusDone, hideDone);
        return row;
    }

    private FlowPane buildPriorityFilterRow() {
        FlowPane row = new FlowPane(10, 6);
        Label label = new Label("Priority:");
        label.getStyleClass().add("muted");
        row.getChildren().addAll(label, priorityLow, priorityMedium, priorityHigh);
        return row;
    }

    private FlowPane buildTogglesRow() {
        FlowPane row = new FlowPane(10, 6);
        row.getChildren().addAll(groupByPhase, showDependencies, highlightCritical, showDetails, showWorkload);
        return row;
    }

    private VBox buildViewRow() {
        HBox scaleGroup = labeledGroup("Scale:", scaleBox);
        HBox endGroup = new HBox(10, fitDates, scaleGroup);
        endGroup.setAlignment(Pos.CENTER_LEFT);

        FlowPane row = new FlowPane(10, 6);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().addAll(
                labeledGroup("View:", viewStartPicker, viewEndPicker),
                autoRangeBtn,
                todayBtn,
                endGroup
        );

        return new VBox(6, row);
    }

    private HBox labeledGroup(String labelText, Node... nodes) {
        Label label = new Label(labelText);
        label.getStyleClass().add("muted");
        HBox box = new HBox(8);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        box.getChildren().add(label);
        if (nodes != null) {
            box.getChildren().addAll(nodes);
        }
        return box;
    }

    private void requestRebuild() {
        rebuildDelay.playFromStart();
    }

    private HBox legendItem(String text, String dotClass) {
        Region dot = new Region();
        dot.getStyleClass().addAll("gantt-legend-dot", dotClass);
        Label label = new Label(text);
        label.getStyleClass().add("muted");
        HBox box = new HBox(6, dot, label);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private List<PhaseGroup> groupTasksByPhase(Project p, List<Task> tasks) {
        List<PhaseGroup> groups = new ArrayList<>();
        Map<String, Phase> phaseById = new LinkedHashMap<>();
        if (p != null) {
            for (Phase phase : p.getPhases()) {
                if (phase != null) phaseById.put(phase.getId(), phase);
            }
        }

        Map<String, List<Task>> grouped = new LinkedHashMap<>();
        List<Task> noPhase = new ArrayList<>();

        for (Task t : tasks) {
            Phase ph = t.getPhase();
            if (ph == null) {
                noPhase.add(t);
                continue;
            }
            grouped.computeIfAbsent(ph.getId(), k -> new ArrayList<>()).add(t);
            phaseById.putIfAbsent(ph.getId(), ph);
        }

        if (!noPhase.isEmpty()) {
            groups.add(new PhaseGroup("_none", "No Phase", noPhase));
        }

        for (Map.Entry<String, Phase> entry : phaseById.entrySet()) {
            List<Task> phaseTasks = grouped.get(entry.getKey());
            if (phaseTasks == null || phaseTasks.isEmpty()) continue;
            String name = entry.getValue() == null ? "Phase" : safe(entry.getValue().getName());
            groups.add(new PhaseGroup(entry.getKey(), name, phaseTasks));
        }

        return groups.isEmpty() ? List.of(new PhaseGroup("_none", "No Phase", tasks)) : groups;
    }

    private boolean isCollapsed(PhaseGroup group) {
        return collapsedPhases.contains(phaseKey(group));
    }

    private String phaseKey(PhaseGroup group) {
        return group.phaseId() != null ? group.phaseId() : group.name();
    }

    private HBox buildPhaseRow(PhaseGroup group) {
        int total = group.tasks().size();
        long done = group.tasks().stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
        int open = Math.max(0, total - (int) done);
        int pct = total == 0 ? 0 : (int) Math.round((done * 100.0) / total);

        Button toggle = new Button(isCollapsed(group) ? "+" : "-");
        toggle.getStyleClass().add("gantt-phase-toggle");
        toggle.setOnAction(e -> {
            String key = phaseKey(group);
            if (collapsedPhases.contains(key)) {
                collapsedPhases.remove(key);
            } else {
                collapsedPhases.add(key);
            }
            rebuild();
        });

        Label title = new Label(group.name());
        title.getStyleClass().addAll("gantt-task-label", "gantt-phase-title");
        Label meta = new Label(total + " tasks | " + open + " open | " + pct + "% done");
        meta.getStyleClass().add("gantt-task-muted");

        StackPane progress = new StackPane();
        progress.getStyleClass().add("gantt-phase-progress");
        progress.setPrefWidth(140);
        progress.setMinHeight(6);
        progress.setMaxHeight(6);

        Region fill = new Region();
        fill.getStyleClass().add("gantt-phase-progress-fill");
        double fillWidth = total == 0 ? 0 : Math.max(6, 140 * (done / (double) total));
        fill.setPrefWidth(fillWidth);
        progress.getChildren().add(fill);

        HBox titleRow = new HBox(8, toggle, title);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        VBox labelBox = new VBox(4, titleRow, meta, progress);
        labelBox.setMinWidth(labelColWidth);
        labelBox.setPrefWidth(labelColWidth);

        Pane timeline = new Pane();
        timeline.setMinWidth(timelineWidth);
        timeline.setPrefWidth(timelineWidth);
        timeline.setMinHeight(28);

        HBox row = new HBox(ROW_GAP, labelBox, timeline);
        row.getStyleClass().add("gantt-phase-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox buildTaskRow(Task t, boolean grouped) {
        HBox row = new HBox(ROW_GAP);
        row.getStyleClass().add("gantt-row");
        row.setAlignment(Pos.CENTER_LEFT);

        String titleText = safe(t.getTitle());
        Label title = new Label(titleText.isBlank() ? "Untitled task" : titleText);
        title.getStyleClass().add("gantt-task-label");

        StringBuilder metaText = new StringBuilder();
        if (!grouped) {
            Phase phase = t.getPhase();
            metaText.append(phase == null ? "No Phase" : safe(phase.getName()));
            metaText.append(" | ");
        }
        if (t.getDueDate() != null) {
            metaText.append("Due ").append(t.getDueDate());
        } else {
            metaText.append("No due date");
        }
        metaText.append(" | ").append(t.getStatus());

        Label meta = new Label(metaText.toString());
        meta.getStyleClass().add("gantt-task-muted");

        VBox labelBox = new VBox(2, title, meta);
        labelBox.setMinWidth(labelColWidth);
        labelBox.setPrefWidth(labelColWidth);

        Pane timeline = new Pane();
        timeline.setMinWidth(timelineWidth);
        timeline.setPrefWidth(timelineWidth);
        timeline.setMinHeight(28);

        Region bar = buildTaskBar(t);
        if (bar != null) {
            timeline.getChildren().add(bar);
        }

        row.getChildren().addAll(labelBox, timeline);
        rowByTask.put(t, row);
        if (bar != null) barByTask.put(t, bar);

        row.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) selectedTask.set(t);
        });
        if (bar != null) {
            bar.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY) selectedTask.set(t);
            });
        }

        return row;
    }

    private Region buildTaskBar(Task t) {
        if (minDate == null || maxDate == null) return null;
        LocalDate end = t.getDueDate();
        if (end == null) return null;

        LocalDate start = end.minusDays(defaultDurationDays);
        if (start.isAfter(end)) start = end;

        if (start.isBefore(minDate)) start = minDate;
        if (end.isAfter(maxDate)) end = maxDate;
        if (end.isBefore(minDate) || start.isAfter(maxDate)) return null;

        int offset = (int) ChronoUnit.DAYS.between(minDate, start);
        int duration = (int) Math.max(1, ChronoUnit.DAYS.between(start, end) + 1);

        double x = offset * dayWidth;
        double w = duration * dayWidth;

        Region bar = new Region();
        bar.getStyleClass().add("gantt-bar");
        bar.getStyleClass().add(statusClass(t.getStatus()));
        bar.getStyleClass().add(priorityClass(t.getPriority()));
        bar.setLayoutX(x);
        bar.setLayoutY(6);
        bar.setPrefWidth(Math.max(8, w));
        bar.setPrefHeight(16);

        return bar;
    }

    private String statusClass(TaskStatus status) {
        if (status == null) return "todo";
        return switch (status) {
            case IN_PROGRESS -> "inprogress";
            case BLOCKED -> "blocked";
            case DONE -> "done";
            default -> "todo";
        };
    }

    private String priorityClass(Priority priority) {
        if (priority == null) return "prio-medium";
        return switch (priority) {
            case LOW -> "prio-low";
            case HIGH -> "prio-high";
            default -> "prio-medium";
        };
    }

    private List<Dependency> buildDependencies(List<PhaseGroup> groups, List<Task> tasks) {
        List<Dependency> deps = new ArrayList<>();
        List<List<Task>> buckets = new ArrayList<>();
        if (groupByPhase.isSelected()) {
            for (PhaseGroup group : groups) {
                buckets.add(new ArrayList<>(group.tasks()));
            }
        } else {
            buckets.add(new ArrayList<>(tasks));
        }

        for (List<Task> bucket : buckets) {
            bucket.sort(Comparator.comparing(Task::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())));
            for (int i = 0; i < bucket.size() - 1; i++) {
                Task from = bucket.get(i);
                Task to = bucket.get(i + 1);
                if (from == null || to == null) continue;
                if (from.getDueDate() == null || to.getDueDate() == null) continue;
                deps.add(new Dependency(from, to));
            }
        }
        return deps;
    }

    private List<Task> computeCriticalTasks(List<PhaseGroup> groups, List<Task> tasks) {
        if (!highlightCritical.isSelected()) return List.of();
        return tasks.stream()
                .filter(t -> t.getPriority() == Priority.HIGH)
                .filter(t -> t.getStatus() != TaskStatus.DONE)
                .collect(Collectors.toList());
    }

    private void updateSelectionStyles() {
        Task sel = selectedTask.get();
        for (Map.Entry<Task, HBox> entry : rowByTask.entrySet()) {
            HBox row = entry.getValue();
            row.getStyleClass().remove("gantt-row-selected");
            if (sel != null && entry.getKey() == sel) {
                row.getStyleClass().add("gantt-row-selected");
            }
        }

        for (Map.Entry<Task, Region> entry : barByTask.entrySet()) {
            Region bar = entry.getValue();
            bar.getStyleClass().remove("critical");
            if (highlightCritical.isSelected() && criticalTasks.contains(entry.getKey())) {
                bar.getStyleClass().add("critical");
            }
        }
    }

    private void renderDependencies() {
        connectors.getChildren().clear();
        connectors.setPrefWidth(labelColWidth + timelineWidth + ROW_GAP);
        connectors.setPrefHeight(rowsBox.getHeight());

        if (minDate != null && maxDate != null) {
            LocalDate today = LocalDate.now();
            int offset = (int) ChronoUnit.DAYS.between(minDate, today);
            if (offset >= 0) {
                double x = offset * dayWidth;
                Line todayLine = new Line(x, 0, x, rowsBox.getHeight());
                todayLine.getStyleClass().add("gantt-today-line");
                connectors.getChildren().add(todayLine);
            }
        }

        if (!showDependencies.isSelected()) return;
        if (dependencies.isEmpty()) return;
        if (connectors.getScene() == null) return;

        for (Dependency dep : dependencies) {
            Region fromBar = barByTask.get(dep.from());
            Region toBar = barByTask.get(dep.to());
            if (fromBar == null || toBar == null) continue;

            Bounds a = connectors.sceneToLocal(fromBar.localToScene(fromBar.getBoundsInLocal()));
            Bounds b = connectors.sceneToLocal(toBar.localToScene(toBar.getBoundsInLocal()));

            double startX = a.getMaxX();
            double startY = a.getMinY() + (a.getHeight() / 2);
            double endX = b.getMinX();
            double endY = b.getMinY() + (b.getHeight() / 2);
            double midX = Math.max(startX + 12, (startX + endX) / 2);

            Path path = new Path(
                    new MoveTo(startX, startY),
                    new LineTo(midX, startY),
                    new LineTo(midX, endY),
                    new LineTo(endX, endY)
            );
            path.getStyleClass().add("gantt-dep-line");

            Polygon arrow = new Polygon(
                    endX, endY,
                    endX - 6, endY - 4,
                    endX - 6, endY + 4
            );
            arrow.getStyleClass().add("gantt-dep-arrow");

            connectors.getChildren().addAll(path, arrow);
        }
    }

    private void scrollToDate(LocalDate date) {
        if (date == null || minDate == null || maxDate == null) return;
        int offset = (int) ChronoUnit.DAYS.between(minDate, date);
        if (offset < 0) offset = 0;
        double targetX = offset * dayWidth;

        double viewport = scroll.getViewportBounds() == null ? 0 : scroll.getViewportBounds().getWidth();
        double contentWidth = sheet.getWidth();
        if (viewport <= 0 || contentWidth <= viewport) return;

        double h = clamp(targetX / (contentWidth - viewport), 0, 1);
        scroll.setHvalue(h);
    }

    private void updateWorkload(Project p) {
        workloadList.getChildren().clear();
        if (p == null) {
            workloadMeta.setText("-");
            return;
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (Member m : p.getMembers()) {
            if (m == null) continue;
            counts.put(m.getId(), 0);
            names.put(m.getId(), safe(m.getName()));
        }

        int unassigned = 0;
        for (Task t : p.getTasks()) {
            Member assignee = t.getAssignee();
            if (assignee == null) {
                unassigned++;
            } else if (counts.containsKey(assignee.getId())) {
                counts.put(assignee.getId(), counts.get(assignee.getId()) + 1);
            } else {
                counts.put(assignee.getId(), 1);
                names.put(assignee.getId(), safe(assignee.getName()));
            }
        }

        int max = counts.values().stream().max(Integer::compareTo).orElse(0);
        max = Math.max(max, unassigned);
        max = Math.max(1, max);

        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            workloadList.getChildren().add(buildWorkloadRow(names.get(entry.getKey()), entry.getValue(), max));
        }
        if (unassigned > 0) {
            workloadList.getChildren().add(buildWorkloadRow("Unassigned", unassigned, max));
        }

        workloadMeta.setText(p.getTasks().size() + " tasks");
    }

    private HBox buildWorkloadRow(String name, int count, int max) {
        Label label = new Label(name);
        label.getStyleClass().add("gantt-task-label");

        Label value = new Label(String.valueOf(count));
        value.getStyleClass().add("muted");

        StackPane bar = new StackPane();
        bar.getStyleClass().add("gantt-workload-bar");
        bar.setPrefWidth(140);
        bar.setMinHeight(8);
        bar.setMaxHeight(8);

        Region fill = new Region();
        fill.getStyleClass().add("gantt-workload-fill");
        double w = Math.max(6, 140 * (count / (double) max));
        fill.setPrefWidth(w);
        bar.getChildren().add(fill);

        HBox row = new HBox(10, label, bar, value);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private VBox buildDetailsCard() {
        Label title = new Label("Task Details");
        title.getStyleClass().add("gantt-header-label");

        taskStatus.getItems().setAll(TaskStatus.values());
        taskPriority.getItems().setAll(Priority.values());

        taskDescription.setWrapText(true);
        taskDescription.setPrefRowCount(4);
        taskDescription.getStyleClass().add("pp-textarea");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setMinWidth(110);
        col1.setPrefWidth(110);
        col1.setMaxWidth(110);

        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(javafx.scene.layout.Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col1, col2);

        addDetailRow(grid, 0, "Title", taskTitle);
        addDetailRow(grid, 1, "Status", taskStatus);
        addDetailRow(grid, 2, "Priority", taskPriority);
        addDetailRow(grid, 3, "Assignee", taskAssignee);
        addDetailRow(grid, 4, "Due Date", taskDue);

        Label descLabel = new Label("Notes");
        descLabel.getStyleClass().add("muted");
        grid.add(descLabel, 0, 5);
        grid.add(taskDescription, 1, 5);

        taskMeta.getStyleClass().add("muted");

        VBox card = new VBox(10, title, grid, taskMeta);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));
        return card;
    }

    private void addDetailRow(GridPane grid, int row, String labelText, Node field) {
        Label label = new Label(labelText);
        label.getStyleClass().add("muted");
        grid.add(label, 0, row);
        grid.add(field, 1, row);
    }

    private VBox buildWorkloadCard() {
        Label title = new Label("Workload");
        title.getStyleClass().add("gantt-header-label");
        workloadMeta.getStyleClass().add("muted");

        VBox card = new VBox(10, title, workloadMeta, workloadList);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));
        return card;
    }

    private List<Task> filteredTasks(Project p) {
        String q = (search.getText() == null) ? "" : search.getText().trim().toLowerCase();

        Set<TaskStatus> statuses = selectedStatuses();
        Set<Priority> priorities = selectedPriorities();

        return p.getTasks().stream()
                .filter(t -> !hideDone.isSelected() || t.getStatus() != TaskStatus.DONE)
                .filter(t -> statuses.contains(t.getStatus()))
                .filter(t -> priorities.contains(t.getPriority()))
                .filter(t -> q.isBlank() || safe(t.getTitle()).toLowerCase().contains(q))
                .sorted(Comparator
                        .comparing(Task::getStatus)
                        .thenComparing(Task::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    private Set<TaskStatus> selectedStatuses() {
        EnumSet<TaskStatus> set = EnumSet.noneOf(TaskStatus.class);
        if (statusTodo.isSelected()) set.add(TaskStatus.TODO);
        if (statusInProgress.isSelected()) set.add(TaskStatus.IN_PROGRESS);
        if (statusBlocked.isSelected()) set.add(TaskStatus.BLOCKED);
        if (statusDone.isSelected()) set.add(TaskStatus.DONE);
        return set.isEmpty() ? EnumSet.allOf(TaskStatus.class) : set;
    }

    private Set<Priority> selectedPriorities() {
        EnumSet<Priority> set = EnumSet.noneOf(Priority.class);
        if (priorityLow.isSelected()) set.add(Priority.LOW);
        if (priorityMedium.isSelected()) set.add(Priority.MEDIUM);
        if (priorityHigh.isSelected()) set.add(Priority.HIGH);
        return set.isEmpty() ? EnumSet.allOf(Priority.class) : set;
    }

    private void computeDateRange(Project p, List<Task> tasks) {
        LocalDate fallbackStart = (p.getStartDate() != null) ? p.getStartDate() : LocalDate.now().minusDays(7);
        LocalDate fallbackEnd = (p.getEndDate() != null) ? p.getEndDate() : LocalDate.now().plusDays(21);

        LocalDate minTask = null;
        LocalDate maxTask = null;

        for (Task t : tasks) {
            LocalDate due = t.getDueDate();
            if (due == null) continue;
            minTask = (minTask == null) ? due : (due.isBefore(minTask) ? due : minTask);
            maxTask = (maxTask == null) ? due : (due.isAfter(maxTask) ? due : maxTask);
        }

        rangeStartDate = (p.getStartDate() != null) ? p.getStartDate() : (minTask != null ? minTask : fallbackStart);
        rangeEndDate = (p.getEndDate() != null) ? p.getEndDate() : (maxTask != null ? maxTask : fallbackEnd);

        LocalDate min = (minTask != null) ? minTask : fallbackStart;
        LocalDate max = (maxTask != null) ? maxTask : fallbackEnd;

        if (p.getStartDate() != null && p.getStartDate().isBefore(min)) min = p.getStartDate();
        if (p.getEndDate() != null && p.getEndDate().isAfter(max)) max = p.getEndDate();

        autoMinDate = min.minusDays(2);
        autoMaxDate = max.plusDays(2);

        if (autoMaxDate.isBefore(autoMinDate)) {
            autoMinDate = fallbackStart;
            autoMaxDate = fallbackEnd;
        }
    }

    private void applyRangeOverrides() {
        minDate = viewStartOverride != null ? viewStartOverride : autoMinDate;
        maxDate = viewEndOverride != null ? viewEndOverride : autoMaxDate;

        if (minDate != null && maxDate != null && minDate.isAfter(maxDate)) {
            LocalDate tmp = minDate;
            minDate = maxDate;
            maxDate = tmp;
        }
    }

    private void syncRangePickers() {
        syncingRange = true;
        try {
            viewStartPicker.setValue(minDate);
            viewEndPicker.setValue(maxDate);
        } finally {
            syncingRange = false;
        }
    }

    // --- rest of your file unchanged (buildHeaderRow, rows, dependencies, workload, etc.) ---

    private HBox buildHeaderRow() {
        HBox row = new HBox(ROW_GAP);
        row.getStyleClass().add("gantt-header-row");
        row.setAlignment(Pos.CENTER_LEFT);

        Label taskHeader = new Label("Task");
        taskHeader.getStyleClass().add("gantt-header-label");
        taskHeader.setMinWidth(labelColWidth);
        taskHeader.setPrefWidth(labelColWidth);

        Pane timelineHeader = new Pane();
        timelineHeader.setMinHeight(28);
        timelineHeader.setPrefHeight(28);

        int days = daysInclusive(minDate, maxDate);
        timelineWidth = days * dayWidth;
        timelineHeader.setMinWidth(timelineWidth);
        timelineHeader.setPrefWidth(timelineWidth);

        if (days > 0) {
            int tickStep = tickStepDays();
            int labelStep = labelStepDays();
            LocalDate d = minDate;
            for (int i = 0; i < days; i++, d = d.plusDays(1)) {
                if (i % tickStep == 0) {
                    double x = i * dayWidth;
                    Region tick = new Region();
                    tick.getStyleClass().add("gantt-tick");
                    tick.setLayoutX(x);
                    tick.setLayoutY(0);
                    tick.setPrefWidth(1);
                    tick.setPrefHeight(28);
                    timelineHeader.getChildren().add(tick);
                }

                if (shouldShowLabel(i, d, labelStep)) {
                    double x = i * dayWidth;
                    Label lbl = new Label(formatHeaderLabel(d));
                    lbl.getStyleClass().add("gantt-header-label");
                    if (dayWidth < 16) {
                        lbl.getStyleClass().add("gantt-header-label-compact");
                    }
                    lbl.setLayoutX(x + 6);
                    lbl.setLayoutY(4);
                    timelineHeader.getChildren().add(lbl);
                }
            }
        }

        row.getChildren().addAll(taskHeader, timelineHeader);
        return row;
    }

    private int tickStepDays() {
        int base = labelMode.baseTickDays();
        int dynamic = (int) Math.ceil(14.0 / Math.max(1.0, dayWidth));
        return Math.max(base, dynamic);
    }

    private int labelStepDays() {
        int base = labelMode == LabelMode.DAY ? 1 : 7;
        int minPx = labelMode == LabelMode.DAY ? 36 : 48;
        int dynamic = (int) Math.ceil(minPx / Math.max(1.0, dayWidth));
        return Math.max(base, dynamic);
    }

    private boolean shouldShowLabel(int index, LocalDate date, int labelStep) {
        if (labelMode == LabelMode.MONTH) return date.getDayOfMonth() == 1;
        return index % Math.max(1, labelStep) == 0;
    }

    private String formatHeaderLabel(LocalDate date) {
        if (labelMode == LabelMode.MONTH) return date.format(MONTH_FMT);
        if (labelMode == LabelMode.DAY) {
            return dayWidth < 24 ? date.format(DAY_SHORT) : date.format(D);
        }
        return date.format(D);
    }

    private void fitDatesToViewport() {
        if (minDate == null || maxDate == null) return;

        int days = daysInclusive(minDate, maxDate);
        if (days <= 0) return;

        double viewport = scroll.getViewportBounds() == null ? 0 : scroll.getViewportBounds().getWidth();
        if (viewport <= 0) return;

        double availableTimelineWidth = Math.max(200, viewport - (labelColWidth + ROW_GAP + 40));
        double target = availableTimelineWidth / days;

        dayWidth = clamp(target, 10, 28);
        requestRebuild();
    }

    private MemberOption findAssigneeOption(Member member) {
        if (member == null) return taskAssignee.getItems().isEmpty() ? null : taskAssignee.getItems().get(0);
        for (MemberOption opt : taskAssignee.getItems()) {
            if (opt.member() != null && opt.member().getId().equals(member.getId())) return opt;
        }
        return taskAssignee.getItems().isEmpty() ? null : taskAssignee.getItems().get(0);
    }

    private static int daysInclusive(LocalDate a, LocalDate b) {
        if (a == null || b == null) return 0;
        long d = ChronoUnit.DAYS.between(a, b);
        return (int) Math.max(0, d + 1);
    }

    private static double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static String safe(String s) {
        return (s == null) ? "" : s;
    }

    private enum LabelMode {
        DAY("Day", 1),
        WEEK("Week", 7),
        MONTH("Month", 7);

        private final String label;
        private final int baseTickDays;

        LabelMode(String label, int baseTickDays) {
            this.label = label;
            this.baseTickDays = baseTickDays;
        }

        int baseTickDays() { return baseTickDays; }

        @Override public String toString() { return label; }
    }

    private record PhaseGroup(String phaseId, String name, List<Task> tasks) { }
    private record Dependency(Task from, Task to) { }

    private record MemberOption(Member member, String label) {
        @Override public String toString() { return label == null ? "" : label; }
    }

    private static final class GroupNode extends Pane {
        GroupNode(Node... nodes) { getChildren().addAll(nodes); }
    }
}
