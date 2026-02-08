package com.projectpilot.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.UUID;

public class ChecklistItem {
    private final String id;
    private final StringProperty text = new SimpleStringProperty("");
    private final BooleanProperty done = new SimpleBooleanProperty(false);

    public ChecklistItem(String text) {
        this(UUID.randomUUID().toString(), text, false);
    }

    public ChecklistItem(String id, String text, boolean done) {
        this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
        this.text.set(text == null ? "" : text);
        this.done.set(done);
    }

    public String getId() { return id; }

    public StringProperty textProperty() { return text; }
    public String getText() { return text.get(); }
    public void setText(String v) { text.set(v); }

    public BooleanProperty doneProperty() { return done; }
    public boolean isDone() { return done.get(); }
    public void setDone(boolean v) { done.set(v); }

    public ChecklistItem copyWithNewId() {
        return new ChecklistItem(null, getText(), isDone());
    }
}
