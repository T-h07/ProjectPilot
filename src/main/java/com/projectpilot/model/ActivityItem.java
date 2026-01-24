package com.projectpilot.model;

import javafx.beans.property.*;
import java.time.LocalDateTime;

public class ActivityItem {
    private final ObjectProperty<LocalDateTime> time = new SimpleObjectProperty<>(LocalDateTime.now());
    private final StringProperty projectName = new SimpleStringProperty();
    private final StringProperty message = new SimpleStringProperty();

    public ActivityItem(String projectName, String message) {
        this.projectName.set(projectName);
        this.message.set(message);
    }

    public ObjectProperty<LocalDateTime> timeProperty() { return time; }
    public LocalDateTime getTime() { return time.get(); }

    public StringProperty projectNameProperty() { return projectName; }
    public String getProjectName() { return projectName.get(); }

    public StringProperty messageProperty() { return message; }
    public String getMessage() { return message.get(); }
}
