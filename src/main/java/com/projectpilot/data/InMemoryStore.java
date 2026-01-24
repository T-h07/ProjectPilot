package com.projectpilot.data;

import com.projectpilot.model.Project;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class InMemoryStore {
    private final ObservableList<Project> projects = FXCollections.observableArrayList();

    public ObservableList<Project> getProjects() { return projects; }

    public Project createProject(String name) {
        Project p = new Project(name);
        projects.add(p);
        return p;
    }
}
