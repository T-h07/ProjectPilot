package com.projectpilot.model;

import com.projectpilot.model.enums.Priority;
import com.projectpilot.model.enums.TaskStatus;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Task {
    private final String id;

    private final StringProperty title = new SimpleStringProperty();
    private final StringProperty description = new SimpleStringProperty("");
    private final ObjectProperty<TaskStatus> status = new SimpleObjectProperty<>(TaskStatus.TODO);
    private final ObjectProperty<Priority> priority = new SimpleObjectProperty<>(Priority.MEDIUM);
    private final ObjectProperty<LocalDate> dueDate = new SimpleObjectProperty<>(LocalDate.now().plusDays(7));
    private final ObjectProperty<Member> assignee = new SimpleObjectProperty<>();
    private final ObjectProperty<Phase> phase = new SimpleObjectProperty<>();
    private final ObservableList<ChecklistItem> checklist = FXCollections.observableArrayList();
    private final IntegerProperty checklistVersion = new SimpleIntegerProperty(0);
    private final Map<ChecklistItem, javafx.beans.value.ChangeListener<Object>> checklistItemListeners = new HashMap<>();

    public Task(String title) {
        this(UUID.randomUUID().toString(), title);
    }

    public Task(String id, String title) {
        this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
        this.title.set(title);
        initChecklistTracking();
    }

    public String getId() { return id; }

    public ObjectProperty<Phase> phaseProperty() { return phase; }
    public Phase getPhase() { return phase.get(); }
    public void setPhase(Phase v) { phase.set(v); }

    public StringProperty titleProperty() { return title; }
    public String getTitle() { return title.get(); }
    public void setTitle(String v) { title.set(v); }

    public StringProperty descriptionProperty() { return description; }
    public String getDescription() { return description.get(); }
    public void setDescription(String v) { description.set(v); }

    public ObjectProperty<TaskStatus> statusProperty() { return status; }
    public TaskStatus getStatus() { return status.get(); }
    public void setStatus(TaskStatus v) { status.set(v); }

    public ObjectProperty<Priority> priorityProperty() { return priority; }
    public Priority getPriority() { return priority.get(); }
    public void setPriority(Priority v) { priority.set(v); }

    public ObjectProperty<LocalDate> dueDateProperty() { return dueDate; }
    public LocalDate getDueDate() { return dueDate.get(); }
    public void setDueDate(LocalDate v) { dueDate.set(v); }

    public ObjectProperty<Member> assigneeProperty() { return assignee; }
    public Member getAssignee() { return assignee.get(); }
    public void setAssignee(Member v) { assignee.set(v); }

    public ObservableList<ChecklistItem> getChecklist() { return checklist; }
    public void setChecklist(java.util.List<ChecklistItem> items) {
        checklist.setAll(items == null ? java.util.List.of() : items);
    }

    public IntegerProperty checklistVersionProperty() { return checklistVersion; }

    @Override public String toString() { return getTitle(); }

    private void initChecklistTracking() {
        checklist.addListener((ListChangeListener<ChecklistItem>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (ChecklistItem item : change.getAddedSubList()) attachChecklistItem(item);
                }
                if (change.wasRemoved()) {
                    for (ChecklistItem item : change.getRemoved()) detachChecklistItem(item);
                }
            }
            bumpChecklistVersion();
        });
    }

    private void attachChecklistItem(ChecklistItem item) {
        if (item == null || checklistItemListeners.containsKey(item)) return;
        javafx.beans.value.ChangeListener<Object> dirty = (obs, o, n) -> bumpChecklistVersion();
        item.textProperty().addListener(dirty);
        item.doneProperty().addListener(dirty);
        checklistItemListeners.put(item, dirty);
    }

    private void detachChecklistItem(ChecklistItem item) {
        if (item == null) return;
        javafx.beans.value.ChangeListener<Object> dirty = checklistItemListeners.remove(item);
        if (dirty == null) return;
        item.textProperty().removeListener(dirty);
        item.doneProperty().removeListener(dirty);
    }

    private void bumpChecklistVersion() {
        checklistVersion.set(checklistVersion.get() + 1);
    }
}
