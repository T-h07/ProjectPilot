package com.projectpilot.model;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.LocalDateTime;
import java.util.UUID;

public class PersonalNote {
    private final String id;
    private final String projectId;

    private final StringProperty taskId = new SimpleStringProperty();
    private final StringProperty ownerId = new SimpleStringProperty();
    private final StringProperty title = new SimpleStringProperty("");
    private final StringProperty body = new SimpleStringProperty("");
    private final ObjectProperty<LocalDateTime> createdAt = new SimpleObjectProperty<>(LocalDateTime.now());
    private final ObjectProperty<LocalDateTime> updatedAt = new SimpleObjectProperty<>(LocalDateTime.now());

    public PersonalNote(String projectId, String ownerId) {
        this(UUID.randomUUID().toString(), projectId, ownerId);
    }

    public PersonalNote(String id, String projectId, String ownerId) {
        this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
        this.projectId = projectId == null ? "" : projectId.trim();
        this.ownerId.set(ownerId == null ? "" : ownerId.trim());
    }

    public String getId() { return id; }
    public String getProjectId() { return projectId; }

    public StringProperty taskIdProperty() { return taskId; }
    public String getTaskId() { return taskId.get(); }
    public void setTaskId(String v) { taskId.set(v); }

    public StringProperty ownerIdProperty() { return ownerId; }
    public String getOwnerId() { return ownerId.get(); }
    public void setOwnerId(String v) { ownerId.set(v); }

    public StringProperty titleProperty() { return title; }
    public String getTitle() { return title.get(); }
    public void setTitle(String v) { title.set(v); }

    public StringProperty bodyProperty() { return body; }
    public String getBody() { return body.get(); }
    public void setBody(String v) { body.set(v); }

    public ObjectProperty<LocalDateTime> createdAtProperty() { return createdAt; }
    public LocalDateTime getCreatedAt() { return createdAt.get(); }
    public void setCreatedAt(LocalDateTime v) { createdAt.set(v); }

    public ObjectProperty<LocalDateTime> updatedAtProperty() { return updatedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt.get(); }
    public void setUpdatedAt(LocalDateTime v) { updatedAt.set(v); }

    public void touchUpdatedAt() {
        updatedAt.set(LocalDateTime.now());
    }
}
