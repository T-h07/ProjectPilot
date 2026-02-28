package com.projectpilot.data;

import com.projectpilot.model.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalDate;
import java.util.Objects;

public class InMemoryStore {

    private final ObservableList<Project> projects = FXCollections.observableArrayList();
    private final ObservableList<Project> historyProjects = FXCollections.observableArrayList();
    private final ObservableList<ActivityItem> activity = FXCollections.observableArrayList();

    public ObservableList<Project> getProjects() { return projects; }
    public ObservableList<Project> getHistoryProjects() { return historyProjects; }
    public ObservableList<ActivityItem> getActivity() { return activity; }

    /**
     * Hook for persistence. InMemoryStore does nothing.
     * DbStore overrides store methods + attaches listeners to persist to SQLite.
     */
    protected void autosave() {
        // no-op
    }

    protected void log(Project p, String msg) {
        log(p, null, null, null, msg);
    }

    protected void log(Project p, String entityType, String entityId, String action, String msg) {
        String pn = (p == null) ? "-" : safe(p.getName());
        String pid = (p == null) ? null : p.getId();
        ActivityItem item = new ActivityItem(pid, pn, null, entityType, entityId, action, msg);
        activity.add(0, item); // newest first
        if (activity.size() > 50) activity.remove(activity.size() - 1);
    }

    public Project createProject(String name) {
        return createProject(new Project(name));
    }

    public Project createProject(Project project) {
        if (project == null) return null;

        Project existing = findProjectByName(project.getName());
        if (existing != null) return existing;

        projects.add(project);
        log(project, "PROJECT", project.getId(), "CREATE", "Project created");
        autosave();
        return project;
    }

    public void deleteProject(Project project) {
        if (project == null) return;

        projects.remove(project);
        historyProjects.remove(project);

        log(project, "PROJECT", project.getId(), "DELETE", "Project deleted");
        autosave();
    }

    public Task addTask(Project project, Task task) {
        if (project == null || task == null) return task;

        Task existing = findTaskByTitle(project, task.getTitle());
        if (existing != null) return existing;

        project.getTasks().add(task);
        log(project, "TASK", task.getId(), "ADD", "Task added: " + safe(task.getTitle()));
        autosave();
        return task;
    }

    public Member addMember(Project project, Member member) {
        if (project == null || member == null) return member;

        Member existing = findMemberById(project, member.getId());
        if (existing != null) return existing;

        project.getMembers().add(member);
        log(project, "MEMBER", member.getId(), "ADD", "Member added: " + safe(member.getName()));
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
        log(project, "MEMBER", member.getId(), "REMOVE", "Member removed: " + safe(member.getName()));
        autosave();
    }

    /**
     * Removes all in-memory project data tied to a member id.
     * Useful after admin-side hard-delete so the running app stays in sync.
     */
    public void purgeMemberData(String memberId) {
        String id = memberId == null ? "" : memberId.trim();
        if (id.isBlank()) return;

        java.util.List<Project> allProjects = new java.util.ArrayList<>();
        allProjects.addAll(projects);
        allProjects.addAll(historyProjects);

        for (Project project : allProjects) {
            if (project == null) continue;

            java.util.List<Member> membersToRemove = new java.util.ArrayList<>();
            for (Member member : new java.util.ArrayList<>(project.getMembers())) {
                if (member != null && id.equals(member.getId())) {
                    membersToRemove.add(member);
                }
            }
            for (Member member : membersToRemove) {
                removeMember(project, member);
            }

            for (Task task : project.getTasks()) {
                if (task == null) continue;
                Member assignee = task.getAssignee();
                if (assignee != null && id.equals(assignee.getId())) {
                    task.setAssignee(null);
                }
            }

            java.util.List<PersonalNote> notesToRemove = new java.util.ArrayList<>();
            for (PersonalNote note : new java.util.ArrayList<>(project.getNotes())) {
                if (note != null && id.equals(note.getOwnerId())) {
                    notesToRemove.add(note);
                }
            }
            for (PersonalNote note : notesToRemove) {
                removeNote(project, note);
            }
        }
    }

    public Phase addPhase(Project project, Phase phase) {
        if (project == null || phase == null) return phase;

        Phase existing = findPhaseByName(project, phase.getName());
        if (existing != null) return existing;

        project.getPhases().add(phase);
        log(project, "PHASE", phase.getId(), "ADD", "Phase added: " + safe(phase.getName()));
        autosave();
        return phase;
    }

    public Milestone addMilestone(Project project, Milestone milestone) {
        if (project == null || milestone == null) return milestone;

        Milestone existing = findMilestoneByName(project, milestone.nameProperty().get());
        if (existing != null) return existing;

        project.getMilestones().add(milestone);
        String name = safe(milestone.nameProperty().get());
        log(project, "MILESTONE", milestone.getId(), "ADD", "Milestone added: " + name);
        autosave();
        return milestone;
    }

