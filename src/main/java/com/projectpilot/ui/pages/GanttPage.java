package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.TaskStatus;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class GanttPage extends VBox {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("MM-dd");

    private final InMemoryStore store;
    private final AppState appState;

    private final Label header = new Label("Gantt");
    private final Label sub = new Label("");

    // Project timeline strip (under subtitle)
    private final Label rangeStart = new Label("-");
    private final Label rangeEnd = new Label("-");
    private final Label rangeMeta = new Label("");
    private final Region rangeLine = new Region();
    private final HBox rangeRow = new HBox(10);

    private final TextField search = new TextField();
    private final CheckBox hideDone = new CheckBox("Hide DONE");
    private final Button fitDates = new Button("Fit dates");

    private final ScrollPane scroll = new ScrollPane();
    private final VBox sheet = new VBox(10);

    private double dayWidth = 22;         // pixels per day
    private double labelColWidth = 320;   // left column width
    private int defaultDurationDays = 7;  // bar length when we only have dueDate

    // chart range (padded)
    private LocalDate minDate;
    private LocalDate maxDate;

    // display/project range (un-padded)
    private LocalDate rangeStartDate;
    private LocalDate rangeEndDate;

    public GanttPage(InMemoryStore store, AppState appState) {
        this.store = store;
        this.appState = appState;

        setPadding(new Insets(16));
        setSpacing(14);

        header.getStyleClass().add("page-title");
        sub.getStyleClass().add("muted");

        // timeline strip styles
        rangeStart.getStyleClass().add("muted");
        rangeEnd.getStyleClass().add("muted");
        rangeMeta.getStyleClass().add("muted");
        rangeLine.getStyleClass().add("gantt-range-line");

        HBox.setHgrow(rangeLine, Priority.ALWAYS);
        rangeRow.setAlignment(Pos.CENTER_LEFT);
        rangeRow.getChildren().addAll(rangeStart, rangeLine, rangeEnd, rangeMeta);

        // top card
        search.setPromptText("Search tasks...");
        search.setPrefWidth(380);

        hideDone.getStyleClass().add("gantt-check"); // CSS will force white label text

        HBox controls = new HBox(12, search, hideDone, fitDates);
        controls.setAlignment(Pos.CENTER_LEFT);

        VBox topCard = new VBox(10, header, sub, rangeRow, controls);
        topCard.getStyleClass().add("card");
        topCard.setPadding(new Insets(14));

        // scroll + sheet
        scroll.getStyleClass().add("gantt-scroll"); // CSS: hide arrow buttons here
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setPannable(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        sheet.getStyleClass().add("gantt-sheet");
        scroll.setContent(sheet);

        VBox ganttCard = new VBox(scroll);
        ganttCard.getStyleClass().add("gantt-card");
        VBox.setVgrow(ganttCard, Priority.ALWAYS);

        getChildren().addAll(topCard, ganttCard);
        VBox.setVgrow(ganttCard, Priority.ALWAYS);

        // wiring
        search.textProperty().addListener((obs, o, n) -> rebuild());
        hideDone.selectedProperty().addListener((obs, o, n) -> rebuild());
        fitDates.setOnAction(e -> fitDatesToViewport());

        store.getProjects().addListener((ListChangeListener<Project>) c -> rebuild());

        // selected project change: swap task listeners + rebuild once
        appState.selectedProjectProperty().addListener((obs, oldP, newP) -> {
            if (oldP != null) oldP.getTasks().removeListener(tasksListener);
            if (newP != null) newP.getTasks().addListener(tasksListener);
            rebuild();
        });

        if (appState.getSelectedProject() != null) {
            appState.getSelectedProject().getTasks().addListener(tasksListener);
        }

        // initial
        rebuild();

        // make Fit Dates reliable after layout
        Platform.runLater(this::fitDatesToViewport);
    }

    private final ListChangeListener<Task> tasksListener = c -> rebuild();

    private void rebuild() {
        Project p = appState.getSelectedProject();
        if (p == null) {
            sub.setText("No project selected • timeline based on task due dates");
            setRangeStrip(null, null);
            Label msg = new Label("Select a project to view its Gantt.");
            msg.getStyleClass().add("muted");
            sheet.getChildren().setAll(msg);
            return;
        }

        sub.setText(p.getName() + "  •  timeline based on task due dates");

        List<Task> tasks = filteredTasks(p);
        computeDateRange(p, tasks);
        setRangeStrip(rangeStartDate, rangeEndDate);

        sheet.getChildren().clear();
        sheet.getChildren().add(buildHeaderRow());
        sheet.getChildren().add(buildRows(tasks));
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

    private List<Task> filteredTasks(Project p) {
        String q = (search.getText() == null) ? "" : search.getText().trim().toLowerCase();

        return p.getTasks().stream()
                .filter(t -> !hideDone.isSelected() || t.getStatus() != TaskStatus.DONE)
                .filter(t -> q.isBlank() || safe(t.getTitle()).toLowerCase().contains(q))
                .sorted(Comparator
                        .comparing(Task::getStatus)
                        .thenComparing(Task::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
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

        // Display range (un-padded): prefer project dates, else task min/max, else fallback
        rangeStartDate = (p.getStartDate() != null) ? p.getStartDate() : (minTask != null ? minTask : fallbackStart);
        rangeEndDate = (p.getEndDate() != null) ? p.getEndDate() : (maxTask != null ? maxTask : fallbackEnd);

        // Chart range (padded): include project dates if present, else task range, else fallback
        LocalDate min = (minTask != null) ? minTask : fallbackStart;
        LocalDate max = (maxTask != null) ? maxTask : fallbackEnd;

        if (p.getStartDate() != null && p.getStartDate().isBefore(min)) min = p.getStartDate();
        if (p.getEndDate() != null && p.getEndDate().isAfter(max)) max = p.getEndDate();

        // pad a bit so bars don’t hug edges
        minDate = min.minusDays(2);
        maxDate = max.plusDays(2);

        if (maxDate.isBefore(minDate)) {
            minDate = fallbackStart;
            maxDate = fallbackEnd;
        }
    }

    private HBox buildHeaderRow() {
        HBox row = new HBox(12);
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
        double timelineWidth = days * dayWidth;
        timelineHeader.setMinWidth(timelineWidth);
        timelineHeader.setPrefWidth(timelineWidth);

        // ticks every 7 days
        LocalDate d = minDate;
        for (int i = 0; i < days; i++, d = d.plusDays(1)) {
            if (i % 7 == 0) {
                double x = i * dayWidth;

                Region tick = new Region();
                tick.getStyleClass().add("gantt-tick");
                tick.setLayoutX(x);
                tick.setLayoutY(0);
                tick.setPrefWidth(1);
                tick.setPrefHeight(28);

                Label lbl = new Label(d.format(D));
                lbl.getStyleClass().add("gantt-header-label");
                lbl.setStyle("-fx-font-size: 11px;");
                lbl.setLayoutX(x + 6);
                lbl.setLayoutY(4);

                timelineHeader.getChildren().addAll(tick, lbl);
            }
        }

        row.getChildren().addAll(taskHeader, timelineHeader);
        return row;
    }

    private VBox buildRows(List<Task> tasks) {
        VBox rows = new VBox(6);

        if (tasks.isEmpty()) {
            Label empty = new Label("No tasks match your filters.");
            empty.getStyleClass().add("muted");
            rows.getChildren().add(empty);
            return rows;
        }

        for (Task t : tasks) {
            rows.getChildren().add(buildTaskRow(t));
        }

        return rows;
    }

    private HBox buildTaskRow(Task t) {
        HBox row = new HBox(12);
        row.getStyleClass().add("gantt-row");
        row.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label(safe(t.getTitle()));
        label.getStyleClass().add("gantt-task-label");
        label.setMinWidth(labelColWidth);
        label.setPrefWidth(labelColWidth);

        if (t.getDueDate() == null) {
            label.getStyleClass().add("gantt-task-muted");
        }

        Pane timeline = new Pane();
        timeline.setMinHeight(34);
        timeline.setPrefHeight(34);

        int days = daysInclusive(minDate, maxDate);
        double timelineWidth = days * dayWidth;
        timeline.setMinWidth(timelineWidth);
        timeline.setPrefWidth(timelineWidth);

        // vertical ticks (subtle), every 7 days
        for (int i = 0; i < days; i += 7) {
            Region tick = new Region();
            tick.getStyleClass().add("gantt-tick");
            tick.setLayoutX(i * dayWidth);
            tick.setLayoutY(0);
            tick.setPrefWidth(1);
            tick.setPrefHeight(34);
            timeline.getChildren().add(tick);
        }

        Region bar = buildBar(t);
        if (bar != null) timeline.getChildren().add(bar);

        row.getChildren().addAll(label, timeline);
        return row;
    }

    private Region buildBar(Task t) {
        LocalDate due = t.getDueDate();
        if (due == null) return null;

        LocalDate start = due.minusDays(defaultDurationDays - 1);

        // keep inside chart bounds
        if (start.isBefore(minDate)) start = minDate;
        if (due.isAfter(maxDate)) due = maxDate;

        int startOffset = (int) ChronoUnit.DAYS.between(minDate, start);
        int endOffset = (int) ChronoUnit.DAYS.between(minDate, due);

        double x = startOffset * dayWidth;
        double w = Math.max(dayWidth, (endOffset - startOffset + 1) * dayWidth);

        Region bar = new Region();
        bar.getStyleClass().addAll("gantt-bar", statusClass(t.getStatus()));
        bar.setLayoutX(x);
        bar.setLayoutY(7);
        bar.setPrefHeight(20);
        bar.setPrefWidth(w);

        String tip = safe(t.getTitle()) +
                "\nStatus: " + t.getStatus() +
                "\nDue: " + t.getDueDate() +
                (t.getDescription() == null || t.getDescription().isBlank() ? "" : "\n\n" + t.getDescription().trim());
        Tooltip.install(bar, new Tooltip(tip));

        return bar;
    }

    private void fitDatesToViewport() {
        if (minDate == null || maxDate == null) return;

        int days = daysInclusive(minDate, maxDate);
        if (days <= 0) return;

        double viewport = scroll.getViewportBounds() == null ? 0 : scroll.getViewportBounds().getWidth();
        if (viewport <= 0) return;

        double availableTimelineWidth = Math.max(200, viewport - (labelColWidth + 40));
        double target = availableTimelineWidth / days;

        // clamp so it never becomes ugly
        dayWidth = clamp(target, 10, 28);

        rebuild();
    }

    private static String statusClass(TaskStatus s) {
        if (s == null) return "todo";
        return switch (s) {
            case TODO -> "todo";
            case IN_PROGRESS -> "inprogress";
            case BLOCKED -> "blocked";
            case DONE -> "done";
        };
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
}
