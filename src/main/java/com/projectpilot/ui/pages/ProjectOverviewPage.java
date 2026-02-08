package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.ActivityItem;
import com.projectpilot.model.Member;
import com.projectpilot.model.Milestone;
import com.projectpilot.model.Phase;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.TaskStatus;
import com.projectpilot.security.AccessPolicy;
import com.projectpilot.service.ProgressService;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Polyline;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProjectOverviewPage extends VBox {

    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("MMM d");

    private final InMemoryStore store;
    private final AppState appState;
    private final ProgressService progressService = new ProgressService();
    private final AccessPolicy policy = new AccessPolicy();

    private final Label header = new Label("Project Overview");
    private final Label sub = new Label("");
    private final Label dateRange = new Label("-");

    private final Label descTitle = new Label("Description");
    private final Label stakeholderTitle = new Label("Stakeholders");
    private final Label descText = new Label("-");
    private final Label stakeholderText = new Label("-");
    private final TextArea descArea = new TextArea();
    private final TextArea stakeholderArea = new TextArea();

    private final Button editSaveBtn = new Button("Edit");
    private final Button cancelBtn = new Button("Cancel");
    private boolean editing = false;

    private final KpiTile progressTile = new KpiTile("Progress");
    private final KpiTile openTile = new KpiTile("Open Tasks");
    private final KpiTile overdueTile = new KpiTile("Overdue");
    private final KpiTile blockedTile = new KpiTile("Blocked");

    private final Label pulseRisk = new Label("-");
    private final Label pulseOverdue = new Label("-");
    private final Label pulseBlocked = new Label("-");
    private final Label pulseLoad = new Label("-");
    private final Label momentumHint = new Label("Upcoming load (14 days)");
    private final Sparkline momentumSpark = new Sparkline();
    private final Label statusHint = new Label("Status split");
    private final HBox statusSplit = new HBox(2);
    private final Label statusSummary = new Label("-");

    private final Label phaseSummary = new Label("-");
    private final VBox phaseList = new VBox(8);

    private final Label milestoneSummary = new Label("-");
    private final ListView<Milestone> milestoneList = new ListView<>();

    private final Label nextWeekHint = new Label("-");
    private final VBox nextWeekList = new VBox(6);

    private final Label activityHint = new Label("-");
    private final VBox activityTimeline = new VBox(6);

    private final Label deadlinesHint = new Label("-");
    private final VBox deadlinesList = new VBox(6);

    private final BooleanBinding canEdit;

    private Project boundProject;
    private final Map<Task, InvalidationListener> taskHooks = new HashMap<>();
    private final Map<Milestone, InvalidationListener> milestoneHooks = new HashMap<>();

    private final ListChangeListener<Task> taskListListener = c -> {
        while (c.next()) {
            if (c.wasAdded()) {
                for (Task t : c.getAddedSubList()) hookTask(t);
            }
            if (c.wasRemoved()) {
                for (Task t : c.getRemoved()) unhookTask(t);
            }
        }
        updateDerived(boundProject);
    };

    private final ListChangeListener<Milestone> milestoneListListener = c -> {
        while (c.next()) {
            if (c.wasAdded()) {
                for (Milestone m : c.getAddedSubList()) hookMilestone(m);
            }
            if (c.wasRemoved()) {
                for (Milestone m : c.getRemoved()) unhookMilestone(m);
            }
        }
        updateMilestones(boundProject);
        updateDeadlines(boundProject);
    };

    public ProjectOverviewPage(InMemoryStore store, AppState appState) {
        this.store = store;
        this.appState = appState;

        this.canEdit = Bindings.createBooleanBinding(
                () -> policy.canEditProjectOverview(appState),
                appState.sessionProperty(),
                appState.selectedProjectProperty(),
                appState.currentProjectRoleProperty()
        );

        setPadding(new Insets(0));
        setSpacing(0);

        header.getStyleClass().add("page-title");
        sub.getStyleClass().add("muted");
        dateRange.getStyleClass().add("muted");

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
        editSaveBtn.visibleProperty().bind(canEdit);
        editSaveBtn.managedProperty().bind(editSaveBtn.visibleProperty());

        cancelBtn.disableProperty().bind(canEdit.not());
        setNodeVisible(cancelBtn, false);

        editSaveBtn.setOnAction(e -> onEditSave());
        cancelBtn.setOnAction(e -> {
            setEditing(false);
            Project p = appState.getSelectedProject();
            if (p != null) updateProjectInfo(p);
        });

        progressTile.getStyleClass().addAll("metric-hero", "metric-accent", "overview-kpi");
        openTile.getStyleClass().addAll("metric-hero", "metric-success", "overview-kpi");
        overdueTile.getStyleClass().addAll("metric-hero", "metric-danger", "overview-kpi");
        blockedTile.getStyleClass().addAll("metric-hero", "metric-warning", "overview-kpi");

        VBox titleBlock = new VBox(2, header, sub, dateRange);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox titleRow = new HBox(12, titleBlock, spacer, editSaveBtn, cancelBtn);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        VBox briefBox = new VBox(8,
                descTitle, descText, descArea,
                stakeholderTitle, stakeholderText, stakeholderArea
        );
        VBox briefCard = new VBox(12, titleRow, briefBox);
        briefCard.getStyleClass().add("card");

        HBox kpiRow = new HBox(12, progressTile, openTile, overdueTile, blockedTile);
        kpiRow.getStyleClass().add("overview-kpi-row");
        for (KpiTile tile : List.of(progressTile, openTile, overdueTile, blockedTile)) {
            HBox.setHgrow(tile, Priority.ALWAYS);
            tile.setMaxWidth(Double.MAX_VALUE);
        }

        VBox pulseCard = buildPulseCard();
        VBox planCard = buildPlanCard();

        HBox pulseRow = new HBox(12, pulseCard, planCard);
        HBox.setHgrow(pulseCard, Priority.ALWAYS);
        HBox.setHgrow(planCard, Priority.ALWAYS);

        VBox phaseCard = buildPhaseCard();
        VBox milestoneCard = buildMilestoneCard();
        HBox midRow = new HBox(12, phaseCard, milestoneCard);
        HBox.setHgrow(phaseCard, Priority.ALWAYS);
        HBox.setHgrow(milestoneCard, Priority.ALWAYS);

        VBox activityCard = buildActivityCard();
        VBox deadlinesCard = buildDeadlinesCard();
        HBox bottomRow = new HBox(12, activityCard, deadlinesCard);
        HBox.setHgrow(activityCard, Priority.ALWAYS);
        HBox.setHgrow(deadlinesCard, Priority.ALWAYS);

        VBox content = new VBox(16, briefCard, kpiRow, pulseRow, midRow, bottomRow);
        content.setPadding(new Insets(16));

        ScrollPane scroll = new ScrollPane(content);
        scroll.getStyleClass().add("pp-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        getChildren().add(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        buildMilestones();

        refresh(appState.getSelectedProject());
        appState.selectedProjectProperty().addListener((obs, o, n) -> refresh(n));
        store.getActivity().addListener((ListChangeListener<ActivityItem>) c -> updateActivity(boundProject));
    }

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

    private void setNodeVisible(TextArea n, boolean v) {
        n.setVisible(v);
        n.setManaged(v);
    }

    private VBox buildPulseCard() {
        Label title = new Label("Project Pulse");
        title.getStyleClass().add("section-title");

        VBox riskBox = pulseMetric("Risk", pulseRisk);
        VBox overdueBox = pulseMetric("Overdue", pulseOverdue);
        VBox blockedBox = pulseMetric("Blocked", pulseBlocked);
        VBox loadBox = pulseMetric("Team load", pulseLoad);

        HBox metrics = new HBox(12, riskBox, overdueBox, blockedBox, loadBox);
        metrics.setAlignment(Pos.CENTER_LEFT);

        momentumHint.getStyleClass().add("section-hint");
        momentumSpark.setPrefHeight(46);

        statusHint.getStyleClass().add("section-hint");
        statusSplit.getStyleClass().add("overview-status-split");
        statusSplit.setMinHeight(8);
        statusSummary.getStyleClass().add("section-hint");

        VBox charts = new VBox(6, momentumHint, momentumSpark, statusHint, statusSplit, statusSummary);

        VBox box = new VBox(10, title, metrics, charts);
        box.getStyleClass().add("card");
        return box;
    }

    private VBox buildPhaseCard() {
        Label title = new Label("Phase Progress");
        title.getStyleClass().add("section-title");
        phaseSummary.getStyleClass().add("section-hint");

        phaseList.getStyleClass().add("overview-phase-list");

        VBox box = new VBox(10, title, phaseSummary, phaseList);
        box.getStyleClass().add("card");
        return box;
    }

    private VBox buildMilestoneCard() {
        Label title = new Label("Milestones");
        title.getStyleClass().add("section-title");
        milestoneSummary.getStyleClass().add("section-hint");

        milestoneList.getStyleClass().add("overview-milestones");
        milestoneList.setPrefHeight(220);

        VBox box = new VBox(10, title, milestoneSummary, milestoneList);
        box.getStyleClass().add("card");
        return box;
    }

    private VBox buildPlanCard() {
        Label title = new Label("Next 7-day Plan");
        title.getStyleClass().add("section-title");
        nextWeekHint.getStyleClass().add("section-hint");

        nextWeekList.getStyleClass().add("overview-list");

        VBox box = new VBox(10, title, nextWeekHint, nextWeekList);
        box.getStyleClass().add("card");
        return box;
    }

    private VBox buildActivityCard() {
        Label title = new Label("Recent Activity");
        title.getStyleClass().add("section-title");
        activityHint.getStyleClass().add("section-hint");

        activityTimeline.getStyleClass().add("overview-activity");

        VBox box = new VBox(10, title, activityHint, activityTimeline);
        box.getStyleClass().add("card");
        return box;
    }

    private VBox buildDeadlinesCard() {
        Label title = new Label("Upcoming Deadlines");
        title.getStyleClass().add("section-title");
        deadlinesHint.getStyleClass().add("section-hint");

        deadlinesList.getStyleClass().add("overview-list");

        VBox box = new VBox(10, title, deadlinesHint, deadlinesList);
        box.getStyleClass().add("card");
        return box;
    }

    private VBox pulseMetric(String title, Label value) {
        Label t = new Label(title);
        t.getStyleClass().add("muted");
        value.getStyleClass().add("overview-pulse-value");
        VBox box = new VBox(4, t, value);
        box.getStyleClass().add("overview-pulse-metric");
        return box;
    }

    private void buildMilestones() {
        milestoneList.setCellFactory(lv -> new ListCell<>() {
            private final CheckBox cb = new CheckBox();
            private final Label name = new Label();
            private final Label meta = new Label();
            private final Label status = new Label();
            private final Region spacer = new Region();
            private final HBox row = new HBox(8, cb, name, meta, spacer, status);
            private Milestone bound;

            {
                cb.getStyleClass().add("overview-milestone-check");
                cb.setFocusTraversable(false);
                name.getStyleClass().add("overview-title");
                meta.getStyleClass().add("overview-meta");
                status.getStyleClass().add("metric-chip");
                row.getStyleClass().add("overview-row");
                row.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(spacer, Priority.ALWAYS);
            }

            @Override
            protected void updateItem(Milestone m, boolean empty) {
                super.updateItem(m, empty);
                if (bound != null) {
                    cb.selectedProperty().unbindBidirectional(bound.completedProperty());
                    bound.completedProperty().removeListener(completedListener);
                }
                bound = m;

                if (empty || m == null) {
                    setGraphic(null);
                    return;
                }
                cb.selectedProperty().bindBidirectional(m.completedProperty());
                m.completedProperty().addListener(completedListener);

                name.setText(safe(m.nameProperty().get()));
                String due = m.dueDateProperty().get() == null ? "-" : m.dueDateProperty().get().format(SHORT_DATE);
                meta.setText(due);
                updateStatus(m.completedProperty().get());

                setGraphic(row);
            }

            private final ChangeListener<Boolean> completedListener = (obs, ov, nv) -> updateStatus(nv);

            private void updateStatus(boolean done) {
                status.setText(done ? "Done" : "Open");
                status.getStyleClass().removeAll("metric-chip-up", "metric-chip-neutral");
                status.getStyleClass().add(done ? "metric-chip-up" : "metric-chip-neutral");
            }
        });
    }

    private void refresh(Project p) {
        setEditing(false);
        bindProject(p);

        if (p == null) {
            sub.setText("No project selected");
            dateRange.setText("-");
            updateDerived(null);
            updateProjectInfo(null);
            return;
        }

        sub.setText(p.getName());
        dateRange.setText(dateRangeLabel(p));
        updateProjectInfo(p);
        updateDerived(p);
        milestoneList.setItems(p.getMilestones());
    }

    private void updateProjectInfo(Project p) {
        if (p == null) {
            descText.setText("-");
            stakeholderText.setText("-");
            return;
        }
        descText.setText(safeText(p.getDescription()));
        stakeholderText.setText(safeText(p.getStakeholders()));
    }

    private void updateDerived(Project p) {
        updateKpis(p);
        updatePulse(p);
        updateMomentum(p);
        updatePhaseProgress(p);
        updateMilestones(p);
        updateNextWeek(p);
        updateActivity(p);
        updateDeadlines(p);
    }

    private void updateKpis(Project p) {
        if (p == null) {
            progressTile.setValue("-");
            progressTile.setSub("No tasks");
            openTile.setValue("-");
            openTile.setSub("-");
            overdueTile.setValue("-");
            overdueTile.setSub("-");
            blockedTile.setValue("-");
            blockedTile.setSub("-");
            return;
        }

        List<Task> tasks = p.getTasks() == null ? List.of() : p.getTasks();
        long done = tasks.stream().filter(t -> t != null && t.getStatus() == TaskStatus.DONE).count();
        long open = tasks.stream().filter(t -> t != null && t.getStatus() != TaskStatus.DONE).count();
        long blocked = tasks.stream().filter(t -> t != null && t.getStatus() == TaskStatus.BLOCKED).count();
        long overdue = countOverdue(tasks);

        int pct = progressService.projectProgressPercent(p);
        progressTile.setValue(pct + "%");
        progressTile.setSub(done + "/" + tasks.size() + " done");

        openTile.setValue(Long.toString(open));
        openTile.setSub("Active tasks");

        overdueTile.setValue(Long.toString(overdue));
        overdueTile.setSub("Past due");

        blockedTile.setValue(Long.toString(blocked));
        blockedTile.setSub("Needs unblock");
    }

    private void updatePulse(Project p) {
        if (p == null) {
            pulseRisk.setText("-");
            pulseOverdue.setText("-");
            pulseBlocked.setText("-");
            pulseLoad.setText("-");
            return;
        }

        List<Task> tasks = p.getTasks() == null ? List.of() : p.getTasks();
        long overdue = countOverdue(tasks);
        long blocked = tasks.stream().filter(t -> t != null && t.getStatus() == TaskStatus.BLOCKED).count();

        pulseOverdue.setText(Long.toString(overdue));
        pulseBlocked.setText(Long.toString(blocked));

        String riskLabel;
        String riskStyle;
        if (overdue >= 3 || blocked >= 2) {
            riskLabel = "High risk";
            riskStyle = "risk-critical";
        } else if (overdue >= 1 || blocked >= 1) {
            riskLabel = "At risk";
            riskStyle = "risk-high";
        } else {
            riskLabel = "On track";
            riskStyle = "risk-low";
        }

        pulseRisk.getStyleClass().removeAll("risk-low", "risk-medium", "risk-high", "risk-critical", "risk-badge");
        pulseRisk.getStyleClass().addAll("risk-badge", riskStyle);
        pulseRisk.setText(riskLabel);

        Map<String, Integer> openByMember = new LinkedHashMap<>();
        for (Member m : p.getMembers()) {
            if (m != null) openByMember.put(m.getId(), 0);
        }

        int unassigned = 0;
        for (Task t : tasks) {
            if (t == null || t.getStatus() == TaskStatus.DONE) continue;
            Member m = t.getAssignee();
            if (m == null) {
                unassigned++;
            } else {
                openByMember.put(m.getId(), openByMember.getOrDefault(m.getId(), 0) + 1);
            }
        }

        int maxOpen = 0;
        for (int v : openByMember.values()) maxOpen = Math.max(maxOpen, v);
        int totalMembers = p.getMembers().size();
        if (totalMembers == 0) {
            pulseLoad.setText("No members");
        } else {
            String extra = unassigned > 0 ? " +" + unassigned + " unassigned" : "";
            pulseLoad.setText(maxOpen + " max / " + totalMembers + " members" + extra);
        }
    }

    private void updateMomentum(Project p) {
        if (p == null) {
            momentumSpark.setSeries(List.of());
            momentumHint.setText("Upcoming load (14 days)");
            statusSummary.setText("-");
            statusSplit.getChildren().clear();
            return;
        }

        List<Task> tasks = p.getTasks() == null ? List.of() : p.getTasks();
        LocalDate today = LocalDate.now();

        List<Integer> series = new ArrayList<>();
        int dueTotal = 0;
        for (int i = 0; i < 14; i++) {
            LocalDate day = today.plusDays(i);
            int count = 0;
            for (Task t : tasks) {
                if (t == null || t.getStatus() == TaskStatus.DONE) continue;
                LocalDate due = t.getDueDate();
                if (due != null && due.equals(day)) count++;
            }
            series.add(count);
            dueTotal += count;
        }
        momentumSpark.setSeries(series);
        momentumHint.setText("Upcoming load (14 days) - " + dueTotal + " due");

        long todo = 0;
        long inProgress = 0;
        long blocked = 0;
        long done = 0;
        for (Task t : tasks) {
            if (t == null) continue;
            TaskStatus status = t.getStatus() == null ? TaskStatus.TODO : t.getStatus();
            switch (status) {
                case IN_PROGRESS -> inProgress++;
                case BLOCKED -> blocked++;
                case DONE -> done++;
                default -> todo++;
            }
        }

        long total = todo + inProgress + blocked + done;
        statusSplit.getChildren().clear();
        if (total == 0) {
            Region empty = new Region();
            empty.getStyleClass().addAll("overview-status-seg", "status-empty");
            empty.setPrefWidth(120);
            statusSplit.getChildren().add(empty);
            statusSummary.setText("No tasks yet");
            return;
        }

        addStatusSegment(statusSplit, todo, total, "status-todo");
        addStatusSegment(statusSplit, inProgress, total, "status-progress");
        addStatusSegment(statusSplit, blocked, total, "status-blocked");
        addStatusSegment(statusSplit, done, total, "status-done");

        statusSummary.setText("Todo " + todo + " | In progress " + inProgress
                + " | Blocked " + blocked + " | Done " + done);
    }

    private void addStatusSegment(HBox bar, long count, long total, String style) {
        if (total <= 0) return;
        double ratio = count / (double) total;
        if (ratio <= 0) return;
        Region seg = new Region();
        seg.getStyleClass().addAll("overview-status-seg", style);
        seg.setMinWidth(6);
        seg.setPrefWidth(Math.max(6, ratio * 140));
        bar.getChildren().add(seg);
    }

    private void updatePhaseProgress(Project p) {
        phaseList.getChildren().clear();

        if (p == null || p.getPhases() == null || p.getPhases().isEmpty()) {
            phaseSummary.setText("No phases yet");
            Label empty = new Label("Add phases to see progress.");
            empty.getStyleClass().add("muted");
            phaseList.getChildren().add(empty);
            return;
        }

        phaseSummary.setText(p.getPhases().size() + " phases");
        for (Phase ph : p.getPhases()) {
            int pct = phaseProgressPercent(p, ph);
            HBox row = buildPhaseRow(ph == null ? "-" : safe(ph.getName()), pct);
            phaseList.getChildren().add(row);
        }
    }

    private HBox buildPhaseRow(String name, int pct) {
        Label label = new Label(name);
        label.getStyleClass().add("overview-title");

        ProgressBar bar = new ProgressBar(pct / 100.0);
        bar.getStyleClass().add("overview-phase-bar");
        bar.setMaxWidth(Double.MAX_VALUE);

        Label pctLabel = new Label(pct + "%");
        pctLabel.getStyleClass().add("overview-meta");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(8, label, spacer, pctLabel);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox wrapper = new VBox(6, row, bar);
        wrapper.getStyleClass().add("overview-row");
        return new HBox(wrapper);
    }

    private void updateMilestones(Project p) {
        if (p == null || p.getMilestones() == null) {
            milestoneSummary.setText("No milestones");
            milestoneList.setItems(null);
            return;
        }

        long done = p.getMilestones().stream().filter(m -> m != null && m.completedProperty().get()).count();
        long total = p.getMilestones().size();
        milestoneSummary.setText(done + "/" + total + " complete");
        milestoneList.refresh();
    }

    private void updateNextWeek(Project p) {
        nextWeekList.getChildren().clear();
        if (p == null) {
            nextWeekHint.setText("No project selected");
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate end = today.plusDays(7);
        List<Task> tasks = new ArrayList<>();
        for (Task t : p.getTasks()) {
            if (t == null || t.getStatus() == TaskStatus.DONE) continue;
            LocalDate due = t.getDueDate();
            if (due == null) continue;
            if (!due.isBefore(today) && !due.isAfter(end)) tasks.add(t);
        }

        tasks.sort(Comparator.comparing(Task::getDueDate));
        if (tasks.isEmpty()) {
            nextWeekHint.setText("No tasks due in the next 7 days.");
            Label empty = new Label("Plan work or pull tasks forward.");
            empty.getStyleClass().add("muted");
            nextWeekList.getChildren().add(empty);
            return;
        }

        nextWeekHint.setText(tasks.size() + " tasks due this week");
        int limit = Math.min(6, tasks.size());
        for (int i = 0; i < limit; i++) {
            nextWeekList.getChildren().add(buildTaskRow(tasks.get(i)));
        }
    }

    private void updateActivity(Project p) {
        activityTimeline.getChildren().clear();
        if (p == null) {
            activityHint.setText("No project selected");
            return;
        }

        List<ActivityItem> items = recentActivity(p, 6);
        if (items.isEmpty()) {
            activityHint.setText("No recent changes yet.");
            Label empty = new Label("Updates will appear as work happens.");
            empty.getStyleClass().add("muted");
            activityTimeline.getChildren().add(empty);
            return;
        }

        activityHint.setText("Latest changes in this project");
        for (int i = 0; i < items.size(); i++) {
            ActivityItem item = items.get(i);
            HBox row = new HBox(8);
            row.getStyleClass().add("project-activity-row");

            VBox marker = new VBox(2);
            marker.getStyleClass().add("activity-marker");
            Region dot = new Region();
            dot.getStyleClass().add("activity-dot");
            Region line = new Region();
            line.getStyleClass().add("activity-line");
            if (i == items.size() - 1) line.getStyleClass().add("activity-line-end");
            marker.getChildren().addAll(dot, line);

            Label message = new Label(activityMessage(item));
            message.getStyleClass().add("activity-text");
            Label time = new Label(formatActivityTime(item.getTime()));
            time.getStyleClass().add("activity-time");

            VBox content = new VBox(2, message, time);
            row.getChildren().addAll(marker, content);
            activityTimeline.getChildren().add(row);
        }
    }

    private void updateDeadlines(Project p) {
        deadlinesList.getChildren().clear();
        if (p == null) {
            deadlinesHint.setText("No project selected");
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate weekEnd = today.plusDays(7);
        LocalDate horizon = today.plusDays(30);

        List<DeadlineItem> items = new ArrayList<>();

        if (p.getEndDate() != null && !p.getEndDate().isBefore(today) && !p.getEndDate().isAfter(horizon)) {
            items.add(new DeadlineItem("Project end", p.getEndDate(), "Project"));
        }

        for (Milestone m : p.getMilestones()) {
            if (m == null || m.completedProperty().get()) continue;
            LocalDate due = m.dueDateProperty().get();
            if (due == null) continue;
            if (!due.isBefore(today) && !due.isAfter(horizon)) {
                items.add(new DeadlineItem(safe(m.nameProperty().get()), due, "Milestone"));
            }
        }

        for (Task t : p.getTasks()) {
            if (t == null || t.getStatus() == TaskStatus.DONE) continue;
            LocalDate due = t.getDueDate();
            if (due == null) continue;
            if (due.isAfter(weekEnd) && !due.isAfter(horizon)) {
                items.add(new DeadlineItem(safe(t.getTitle()), due, "Task"));
            }
        }

        items.sort(Comparator.comparing(DeadlineItem::date));
        if (items.isEmpty()) {
            deadlinesHint.setText("No upcoming deadlines in the next 30 days.");
            Label empty = new Label("You are clear beyond this week.");
            empty.getStyleClass().add("muted");
            deadlinesList.getChildren().add(empty);
            return;
        }

        deadlinesHint.setText(items.size() + " upcoming items");
        int limit = Math.min(6, items.size());
        for (int i = 0; i < limit; i++) {
            deadlinesList.getChildren().add(buildDeadlineRow(items.get(i)));
        }
    }

    private void bindProject(Project p) {
        if (boundProject != null) {
            try { boundProject.getTasks().removeListener(taskListListener); } catch (Exception ignored) {}
            try { boundProject.getMilestones().removeListener(milestoneListListener); } catch (Exception ignored) {}
        }

        for (Task t : new HashSet<>(taskHooks.keySet())) unhookTask(t);
        for (Milestone m : new HashSet<>(milestoneHooks.keySet())) unhookMilestone(m);

        boundProject = p;
        if (p == null) return;

        p.getTasks().addListener(taskListListener);
        p.getMilestones().addListener(milestoneListListener);

        for (Task t : p.getTasks()) hookTask(t);
        for (Milestone m : p.getMilestones()) hookMilestone(m);
    }

    private void hookTask(Task t) {
        if (t == null || taskHooks.containsKey(t)) return;
        InvalidationListener l = obs -> updateDerived(boundProject);
        t.statusProperty().addListener(l);
        t.dueDateProperty().addListener(l);
        t.assigneeProperty().addListener(l);
        t.priorityProperty().addListener(l);
        t.phaseProperty().addListener(l);
        taskHooks.put(t, l);
    }

    private void unhookTask(Task t) {
        if (t == null) return;
        InvalidationListener l = taskHooks.remove(t);
        if (l == null) return;
        try { t.statusProperty().removeListener(l); } catch (Exception ignored) {}
        try { t.dueDateProperty().removeListener(l); } catch (Exception ignored) {}
        try { t.assigneeProperty().removeListener(l); } catch (Exception ignored) {}
        try { t.priorityProperty().removeListener(l); } catch (Exception ignored) {}
        try { t.phaseProperty().removeListener(l); } catch (Exception ignored) {}
    }

    private void hookMilestone(Milestone m) {
        if (m == null || milestoneHooks.containsKey(m)) return;
        InvalidationListener l = obs -> {
            updateMilestones(boundProject);
            updateDeadlines(boundProject);
        };
        m.completedProperty().addListener(l);
        m.dueDateProperty().addListener(l);
        milestoneHooks.put(m, l);
    }

    private void unhookMilestone(Milestone m) {
        if (m == null) return;
        InvalidationListener l = milestoneHooks.remove(m);
        if (l == null) return;
        try { m.completedProperty().removeListener(l); } catch (Exception ignored) {}
        try { m.dueDateProperty().removeListener(l); } catch (Exception ignored) {}
    }

    private HBox buildTaskRow(Task t) {
        Label title = new Label(safe(t.getTitle()));
        title.getStyleClass().add("overview-title");

        String due = t.getDueDate() == null ? "-" : t.getDueDate().format(SHORT_DATE);
        Label dueLabel = new Label(due);
        dueLabel.getStyleClass().add("overview-meta");

        Label status = new Label(t.getStatus() == null ? "" : t.getStatus().name());
        status.getStyleClass().addAll("metric-chip", "metric-chip-neutral");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(8, title, dueLabel, spacer, status);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("overview-row");
        return row;
    }

    private HBox buildDeadlineRow(DeadlineItem item) {
        Label title = new Label(item.label());
        title.getStyleClass().add("overview-title");

        Label due = new Label(item.date().format(SHORT_DATE));
        due.getStyleClass().add("overview-meta");

        Label type = new Label(item.type());
        type.getStyleClass().addAll("metric-chip", "metric-chip-neutral");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(8, title, due, spacer, type);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("overview-row");
        return row;
    }

    private List<ActivityItem> recentActivity(Project p, int limit) {
        List<ActivityItem> out = new ArrayList<>();
        if (p == null) return out;
        String pid = p.getId();
        if (pid == null) return out;

        for (ActivityItem item : store.getActivity()) {
            if (item == null) continue;
            if (!pid.equals(item.getProjectId())) continue;
            out.add(item);
            if (out.size() >= limit) break;
        }
        return out;
    }

    private String activityMessage(ActivityItem item) {
        if (item == null) return "Update";
        String msg = safe(item.getMessage());
        if (!msg.isBlank()) return msg;
        String type = safe(item.getEntityType());
        String action = safe(item.getAction());
        String text = (type + " " + action).trim();
        return text.isEmpty() ? "Update" : text;
    }

    private String formatActivityTime(LocalDateTime time) {
        if (time == null) return "-";
        LocalDateTime now = LocalDateTime.now();
        long minutes = java.time.Duration.between(time, now).toMinutes();
        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        if (days < 7) return days + "d ago";
        return time.format(SHORT_DATE);
    }

    private long countOverdue(List<Task> tasks) {
        LocalDate today = LocalDate.now();
        return tasks.stream()
                .filter(t -> t != null && t.getStatus() != TaskStatus.DONE)
                .filter(t -> t.getDueDate() != null && t.getDueDate().isBefore(today))
                .count();
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

    private String dateRangeLabel(Project p) {
        if (p == null) return "-";
        LocalDate start = p.getStartDate();
        LocalDate end = p.getEndDate();
        if (start == null && end == null) return "No dates";
        if (start == null) return "Until " + end.format(SHORT_DATE);
        if (end == null) return "From " + start.format(SHORT_DATE);
        return start.format(SHORT_DATE) + " - " + end.format(SHORT_DATE);
    }

    private String safeText(String s) {
        return (s == null || s.trim().isEmpty()) ? "-" : s.trim();
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim();
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private record DeadlineItem(String label, LocalDate date, String type) {}

    private static final class KpiTile extends VBox {
        private final Label label = new Label();
        private final Label value = new Label("-");
        private final Label sub = new Label("");

        KpiTile(String title) {
            setPadding(new Insets(16, 18, 16, 18));
            setSpacing(6);
            setMinHeight(110);

            label.setText(title == null ? "" : title);
            label.getStyleClass().add("metric-label");
            value.getStyleClass().add("overview-kpi-value");
            sub.getStyleClass().add("overview-kpi-sub");

            getChildren().addAll(label, value, sub);
        }

        void setValue(String text) {
            value.setText(text == null ? "-" : text);
        }

        void setSub(String text) {
            sub.setText(text == null ? "" : text);
        }
    }

    private static final class Sparkline extends Region {
        private final Polyline line = new Polyline();
        private List<Integer> series = List.of();

        Sparkline() {
            getStyleClass().add("overview-sparkline");
            line.getStyleClass().add("overview-sparkline-line");
            getChildren().add(line);
        }

        void setSeries(List<Integer> values) {
            series = values == null ? List.of() : values;
            requestLayout();
        }

        @Override
        protected void layoutChildren() {
            double w = getWidth();
            double h = getHeight();
            if (w <= 0 || h <= 0 || series.isEmpty()) {
                line.getPoints().clear();
                return;
            }

            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (int v : series) {
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            if (min == max) max = min + 1;

            double padX = 4;
            double padY = 4;
            double usableW = Math.max(1, w - padX * 2);
            double usableH = Math.max(1, h - padY * 2);

            int count = series.size();
            line.getPoints().clear();
            for (int i = 0; i < count; i++) {
                double x = padX + (usableW * i / Math.max(1, count - 1));
                double norm = (series.get(i) - min) / (double) (max - min);
                double y = padY + (usableH * (1 - norm));
                line.getPoints().addAll(x, y);
            }
        }
    }
}
