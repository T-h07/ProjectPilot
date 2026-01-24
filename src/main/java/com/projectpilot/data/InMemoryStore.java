package com.projectpilot.data;

import com.projectpilot.model.Member;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class InMemoryStore {
    private final ObservableList<Project> projects = FXCollections.observableArrayList();

    public ObservableList<Project> getProjects() { return projects; }

    // ✅ overload so SampleData can keep using createProject("name")
    public Project createProject(String name) {
        return createProject(new Project(name));
    }

    public Project createProject(Project project) {
        projects.add(project);
        return project;
    }

    public void deleteProject(Project project) {
        projects.remove(project);
    }

    public Task addTask(Project project, Task task) {
        project.getTasks().add(task);
        return task;
    }

    public Member addMember(Project project, Member member) {
        project.getMembers().add(member);
        return member;
    }
}
