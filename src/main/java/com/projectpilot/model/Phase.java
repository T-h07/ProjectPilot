package com.projectpilot.model;

import javafx.beans.property.*;
import java.time.LocalDate;
import java.util.UUID;

public class Phase {
    private final String id;
    private final StringProperty name = new SimpleStringProperty();
    private final ObjectProperty<LocalDate> start = new SimpleObjectProperty<>(LocalDate.now());
    private final ObjectProperty<LocalDate> end = new SimpleObjectProperty<>(LocalDate.now().plusWeeks(1));

    public Phase(String name) {
        this(UUID.randomUUID().toString(), name);
    }

    public Phase(String id, String name) {
        this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
        this.name.set(name);
    }

    public String getId() { return id; }

    public StringProperty nameProperty() { return name; }
    public String getName() { return name.get(); }
    public void setName(String v) { name.set(v); }

    public ObjectProperty<LocalDate> startProperty() { return start; }
    public ObjectProperty<LocalDate> endProperty() { return end; }
}
