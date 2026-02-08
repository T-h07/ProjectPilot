package com.projectpilot.model;

import com.projectpilot.model.enums.ResourceType;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.LocalDateTime;
import java.util.UUID;

public class ResourceItem {
    private final String id;
    private final String projectId;

    private final StringProperty taskId = new SimpleStringProperty();
    private final ObjectProperty<ResourceType> type = new SimpleObjectProperty<>(ResourceType.LINK);
    private final StringProperty title = new SimpleStringProperty("");
    private final StringProperty target = new SimpleStringProperty("");
    private final StringProperty notes = new SimpleStringProperty("");
    private final StringProperty addedBy = new SimpleStringProperty("");
    private final ObjectProperty<LocalDateTime> createdAt = new SimpleObjectProperty<>(LocalDateTime.now());
    private final ObjectProperty<LocalDateTime> updatedAt = new SimpleObjectProperty<>(LocalDateTime.now());

    public ResourceItem(String projectId) {
        this(UUID.randomUUID().toString(), projectId);
    }

    public ResourceItem(String id, String projectId) {
        this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
        this.projectId = projectId == null ? "" : projectId.trim();
    }

    public String getId() { return id; }
    public String getProjectId() { return projectId; }

    public StringProperty taskIdProperty() { return taskId; }
    public String getTaskId() { return taskId.get(); }
    public void setTaskId(String v) { taskId.set(v); }

    public ObjectProperty<ResourceType> typeProperty() { return type; }
    public ResourceType getType() { return type.get(); }
    public void setType(ResourceType v) { type.set(v); }

    public StringProperty titleProperty() { return title; }
    public String getTitle() { return title.get(); }
    public void setTitle(String v) { title.set(v); }

    public StringProperty targetProperty() { return target; }
    public String getTarget() { return target.get(); }
    public void setTarget(String v) { target.set(v); }

    public StringProperty notesProperty() { return notes; }
    public String getNotes() { return notes.get(); }
    public void setNotes(String v) { notes.set(v); }

    public StringProperty addedByProperty() { return addedBy; }
    public String getAddedBy() { return addedBy.get(); }
    public void setAddedBy(String v) { addedBy.set(v); }

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