    public ResourceItem addResource(Project project, ResourceItem item) {
        if (project == null || item == null) return item;

        ResourceItem existing = findResourceByKey(project, item);
        if (existing != null) return existing;

        project.getResources().add(item);
        log(project, "RESOURCE", item.getId(), "ADD", "Resource added: " + safe(item.getTitle()));
        autosave();
        return item;
    }

    public void removeResource(Project project, ResourceItem item) {
        if (project == null || item == null) return;

        project.getResources().remove(item);
        log(project, "RESOURCE", item.getId(), "REMOVE", "Resource removed: " + safe(item.getTitle()));
        autosave();
    }

    public PersonalNote addNote(Project project, PersonalNote note) {
        if (project == null || note == null) return note;

        PersonalNote existing = findNoteByKey(project, note);
        if (existing != null) return existing;

        project.getNotes().add(note);
        autosave();
        return note;
    }

    public void removeNote(Project project, PersonalNote note) {
        if (project == null || note == null) return;

        project.getNotes().remove(note);
        autosave();
    }

    public void markProjectDone(Project project) {
        if (project == null) return;

        projects.remove(project);

        project.setStatus(Project.ProjectStatus.DONE);
        project.setCompletedDate(LocalDate.now());

        if (!historyProjects.contains(project)) historyProjects.add(project);

        log(project, "PROJECT", project.getId(), "DONE", "Project marked DONE");
        autosave();
    }

    public void restoreProject(Project project) {
        if (project == null) return;

        historyProjects.remove(project);

        project.setStatus(Project.ProjectStatus.ACTIVE);
        project.setCompletedDate(null);

        if (!projects.contains(project)) projects.add(project);

        log(project, "PROJECT", project.getId(), "RESTORE", "Project restored to ACTIVE");
        autosave();
    }

    protected String safe(String s) {
        return s == null ? "" : s;
    }

    private Project findProjectByName(String name) {
        String n = normalizeName(name);
        if (n.isBlank()) return null;

        for (Project p : projects) {
            if (p != null && normalizeName(p.getName()).equals(n)) return p;
        }
        for (Project p : historyProjects) {
            if (p != null && normalizeName(p.getName()).equals(n)) return p;
        }
        return null;
    }

    private Task findTaskByTitle(Project project, String title) {
        if (project == null) return null;
        String n = normalizeName(title);
        if (n.isBlank()) return null;
        for (Task t : project.getTasks()) {
            if (t != null && normalizeName(t.getTitle()).equals(n)) return t;
        }
        return null;
    }

    private Member findMemberById(Project project, String id) {
        if (project == null || id == null) return null;
        String trimmed = id.trim();
        if (trimmed.isBlank()) return null;
        for (Member m : project.getMembers()) {
            if (m != null && trimmed.equals(m.getId())) return m;
        }
        return null;
    }

    private Phase findPhaseByName(Project project, String name) {
        if (project == null) return null;
        String n = normalizeName(name);
        if (n.isBlank()) return null;
        for (Phase ph : project.getPhases()) {
            if (ph != null && normalizeName(ph.getName()).equals(n)) return ph;
        }
        return null;
    }

    private Milestone findMilestoneByName(Project project, String name) {
        if (project == null) return null;
        String n = normalizeName(name);
        if (n.isBlank()) return null;
        for (Milestone ms : project.getMilestones()) {
            if (ms != null && normalizeName(ms.nameProperty().get()).equals(n)) return ms;
        }
        return null;
    }

    private ResourceItem findResourceByKey(Project project, ResourceItem item) {
        if (project == null || item == null) return null;
        String n = normalizeName(item.getTitle());
        String taskId = normalizeId(item.getTaskId());
        if (n.isBlank()) return null;
        for (ResourceItem r : project.getResources()) {
            if (r == null) continue;
            if (!normalizeName(r.getTitle()).equals(n)) continue;
            if (!Objects.equals(normalizeId(r.getTaskId()), taskId)) continue;
            if (r.getType() != item.getType()) continue;
            return r;
        }
        return null;
    }

    private PersonalNote findNoteByKey(Project project, PersonalNote note) {
        if (project == null || note == null) return null;
        String n = normalizeName(note.getTitle());
        String taskId = normalizeId(note.getTaskId());
        String ownerId = normalizeId(note.getOwnerId());
        if (n.isBlank()) return null;
        for (PersonalNote pn : project.getNotes()) {
            if (pn == null) continue;
            if (!normalizeName(pn.getTitle()).equals(n)) continue;
            if (!Objects.equals(normalizeId(pn.getTaskId()), taskId)) continue;
            if (!Objects.equals(normalizeId(pn.getOwnerId()), ownerId)) continue;
            return pn;
        }
        return null;
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

    private String normalizeId(String id) {
        if (id == null) return null;
        String v = id.trim();
        return v.isEmpty() ? null : v;
    }
}
