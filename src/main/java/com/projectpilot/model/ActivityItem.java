package com.projectpilot.model;

import javafx.beans.property.MapProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleMapProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import java.time.LocalDateTime;

public class ActivityItem {
    private final ObjectProperty<LocalDateTime> time = new SimpleObjectProperty<>(LocalDateTime.now());
    private final StringProperty projectId = new SimpleStringProperty();
    private final StringProperty projectName = new SimpleStringProperty();
    private final StringProperty actor = new SimpleStringProperty();
    private final StringProperty entityType = new SimpleStringProperty();
    private final StringProperty entityId = new SimpleStringProperty();
    private final StringProperty action = new SimpleStringProperty();
    private final StringProperty message = new SimpleStringProperty();
    private final MapProperty<String, Integer> reactions =
            new SimpleMapProperty<>(FXCollections.observableHashMap());

    public ActivityItem(String projectName, String message) {
        this(null, projectName, null, null, null, null, message);
    }

    public ActivityItem(String projectId, String projectName, String actor,
                        String entityType, String entityId, String action, String message) {
        this.projectId.set(projectId);
        this.projectName.set(projectName);
        this.actor.set(actor);
        this.entityType.set(entityType);
        this.entityId.set(entityId);
        this.action.set(action);
        this.message.set(message);
    }

    public ObjectProperty<LocalDateTime> timeProperty() { return time; }
    public LocalDateTime getTime() { return time.get(); }

    public StringProperty projectIdProperty() { return projectId; }
    public String getProjectId() { return projectId.get(); }

    public StringProperty projectNameProperty() { return projectName; }
    public String getProjectName() { return projectName.get(); }

    public StringProperty actorProperty() { return actor; }
    public String getActor() { return actor.get(); }

    public StringProperty entityTypeProperty() { return entityType; }
    public String getEntityType() { return entityType.get(); }

    public StringProperty entityIdProperty() { return entityId; }
    public String getEntityId() { return entityId.get(); }

    public StringProperty actionProperty() { return action; }
    public String getAction() { return action.get(); }

    public StringProperty messageProperty() { return message; }
    public String getMessage() { return message.get(); }

    public MapProperty<String, Integer> reactionsProperty() { return reactions; }
    public ObservableMap<String, Integer> getReactions() { return reactions.get(); }

    public void addReaction(String key) {
        if (key == null || key.isBlank()) return;
        int count = getReactions().getOrDefault(key, 0);
        getReactions().put(key, count + 1);
    }
}
