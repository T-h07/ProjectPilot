package com.projectpilot.ui.pages;

import com.projectpilot.core.AppState;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.model.Member;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import com.projectpilot.model.enums.Priority;
import com.projectpilot.model.enums.TaskStatus;
import com.projectpilot.security.AccessPolicy;
import com.projectpilot.ui.components.ProjectPicker;
import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class CalendarPage extends VBox {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MMMM yyyy");

    private final InMemoryStore store;
    private final AppState appState;
    private final AccessPolicy policy = new AccessPolicy();

    private final ObjectProperty<YearMonth> month = new SimpleObjectProperty<>(YearMonth.now());

    private final Label header = new Label("Calendar");
    private final Label monthLabel = new Label();
    private final GridPane calendarGrid = new GridPane();
    private final ListView<Task> unscheduledList = new ListView<>();

    private final PauseTransition rebuildDelay = new PauseTransition(Duration.millis(120));
    private final Set<Task> hooked = new HashSet<>();

    private Project boundProject;

    private final BooleanBinding canCreate;
    private final BooleanBinding canEditMeta;

    public CalendarPage(InMemoryStore store, AppState appState) {
        this.store = store;
        this.appState = appState;

        this.canCreate = Bindings.createBooleanBinding(
                () -> policy.canCreateTasks(appState),
                appState.sessionProperty(),
                appState.selectedProjectProperty(),
                appState.currentProjectRoleProperty()
        );

        this.canEditMeta = Bindings.createBooleanBinding(
                () -> policy.canEditTaskMeta(appState),
                appState.sessionProperty(),
                appState.selectedProjectProperty(),
                appState.currentProjectRoleProperty()
        );

        setPadding(new Insets(16));
        setSpacing(12);

        header.getStyleClass().add("page-title");

        ProjectPicker projectPicker = new ProjectPicker(store, appState);
        projectPicker.setPrefWidth(320);

        Button prev = new Button("<");
        Button next = new Button(">");
        Button today = new Button("Today");
        prev.getStyleClass().add("ghost");
        next.getStyleClass().add("ghost");
        today.getStyleClass().add("subtle");

        monthLabel.getStyleClass().add("section-title");
        monthLabel.setText(MONTH_FMT.format(month.get()));

        prev.setOnAction(e -> month.set(month.get().minusMonths(1)));
        next.setOnAction(e -> month.set(month.get().plusMonths(1)));
        today.setOnAction(e -> month.set(YearMonth.now()));

        Button recurring = new Button("New recurring");
        recurring.getStyleClass().add("primary");
        recurring.visibleProperty().bind(canCreate);
        recurring.managedProperty().bind(recurring.visibleProperty());
        recurring.setOnAction(e -> showRecurringDialog());

        HBox monthNav = new HBox(8, prev, monthLabel, next, today);
        monthNav.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        HBox toolbar = new HBox(12,
                new Label("Project:"), projectPicker,
                spacer,
                monthNav,
                recurring
        );
        toolbar.setAlignment(Pos.CENTER_LEFT);

        calendarGrid.getStyleClass().add("calendar-grid");
        configureGridConstraints();
        calendarGrid.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox calendarCard = new VBox(8, buildDowRow(), calendarGrid);
        calendarCard.getStyleClass().add("calendar-card");
        VBox.setVgrow(calendarGrid, javafx.scene.layout.Priority.ALWAYS);
        calendarCard.setMaxHeight(Double.MAX_VALUE);

        Label unscheduledTitle = new Label("Unscheduled");
        unscheduledTitle.getStyleClass().add("section-title");
        unscheduledList.getStyleClass().add("calendar-unscheduled-list");
        unscheduledList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Task item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                setGraphic(buildTaskChip(item));
            }
        });
        VBox unscheduledCard = new VBox(10, unscheduledTitle, unscheduledList);
        unscheduledCard.getStyleClass().add("calendar-unscheduled");
        VBox.setVgrow(unscheduledList, javafx.scene.layout.Priority.ALWAYS);

        HBox body = new HBox(14, calendarCard, unscheduledCard);
        HBox.setHgrow(calendarCard, javafx.scene.layout.Priority.ALWAYS);
        unscheduledCard.setPrefWidth(260);
        VBox.setVgrow(body, javafx.scene.layout.Priority.ALWAYS);

        getChildren().addAll(header, toolbar, body);

        rebuildDelay.setOnFinished(e -> rebuild());

        month.addListener((obs, ov, nv) -> {
            monthLabel.setText(MONTH_FMT.format(nv));
            requestRebuild();
        });

        appState.selectedProjectProperty().addListener((obs, ov, nv) -> refresh(nv));

        refresh(appState.getSelectedProject());
    }

    private HBox buildDowRow() {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        for (DayOfWeek dow : DayOfWeek.values()) {
            Label lbl = new Label(dow.name().substring(0, 3));
            lbl.getStyleClass().add("calendar-dow");
            HBox.setHgrow(lbl, javafx.scene.layout.Priority.ALWAYS);
            lbl.setMaxWidth(Double.MAX_VALUE);
            lbl.setAlignment(Pos.CENTER);
            row.getChildren().add(lbl);
        }
        return row;
    }

    private void configureGridConstraints() {
        if (!calendarGrid.getColumnConstraints().isEmpty()) return;
        for (int i = 0; i < 7; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setHgrow(javafx.scene.layout.Priority.ALWAYS);
            col.setFillWidth(true);
            col.setPercentWidth(100.0 / 7.0);
            calendarGrid.getColumnConstraints().add(col);
        }
        for (int i = 0; i < 6; i++) {
            RowConstraints row = new RowConstraints();
            row.setVgrow(javafx.scene.layout.Priority.ALWAYS);
            row.setFillHeight(true);
            row.setPercentHeight(100.0 / 6.0);
            calendarGrid.getRowConstraints().add(row);
        }
    }

    private void refresh(Project p) {
        boundProject = p;
        hooked.clear();

        if (p != null) {
            p.getTasks().addListener((ListChangeListener<Task>) c -> {
                while (c.next()) {
                    if (c.wasAdded()) for (Task t : c.getAddedSubList()) hookTask(t);
                }
                requestRebuild();
            });
            for (Task t : p.getTasks()) hookTask(t);
        }
        rebuild();
    }

    private void hookTask(Task t) {
        if (t == null || !hooked.add(t)) return;
        t.titleProperty().addListener((obs, ov, nv) -> requestRebuild());
        t.statusProperty().addListener((obs, ov, nv) -> requestRebuild());
        t.dueDateProperty().addListener((obs, ov, nv) -> requestRebuild());
        t.assigneeProperty().addListener((obs, ov, nv) -> requestRebuild());
    }

    private void rebuild() {
        calendarGrid.getChildren().clear();
        if (boundProject == null) {
            unscheduledList.setItems(FXCollections.observableArrayList());
            return;
        }

        Map<LocalDate, List<Task>> byDate = new HashMap<>();
        List<Task> unscheduled = new ArrayList<>();

        for (Task t : visibleTasks(boundProject)) {
            if (t == null) continue;
            LocalDate due = t.getDueDate();
            if (due == null) {
                unscheduled.add(t);
            } else {
                byDate.computeIfAbsent(due, k -> new ArrayList<>()).add(t);
            }
        }

        unscheduledList.setItems(FXCollections.observableArrayList(unscheduled));

        YearMonth ym = month.get();
        LocalDate first = ym.atDay(1);
        int shift = first.getDayOfWeek().getValue() - 1; // Monday=1
        LocalDate start = first.minusDays(shift);

        for (int i = 0; i < 42; i++) {
            LocalDate day = start.plusDays(i);
            int row = i / 7;
            int col = i % 7;
            VBox cell = buildDayCell(day, byDate.getOrDefault(day, List.of()), ym);
            calendarGrid.add(cell, col, row);
        }
    }

    private VBox buildDayCell(LocalDate day, List<Task> tasks, YearMonth ym) {
        Label dayLabel = new Label(Integer.toString(day.getDayOfMonth()));
        dayLabel.getStyleClass().add("calendar-day-label");

        VBox tasksBox = new VBox(4);
        for (Task t : tasks) {
            tasksBox.getChildren().add(buildTaskChip(t));
        }

        VBox cell = new VBox(6, dayLabel, tasksBox);
        cell.getStyleClass().add("calendar-day-cell");
        if (!day.getMonth().equals(ym.getMonth())) cell.getStyleClass().add("calendar-day-outside");
        if (day.equals(LocalDate.now())) cell.getStyleClass().add("calendar-day-today");
        cell.setMaxWidth(Double.MAX_VALUE);
        cell.setMaxHeight(Double.MAX_VALUE);
        GridPane.setHgrow(cell, javafx.scene.layout.Priority.ALWAYS);
        GridPane.setVgrow(cell, javafx.scene.layout.Priority.ALWAYS);

        cell.setOnDragOver(e -> {
            if (!canEditMeta.get()) return;
            Dragboard db = e.getDragboard();
            if (db.hasString()) {
                e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
        });

        cell.setOnDragDropped(e -> {
            if (!canEditMeta.get()) return;
            Dragboard db = e.getDragboard();
            boolean ok = false;
            if (db.hasString() && boundProject != null) {
                Task t = findTaskById(boundProject, db.getString());
                if (t != null) {
                    t.setDueDate(day);
                    ok = true;
                }
            }
            e.setDropCompleted(ok);
            e.consume();
        });

        return cell;
    }

    private Node buildTaskChip(Task task) {
        Label chip = new Label(task.getTitle() == null ? "Untitled task" : task.getTitle());
        chip.getStyleClass().add("calendar-task-chip");
        if (task.getStatus() == TaskStatus.DONE) chip.getStyleClass().add("calendar-task-chip-done");

        chip.setOnMouseClicked(e -> {
            appState.setSelectedTask(task);
            appState.setCurrentPage(com.projectpilot.core.PageId.TASKS);
        });

        chip.setOnDragDetected(e -> {
            if (!canEditMeta.get()) return;
            Dragboard db = chip.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(task.getId());
            db.setContent(content);
            e.consume();
        });

        return chip;
    }

    private List<Task> visibleTasks(Project p) {
        if (policy.canSeeAllProjectTasks(appState)) return new ArrayList<>(p.getTasks());
        List<Task> out = new ArrayList<>();
        for (Task t : p.getTasks()) {
            if (policy.isAssignedToMe(appState, t)) out.add(t);
        }
        return out;
    }

    private Task findTaskById(Project p, String id) {
        if (p == null || id == null) return null;
        for (Task t : p.getTasks()) {
            if (t != null && id.equals(t.getId())) return t;
        }
        return null;
    }

    private void showRecurringDialog() {
        Project p = boundProject;
        if (p == null) {
            alertInfo("Select a project first", "Choose a project to create recurring tasks.");
            return;
        }

        Dialog<RecurringSpec> dialog = new Dialog<>();
        com.projectpilot.ui.dialogs.DialogTheme.apply(dialog);
        dialog.setTitle("Create recurring tasks");

        ButtonType createBtn = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createBtn, ButtonType.CANCEL);

        TextField titleField = new TextField();
        DatePicker startPicker = new DatePicker(LocalDate.now());
        ComboBox<RepeatUnit> repeatBox = new ComboBox<>();
        repeatBox.getItems().setAll(RepeatUnit.values());
        repeatBox.setValue(RepeatUnit.WEEKLY);

        Spinner<Integer> occurrences = new Spinner<>(2, 52, 6);
        occurrences.setEditable(true);

        ComboBox<Member> assigneeBox = new ComboBox<>();
        assigneeBox.getItems().add(null);
        assigneeBox.getItems().addAll(p.getMembers());
        assigneeBox.setValue(null);
        assigneeBox.setPromptText("Unassigned");
        assigneeBox.setCellFactory(cb -> new ListCell<>() {
            @Override protected void updateItem(Member item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Unassigned" : item.getName());
            }
        });
        assigneeBox.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Member item, boolean empty) {
                super.updateItem(item, empty);
                setText(item == null ? "Unassigned" : item.getName());
            }
        });

        ComboBox<Priority> priorityBox = new ComboBox<>();
        priorityBox.getItems().setAll(Priority.values());
        priorityBox.setValue(Priority.MEDIUM);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.add(new Label("Title"), 0, 0);
        form.add(titleField, 1, 0);
        form.add(new Label("Start date"), 0, 1);
        form.add(startPicker, 1, 1);
        form.add(new Label("Repeat"), 0, 2);
        form.add(repeatBox, 1, 2);
        form.add(new Label("Occurrences"), 0, 3);
        form.add(occurrences, 1, 3);
        form.add(new Label("Assignee"), 0, 4);
        form.add(assigneeBox, 1, 4);
        form.add(new Label("Priority"), 0, 5);
        form.add(priorityBox, 1, 5);

        dialog.getDialogPane().setContent(form);

        Node okNode = dialog.getDialogPane().lookupButton(createBtn);
        okNode.disableProperty().bind(
                Bindings.createBooleanBinding(
                        () -> titleField.getText().trim().isEmpty() || startPicker.getValue() == null,
                        titleField.textProperty(),
                        startPicker.valueProperty()
                )
        );

        dialog.setResultConverter(btn -> {
            if (btn != createBtn) return null;
            return new RecurringSpec(
                    titleField.getText().trim(),
                    startPicker.getValue(),
                    repeatBox.getValue(),
                    occurrences.getValue(),
                    assigneeBox.getValue(),
                    priorityBox.getValue()
            );
        });

        dialog.showAndWait().ifPresent(spec -> createRecurringTasks(p, spec));
    }

    private void createRecurringTasks(Project p, RecurringSpec spec) {
        if (p == null || spec == null) return;
        LocalDate date = spec.start();
        for (int i = 0; i < spec.occurrences(); i++) {
            String title = spec.title() + " (" + date + ")";
            Task task = new Task(title);
            task.setDueDate(date);
            task.setStatus(TaskStatus.TODO);
            if (spec.priority() != null) task.setPriority(spec.priority());
            if (spec.assignee() != null) task.setAssignee(spec.assignee());
            store.addTask(p, task);
            date = switch (spec.unit()) {
                case DAILY -> date.plusDays(1);
                case WEEKLY -> date.plusWeeks(1);
                case MONTHLY -> date.plusMonths(1);
            };
        }
        requestRebuild();
    }

    private void requestRebuild() {
        rebuildDelay.playFromStart();
    }

    private void alertInfo(String header, String text) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("ProjectPilot");
        a.setHeaderText(header);
        a.setContentText(text);
        a.showAndWait();
    }

    private enum RepeatUnit { DAILY, WEEKLY, MONTHLY }

    private record RecurringSpec(
            String title,
            LocalDate start,
            RepeatUnit unit,
            int occurrences,
            Member assignee,
            Priority priority
    ) {}
}
