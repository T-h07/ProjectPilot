package com.projectpilot.model;

import javafx.beans.property.*;
import java.time.LocalDate;
import java.util.UUID;

public class Phase {
    private final String id = UUID.randomUUID().toString();
    private final StringProperty name = new SimpleStringProperty();
    private final ObjectProperty<LocalDate> start = new SimpleObjectProperty<>(LocalDate.now());
    private final ObjectProperty<LocalDate> end = new SimpleObjectProperty<>(LocalDate.now().plusWeeks(1));

    public Phase(String name) { this.name.set(name); }

    public String getId() { return id; }
    public StringProperty nameProperty() { return name; }
    public String getName() { return name.get(); }
    public void setName(String v) { name.set(v); }

    public ObjectProperty<LocalDate> startProperty() { return start; }
    public ObjectProperty<LocalDate> endProperty() { return end; }
}
