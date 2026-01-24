package com.projectpilot.model;

import com.projectpilot.model.enums.ProjectRole;
import javafx.beans.property.*;

import java.util.UUID;

public class Member {
    private final String id = UUID.randomUUID().toString();
    private final StringProperty name = new SimpleStringProperty();
    private final ObjectProperty<ProjectRole> role = new SimpleObjectProperty<>(ProjectRole.MEMBER);

    public Member(String name, ProjectRole role) {
        this.name.set(name);
        this.role.set(role);
    }

    public String getId() { return id; }

    public StringProperty nameProperty() { return name; }
    public String getName() { return name.get(); }
    public void setName(String v) { name.set(v); }

    public ObjectProperty<ProjectRole> roleProperty() { return role; }
    public ProjectRole getRole() { return role.get(); }
    public void setRole(ProjectRole v) { role.set(v); }

    @Override public String toString() { return getName(); }
}
