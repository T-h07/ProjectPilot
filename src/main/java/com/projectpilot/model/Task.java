package com.projectpilot.model;

import com.projectpilot.model.enums.Priority;
import com.projectpilot.model.enums.TaskStatus;
import javafx.beans.property.*;

import java.time.LocalDate;
import java.util.UUID;
import com.projectpilot.model.Phase;


public class Task {
    private final String id = UUID.randomUUID().toString();

    private final StringProperty title = new SimpleStringProperty();
    private final StringProperty description = new SimpleStringProperty("");
    private final ObjectProperty<TaskStatus> status = new SimpleObjectProperty<>(TaskStatus.TODO);
    private final ObjectProperty<Priority> priority = new SimpleObjectProperty<>(Priority.MEDIUM);
    private final ObjectProperty<LocalDate> dueDate = new SimpleObjectProperty<>(LocalDate.now().plusDays(7));
    private final ObjectProperty<Member> assignee = new SimpleObjectProperty<>();
    private final ObjectProperty<Phase> phase = new SimpleObjectProperty<>();


    public Task(String title) { this.title.set(title); }

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

    @Override public String toString() { return getTitle(); }
}
