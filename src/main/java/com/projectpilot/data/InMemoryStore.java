package com.projectpilot.data;

import com.projectpilot.model.ActivityItem;
import com.projectpilot.model.Member;
import com.projectpilot.model.Project;
import com.projectpilot.model.Task;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import com.projectpilot.model.Milestone;

public class InMemoryStore {

    private final ObservableList<Project> projects = FXCollections.observableArrayList();
    private final ObservableList<ActivityItem> activity = FXCollections.observableArrayList();

    public ObservableList<Project> getProjects() {
        return projects;
    }

    public ObservableList<ActivityItem> getActivity() {
        return activity;
    }

    public Milestone addMilestone(Project project, Milestone milestone) {
        project.getMilestones().add(milestone);
        log(project, "Milestone added: " + milestone.nameProperty().get());
        return milestone;
    }

    private void log(Project p, String msg) {
        String pn = (p == null) ? "-" : p.getName();
        activity.add(0, new ActivityItem(pn, msg)); // newest first
        if (activity.size() > 50) activity.remove(activity.size() - 1);
    }

    // overload so SampleData can keep using createProject("name")
    public Project createProject(String name) {
        return createProject(new Project(name));
    }

    public Project createProject(Project project) {
        projects.add(project);
        log(project, "Project created");
        return project;
    }

    public void deleteProject(Project project) {
        projects.remove(project);
        log(project, "Project deleted");
    }

    public Task addTask(Project project, Task task) {
        project.getTasks().add(task);
        log(project, "Task created: " + task.getTitle());
        return task;
    }

    public Member addMember(Project project, Member member) {
        project.getMembers().add(member);
        log(project, "Member added: " + member.getName());
        return member;
    }
}
