package com.projectpilot.data;

import com.projectpilot.model.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalDate;

public class InMemoryStore {

    private final ObservableList<Project> projects = FXCollections.observableArrayList();
    private final ObservableList<Project> historyProjects = FXCollections.observableArrayList();
    private final ObservableList<ActivityItem> activity = FXCollections.observableArrayList();

    public ObservableList<Project> getProjects() { return projects; }
    public ObservableList<Project> getHistoryProjects() { return historyProjects; }
    public ObservableList<ActivityItem> getActivity() { return activity; }

    // Central place to persist after any change
    private void autosave() {
        PersistenceService.safeSave(this);
    }

    private void log(Project p, String msg) {
        String pn = (p == null) ? "-" : safe(p.getName());
        activity.add(0, new ActivityItem(pn, msg)); // newest first
        if (activity.size() > 50) activity.remove(activity.size() - 1);
    }

    // ✅ overload so SampleData can keep using createProject("name")
    public Project createProject(String name) {
        return createProject(new Project(name));
    }

    public Project createProject(Project project) {
        if (project == null) return null;

        projects.add(project);
        log(project, "Project created");
        autosave();
        return project;
    }

    public void deleteProject(Project project) {
        if (project == null) return;

        projects.remove(project);
        historyProjects.remove(project); // also remove if it exists in history

        log(project, "Project deleted");
        autosave();
    }

    public Task addTask(Project project, Task task) {
        if (project == null || task == null) return task;

        project.getTasks().add(task);
        log(project, "Task added: " + safe(task.getTitle()));
        autosave();
        return task;
    }

    public Member addMember(Project project, Member member) {
        if (project == null || member == null) return member;

        project.getMembers().add(member);
        log(project, "Member added: " + safe(member.getName()));
        autosave();
        return member;
    }

    public void removeMember(Project project, Member member) {
        if (project == null || member == null) return;

        // Unassign tasks safely
        for (Task t : project.getTasks()) {
            if (java.util.Objects.equals(t.getAssignee(), member)) {
                t.setAssignee(null);
            }
        }

        project.getMembers().remove(member);
        log(project, "Member removed: " + safe(member.getName()));
        autosave();
    }

    public Phase addPhase(Project project, Phase phase) {
        if (project == null || phase == null) return phase;

        project.getPhases().add(phase);
        log(project, "Phase added: " + safe(phase.toString()));
        autosave();
        return phase;
    }

    public Milestone addMilestone(Project project, Milestone milestone) {
        if (project == null || milestone == null) return milestone;

        project.getMilestones().add(milestone);
        String name = safe(milestone.nameProperty().get());
        log(project, "Milestone added: " + name);
        autosave();
        return milestone;
    }

    public void markProjectDone(Project project) {
        if (project == null) return;

        projects.remove(project);

        project.setStatus(Project.ProjectStatus.DONE);
        project.setCompletedDate(LocalDate.now());

        if (!historyProjects.contains(project)) historyProjects.add(project);

        log(project, "Project marked DONE");
        autosave();
    }

    public void restoreProject(Project project) {
        if (project == null) return;

        historyProjects.remove(project);

        project.setStatus(Project.ProjectStatus.ACTIVE);
        project.setCompletedDate(null);

        if (!projects.contains(project)) projects.add(project);

        log(project, "Project restored to ACTIVE");
        autosave();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
