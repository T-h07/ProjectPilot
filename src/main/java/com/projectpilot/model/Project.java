package com.projectpilot.model;

import com.projectpilot.model.enums.ProjectHealth;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalDate;
import java.util.UUID;

public class Project {
    private final String id = UUID.randomUUID().toString();

    private final StringProperty name = new SimpleStringProperty();
    private final StringProperty description = new SimpleStringProperty("");
    private final ObjectProperty<LocalDate> startDate = new SimpleObjectProperty<>(LocalDate.now());
    private final ObjectProperty<LocalDate> endDate = new SimpleObjectProperty<>(LocalDate.now().plusWeeks(4));
    private final ObjectProperty<ProjectHealth> health = new SimpleObjectProperty<>(ProjectHealth.ON_TRACK);

    private final ObservableList<Phase> phases = FXCollections.observableArrayList();
    private final ObservableList<Task> tasks = FXCollections.observableArrayList();
    private final ObservableList<Member> members = FXCollections.observableArrayList();
    private final ObservableList<Milestone> milestones = FXCollections.observableArrayList();

    public Project(String name) { this.name.set(name); }

    public String getId() { return id; }

    public StringProperty nameProperty() { return name; }
    public String getName() { return name.get(); }
    public void setName(String v) { name.set(v); }

    public StringProperty descriptionProperty() { return description; }
    public String getDescription() { return description.get(); }
    public void setDescription(String v) { description.set(v); }

    public ObjectProperty<LocalDate> startDateProperty() { return startDate; }
    public LocalDate getStartDate() { return startDate.get(); }
    public void setStartDate(LocalDate v) { startDate.set(v); }

    public ObjectProperty<LocalDate> endDateProperty() { return endDate; }
    public LocalDate getEndDate() { return endDate.get(); }
    public void setEndDate(LocalDate v) { endDate.set(v); }

    public ObjectProperty<ProjectHealth> healthProperty() { return health; }
    public ProjectHealth getHealth() { return health.get(); }
    public void setHealth(ProjectHealth v) { health.set(v); }

    public ObservableList<Phase> getPhases() { return phases; }
    public ObservableList<Task> getTasks() { return tasks; }
    public ObservableList<Member> getMembers() { return members; }
    public ObservableList<Milestone> getMilestones() { return milestones; }

    @Override public String toString() { return getName(); }
}
