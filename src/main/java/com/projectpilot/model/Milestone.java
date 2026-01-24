package com.projectpilot.model;

import javafx.beans.property.*;
import java.time.LocalDate;
import java.util.UUID;

public class Milestone {
    private final String id = UUID.randomUUID().toString();
    private final StringProperty name = new SimpleStringProperty();
    private final ObjectProperty<LocalDate> dueDate = new SimpleObjectProperty<>(LocalDate.now().plusWeeks(2));
    private final BooleanProperty completed = new SimpleBooleanProperty(false);

    public Milestone(String name) { this.name.set(name); }

    public String getId() { return id; }
    public StringProperty nameProperty() { return name; }
    public ObjectProperty<LocalDate> dueDateProperty() { return dueDate; }
    public BooleanProperty completedProperty() { return completed; }
}
