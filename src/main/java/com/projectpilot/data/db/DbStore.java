package com.projectpilot.data.db;

import com.projectpilot.data.InMemoryStore;
import com.projectpilot.data.db.auth.UserAdminService;
import com.projectpilot.model.*;
import com.projectpilot.model.enums.*;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import com.projectpilot.util.AppLog;
import com.projectpilot.util.ChecklistCodec;
import static com.projectpilot.data.db.DbDates.*;

public final class DbStore extends InMemoryStore {

    private final DbManager db;
    private final ExecutorService dbExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "pp-db-writer");
        t.setDaemon(true);
        return t;
    });

    // prevent write storms while loading
    private volatile boolean loading = false;

    // track project-level detach to avoid duplicates
    private final Map<String, Runnable> detachByProjectId = new HashMap<>();

    public DbStore(DbManager db) {
        this.db = Objects.requireNonNull(db);
        this.db.init();
        loadAll();
    }

    public void shutdown() {
        flushWrites(3, TimeUnit.SECONDS);
        dbExec.shutdown();
        try {
            if (!dbExec.awaitTermination(3, TimeUnit.SECONDS)) {
                dbExec.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            dbExec.shutdownNow();
        }
    }

    public boolean isShutdown() {
        return dbExec.isShutdown() || dbExec.isTerminated();
    }

    // -----------------------------------------
    // Startup load
    // -----------------------------------------

    public void loadAll() {
        loading = true;
        try {
            db.tx(conn -> {
                clearAllInMemory();

                // Load projects (active + history)
                List<ProjectRow> projects = selectProjects(conn);
                Map<String, Project> projectById = new HashMap<>();

                for (ProjectRow pr : projects) {
                    Project p = buildProject(pr);
                    projectById.put(p.getId(), p);
                    if (p.getStatus() == Project.ProjectStatus.DONE) {
                        getHistoryProjects().add(p);
                    } else {
                        getProjects().add(p);
                    }
                }

                // Load member identities
                Map<String, MemberRow> baseMemberById = new HashMap<>();
                for (MemberRow mr : selectMembers(conn)) {
                    baseMemberById.put(mr.id, mr);
                }
                Map<String, Map<String, Member>> membersByProject = new HashMap<>();

                // Load project membership with per-project role
                for (ProjectMemberRow pm : selectProjectMembers(conn)) {
                    Project p = projectById.get(pm.projectId);
                    MemberRow base = baseMemberById.get(pm.memberId);
                    if (p == null || base == null) continue;

                    // ✅ IMPORTANT: role must come from project_members.project_role (fallback to members.role)
                    ProjectRole role = safeEnum(ProjectRole.class, pm.projectRole,
                            safeEnum(ProjectRole.class, base.role, ProjectRole.MEMBER));

                    Map<String, Member> memberMap = membersByProject.computeIfAbsent(pm.projectId, k -> new HashMap<>());
                    Member existing = memberMap.get(base.id);
                    if (existing == null) {
                        Member m = new Member(base.id, base.name, role);
                        memberMap.put(base.id, m);
                        p.getMembers().add(m);
                    } else if (existing.getRole() != role) {
                        existing.setRole(role);
                    }
                }

                // Load phases per project
                Map<String, Phase> phaseById = new HashMap<>();
                for (PhaseRow phr : selectPhases(conn)) {
                    Project p = projectById.get(phr.projectId);
                    if (p == null) continue;

                    Phase ph = new Phase(phr.id, phr.name);
                    if (phr.startAt != null) ph.startProperty().set(fromEpochMillisToLocalDate(phr.startAt));
                    if (phr.endAt != null) ph.endProperty().set(fromEpochMillisToLocalDate(phr.endAt));

                    p.getPhases().add(ph);
                    phaseById.put(ph.getId(), ph);
                }

                // Load tasks per project
                for (TaskRow tr : selectTasks(conn)) {
                    Project p = projectById.get(tr.projectId);
                    if (p == null) continue;

                    Task t = new Task(tr.id, tr.title);
                    t.setDescription(tr.details);
                    t.setStatus(safeEnum(TaskStatus.class, tr.status, TaskStatus.TODO));
                    t.setPriority(safeEnum(Priority.class, tr.priority, Priority.MEDIUM));
                    if (tr.dueAt != null) t.setDueDate(fromEpochMillisToLocalDate(tr.dueAt));
                    t.setChecklist(ChecklistCodec.decode(tr.checklistJson));

                    String aid = (tr.assigneeMemberId == null) ? null : tr.assigneeMemberId.trim();
                    if (aid != null && !aid.isBlank()) {
                        Map<String, Member> memberMap = membersByProject.get(tr.projectId);
                        Member ass = memberMap == null ? null : memberMap.get(aid);
                        t.setAssignee(ass);
                    }

                    if (tr.phaseId != null) {
                        Phase ph = phaseById.get(tr.phaseId);
                        t.setPhase(ph);
                    }

                    p.getTasks().add(t);
                }

                // Load milestones
                for (MilestoneRow msr : selectMilestones(conn)) {
                    Project p = projectById.get(msr.projectId);
                    if (p == null) continue;

                    Milestone ms = new Milestone(msr.id, msr.title);
                    if (msr.targetAt != null) ms.dueDateProperty().set(fromEpochMillisToLocalDate(msr.targetAt));
                    ms.completedProperty().set(msr.isDone);

                    p.getMilestones().add(ms);
                }

                // Load resources
                for (ResourceRow rr : selectResources(conn)) {
                    Project p = projectById.get(rr.projectId);
                    if (p == null) continue;

                    ResourceItem r = new ResourceItem(rr.id, rr.projectId);
                    r.setTaskId(rr.taskId);
                    r.setType(safeEnum(ResourceType.class, rr.type, ResourceType.LINK));
                    r.setTitle(rr.title);
                    r.setTarget(rr.target);
                    r.setNotes(rr.notes);
                    r.setAddedBy(rr.addedBy);
                    r.setCreatedAt(fromEpochMillisToLocalDateTime(rr.createdAt));
                    r.setUpdatedAt(fromEpochMillisToLocalDateTime(rr.updatedAt));
                    p.getResources().add(r);
                }

                // Load notes
                for (NoteRow nr : selectNotes(conn)) {
                    Project p = projectById.get(nr.projectId);
                    if (p == null) continue;

                    PersonalNote note = new PersonalNote(nr.id, nr.projectId, nr.ownerId);
                    note.setTaskId(nr.taskId);
                    note.setTitle(nr.title);
                    note.setBody(nr.body);
                    note.setCreatedAt(fromEpochMillisToLocalDateTime(nr.createdAt));
                    note.setUpdatedAt(fromEpochMillisToLocalDateTime(nr.updatedAt));
                    p.getNotes().add(note);
                }

                // Load activity
                getActivity().clear();
                for (ActivityRow ar : selectRecentActivity(conn, 50)) {
                    String projectName = "-";
                    if (ar.projectId != null) {
                        Project p = projectById.get(ar.projectId);
                        if (p != null) projectName = p.getName();
                    }
                    ActivityItem item = new ActivityItem(
                            ar.projectId,
                            projectName,
                            null,
                            ar.entityType,
                            ar.entityId,
                            ar.action,
                            ar.details
                    );
                    item.timeProperty().set(fromEpochMillisToLocalDateTime(ar.at));
                    getActivity().add(item);
                }

                return null;
            });

            // After load: attach listeners
            attachListenersForAllProjects();

        } finally {
            loading = false;
        }
    }

    private void clearAllInMemory() {
        getProjects().clear();
        getHistoryProjects().clear();
        getActivity().clear();
        detachAllProjectListeners();
    }

    public java.util.List<UserAdminService.UserRow> listUsers() {
        try {
            return new UserAdminService(db).listLoginUsers()
                    .stream()
                    .filter(UserAdminService.UserRow::active)
                    .toList();
        } catch (Exception e) {
            AppLog.warn("db", "listUsers() failed: " + (e == null ? "" : e.getMessage()));
            return java.util.List.of();
        }
    }

    public DbManager manager() {
        return db;
    }

    public java.util.List<TeamService.TeamRow> listTeams() {
        try {
            return new TeamService(db).listTeams();
        } catch (Exception e) {
            AppLog.warn("db", "listTeams() failed: " + (e == null ? "" : e.getMessage()));
            return java.util.List.of();
        }
    }

    public java.util.List<TeamService.TeamMemberRow> listTeamMembers(String teamId) {
        try {
            return new TeamService(db).listTeamMembers(teamId);
        } catch (Exception e) {
            AppLog.warn("db", "listTeamMembers() failed: " + (e == null ? "" : e.getMessage()));
            return java.util.List.of();
        }
    }

    public void createTeam(String name, String leaderId, java.util.List<TeamService.TeamMemberSpec> members) {
        new TeamService(db).createTeam(name, leaderId, members);
    }

    public void assignTeamToProject(String teamId, String projectId) {
        new TeamService(db).assignTeamToProject(teamId, projectId);
    }

    public java.util.List<String> listTeamNamesForMemberInProject(String memberId, String projectId) {
        try {
            return new TeamService(db).listTeamNamesForMemberInProject(memberId, projectId);
        } catch (Exception e) {
            AppLog.warn("db", "listTeamNamesForMemberInProject() failed: " + (e == null ? "" : e.getMessage()));
            return java.util.List.of();
        }
    }

    // -----------------------------------------
    // Write-through overrides
    // -----------------------------------------

    @Override
    public Project createProject(Project project) {
        Project p = super.createProject(project);
        if (p == null) return null;
        if (loading) return p;

        final long now = System.currentTimeMillis();
        submitWrite(() -> db.tx(conn -> {
            upsertProject(conn, p, now);
            appendActivity(conn, now, p.getId(), "PROJECT", p.getId(), "CREATE", "Project created");
            persistInitialProjectContent(conn, p);
            return null;
        }));

        // project listener guarded by detachByProjectId
        attachListenersForProject(p);
        return p;
    }

    @Override
    public void deleteProject(Project project) {
        if (project == null) return;
        String pid = project.getId();

        super.deleteProject(project);
        if (loading) return;

        submitWrite(() -> db.tx(conn -> {
            deleteProjectById(conn, pid);
            appendActivity(conn, System.currentTimeMillis(), null, "PROJECT", pid, "DELETE", "Project deleted");
            return null;
        }));

        detachProjectListeners(pid);
    }

    @Override
    public Task addTask(Project project, Task task) {
        Task t = super.addTask(project, task);
        if (loading || project == null || task == null) return t;

        submitWrite(() -> db.tx(conn -> {
            upsertTask(conn, project, task);
            appendActivity(conn, System.currentTimeMillis(), project.getId(), "TASK", task.getId(),
                    "ADD", "Task added: " + safe(task.getTitle()));
            return null;
        }));

        // DO NOT attach here; project list listener will attach once.
        return t;
    }

    @Override
    public Member addMember(Project project, Member member) {
        Member m = super.addMember(project, member);
        if (loading || project == null || member == null) return m;

        submitWrite(() -> db.tx(conn -> {
            upsertMemberIdentity(conn, member);
            upsertProjectMemberRole(conn, project.getId(), member.getId(), member.getRole());
            appendActivity(conn, System.currentTimeMillis(), project.getId(), "MEMBER", member.getId(),
                    "ADD", "Member added: " + safe(member.getName()));
            return null;
        }));

        // DO NOT attach here; project list listener will attach once.
        return m;
    }

    @Override
    public void removeMember(Project project, Member member) {
        if (project == null || member == null) return;

        super.removeMember(project, member);
        if (loading) return;

        submitWrite(() -> db.tx(conn -> {
            unlinkProjectMember(conn, project.getId(), member.getId());
            nullAssigneeForMember(conn, project.getId(), member.getId());
            appendActivity(conn, System.currentTimeMillis(), project.getId(), "MEMBER", member.getId(),
                    "REMOVE", "Member removed: " + safe(member.getName()));
            return null;
        }));
    }

    @Override
    public Phase addPhase(Project project, Phase phase) {
        Phase ph = super.addPhase(project, phase);
        if (loading || project == null || phase == null) return ph;

        submitWrite(() -> db.tx(conn -> {
            upsertPhase(conn, project, phase, project.getPhases().indexOf(phase));
            appendActivity(conn, System.currentTimeMillis(), project.getId(), "PHASE", phase.getId(),
                    "ADD", "Phase added: " + safe(phase.getName()));
            return null;
        }));

        return ph;
    }

    @Override
    public Milestone addMilestone(Project project, Milestone milestone) {
        Milestone ms = super.addMilestone(project, milestone);
        if (loading || project == null || milestone == null) return ms;

        submitWrite(() -> db.tx(conn -> {
            upsertMilestone(conn, project, milestone);
            appendActivity(conn, System.currentTimeMillis(), project.getId(), "MILESTONE", milestone.getId(),
                    "ADD", "Milestone added: " + safe(milestone.nameProperty().get()));
            return null;
        }));

        return ms;
    }

    @Override
    public ResourceItem addResource(Project project, ResourceItem item) {
        ResourceItem out = super.addResource(project, item);
        if (loading || project == null || item == null) return out;

        submitWrite(() -> db.tx(conn -> {
            upsertResource(conn, project, item);
            return null;
        }));

        return out;
    }

    @Override
    public void removeResource(Project project, ResourceItem item) {
        if (project == null || item == null) return;
        super.removeResource(project, item);
        if (loading) return;

        submitWrite(() -> db.tx(conn -> {
            deleteResourceById(conn, item.getId());
            return null;
        }));
    }

    @Override
    public PersonalNote addNote(Project project, PersonalNote note) {
        PersonalNote out = super.addNote(project, note);
        if (loading || project == null || note == null) return out;

        submitWrite(() -> db.tx(conn -> {
            upsertNote(conn, project, note);
            return null;
        }));

        return out;
    }

    @Override
    public void removeNote(Project project, PersonalNote note) {
        if (project == null || note == null) return;
        super.removeNote(project, note);
        if (loading) return;

        submitWrite(() -> db.tx(conn -> {
            deleteNoteById(conn, note.getId());
            return null;
        }));
    }

    @Override
    public void markProjectDone(Project project) {
        super.markProjectDone(project);
        if (loading || project == null) return;

        submitWrite(() -> db.tx(conn -> {
            upsertProject(conn, project, System.currentTimeMillis());
            appendActivity(conn, System.currentTimeMillis(), project.getId(), "PROJECT", project.getId(),
                    "DONE", "Project marked DONE");
            return null;
        }));
    }

    @Override
    public void restoreProject(Project project) {
        super.restoreProject(project);
        if (loading || project == null) return;

        submitWrite(() -> db.tx(conn -> {
            upsertProject(conn, project, System.currentTimeMillis());
            appendActivity(conn, System.currentTimeMillis(), project.getId(), "PROJECT", project.getId(),
                    "RESTORE", "Project restored to ACTIVE");
            return null;
        }));
    }

    // -----------------------------------------
    // Listener wiring
    // -----------------------------------------

    private void attachListenersForAllProjects() {
        for (Project p : getProjects()) attachListenersForProject(p);
        for (Project p : getHistoryProjects()) attachListenersForProject(p);

        getProjects().addListener((ListChangeListener<Project>) ch -> {
            if (loading) return;
            while (ch.next()) {
                if (ch.wasAdded()) for (Project p : ch.getAddedSubList()) attachListenersForProject(p);
                if (ch.wasRemoved()) for (Project p : ch.getRemoved()) detachProjectListeners(p.getId());
            }
        });

        getHistoryProjects().addListener((ListChangeListener<Project>) ch -> {
            if (loading) return;
            while (ch.next()) {
                if (ch.wasAdded()) for (Project p : ch.getAddedSubList()) attachListenersForProject(p);
                if (ch.wasRemoved()) for (Project p : ch.getRemoved()) detachProjectListeners(p.getId());
            }
        });
    }

    private void attachListenersForProject(Project p) {
        if (p == null) return;
        String pid = p.getId();
        if (detachByProjectId.containsKey(pid)) return;

        final List<Runnable> detach = new ArrayList<>();

        ChangeListener<Object> projectDirty = (obs, o, n) -> {
            if (loading) return;
            submitWrite(() -> db.tx(conn -> {
                upsertProject(conn, p, System.currentTimeMillis());
                return null;
            }));
        };

        p.nameProperty().addListener(projectDirty);
        p.descriptionProperty().addListener(projectDirty);
        p.stakeholdersProperty().addListener(projectDirty);
        p.phaseTemplateProperty().addListener(projectDirty);
        p.startDateProperty().addListener(projectDirty);
        p.endDateProperty().addListener(projectDirty);
        p.healthProperty().addListener(projectDirty);
        p.statusProperty().addListener(projectDirty);
        p.completedDateProperty().addListener(projectDirty);

        detach.add(() -> p.nameProperty().removeListener(projectDirty));
        detach.add(() -> p.descriptionProperty().removeListener(projectDirty));
        detach.add(() -> p.stakeholdersProperty().removeListener(projectDirty));
        detach.add(() -> p.phaseTemplateProperty().removeListener(projectDirty));
        detach.add(() -> p.startDateProperty().removeListener(projectDirty));
        detach.add(() -> p.endDateProperty().removeListener(projectDirty));
        detach.add(() -> p.healthProperty().removeListener(projectDirty));
        detach.add(() -> p.statusProperty().removeListener(projectDirty));
        detach.add(() -> p.completedDateProperty().removeListener(projectDirty));

        ListChangeListener<Task> tasksListener = ch -> {
            if (loading) return;
            while (ch.next()) {
                if (ch.wasAdded()) {
                    for (Task t : ch.getAddedSubList()) {
                        submitWrite(() -> db.tx(conn -> { upsertTask(conn, p, t); return null; }));
                        attachListenersForTask(p, t);
                    }
                }
                if (ch.wasRemoved()) {
                    for (Task t : ch.getRemoved()) {
                        submitWrite(() -> db.tx(conn -> { deleteTaskById(conn, t.getId()); return null; }));
                    }
                }
            }
        };
        p.getTasks().addListener(tasksListener);
        detach.add(() -> p.getTasks().removeListener(tasksListener));

        ListChangeListener<Phase> phasesListener = ch -> {
            if (loading) return;
            while (ch.next()) {
                if (ch.wasAdded()) {
                    for (Phase ph : ch.getAddedSubList()) {
                        int idx = p.getPhases().indexOf(ph);
                        submitWrite(() -> db.tx(conn -> { upsertPhase(conn, p, ph, idx); return null; }));
                        attachListenersForPhase(p, ph);
                    }
                }
                if (ch.wasRemoved()) {
                    for (Phase ph : ch.getRemoved()) {
                        submitWrite(() -> db.tx(conn -> { deletePhaseById(conn, ph.getId()); return null; }));
                    }
                }
            }
        };
        p.getPhases().addListener(phasesListener);
        detach.add(() -> p.getPhases().removeListener(phasesListener));

        ListChangeListener<Member> membersListener = ch -> {
            if (loading) return;
            while (ch.next()) {
                if (ch.wasAdded()) {
                    for (Member m : ch.getAddedSubList()) {
                        submitWrite(() -> db.tx(conn -> {
                            upsertMemberIdentity(conn, m);
                            upsertProjectMemberRole(conn, p.getId(), m.getId(), m.getRole());
                            return null;
                        }));
                        attachListenersForMember(p, m);
                    }
                }
                if (ch.wasRemoved()) {
                    for (Member m : ch.getRemoved()) {
                        submitWrite(() -> db.tx(conn -> {
                            unlinkProjectMember(conn, p.getId(), m.getId());
                            nullAssigneeForMember(conn, p.getId(), m.getId());
                            return null;
                        }));
                    }
                }
            }
        };
        p.getMembers().addListener(membersListener);
        detach.add(() -> p.getMembers().removeListener(membersListener));

        ListChangeListener<Milestone> milestonesListener = ch -> {
            if (loading) return;
            while (ch.next()) {
                if (ch.wasAdded()) {
                    for (Milestone ms : ch.getAddedSubList()) {
                        submitWrite(() -> db.tx(conn -> { upsertMilestone(conn, p, ms); return null; }));
                        attachListenersForMilestone(p, ms);
                    }
                }
                if (ch.wasRemoved()) {
                    for (Milestone ms : ch.getRemoved()) {
                        submitWrite(() -> db.tx(conn -> { deleteMilestoneById(conn, ms.getId()); return null; }));
                    }
                }
            }
        };
        p.getMilestones().addListener(milestonesListener);
        detach.add(() -> p.getMilestones().removeListener(milestonesListener));

        ListChangeListener<ResourceItem> resourcesListener = ch -> {
            if (loading) return;
            while (ch.next()) {
                if (ch.wasAdded()) {
                    for (ResourceItem r : ch.getAddedSubList()) {
                        submitWrite(() -> db.tx(conn -> { upsertResource(conn, p, r); return null; }));
                        attachListenersForResource(p, r);
                    }
                }
                if (ch.wasRemoved()) {
                    for (ResourceItem r : ch.getRemoved()) {
                        submitWrite(() -> db.tx(conn -> { deleteResourceById(conn, r.getId()); return null; }));
                    }
                }
            }
        };
        p.getResources().addListener(resourcesListener);
        detach.add(() -> p.getResources().removeListener(resourcesListener));

        ListChangeListener<PersonalNote> notesListener = ch -> {
            if (loading) return;
            while (ch.next()) {
                if (ch.wasAdded()) {
                    for (PersonalNote note : ch.getAddedSubList()) {
                        submitWrite(() -> db.tx(conn -> { upsertNote(conn, p, note); return null; }));
                        attachListenersForNote(p, note);
                    }
                }
                if (ch.wasRemoved()) {
                    for (PersonalNote note : ch.getRemoved()) {
                        submitWrite(() -> db.tx(conn -> { deleteNoteById(conn, note.getId()); return null; }));
                    }
                }
            }
        };
        p.getNotes().addListener(notesListener);
        detach.add(() -> p.getNotes().removeListener(notesListener));

        for (Task t : p.getTasks()) attachListenersForTask(p, t);
        for (Phase ph : p.getPhases()) attachListenersForPhase(p, ph);
        for (Member m : p.getMembers()) attachListenersForMember(p, m);
        for (Milestone ms : p.getMilestones()) attachListenersForMilestone(p, ms);
        for (ResourceItem r : p.getResources()) attachListenersForResource(p, r);
        for (PersonalNote note : p.getNotes()) attachListenersForNote(p, note);

        detachByProjectId.put(pid, () -> detach.forEach(Runnable::run));
    }

    private void attachListenersForTask(Project p, Task t) {
        if (t == null || p == null) return;

        ChangeListener<Object> dirty = (obs, o, n) -> {
            if (loading) return;
            submitWrite(() -> db.tx(conn -> { upsertTask(conn, p, t); return null; }));
        };

        t.titleProperty().addListener(dirty);
        t.descriptionProperty().addListener(dirty);
        t.statusProperty().addListener(dirty);
        t.priorityProperty().addListener(dirty);
        t.dueDateProperty().addListener(dirty);
        t.assigneeProperty().addListener(dirty);
        t.phaseProperty().addListener(dirty);
        t.checklistVersionProperty().addListener(dirty);
    }

    private void attachListenersForPhase(Project p, Phase ph) {
        if (ph == null || p == null) return;

        ChangeListener<Object> dirty = (obs, o, n) -> {
            if (loading) return;
            int idx = p.getPhases().indexOf(ph);
            submitWrite(() -> db.tx(conn -> { upsertPhase(conn, p, ph, idx); return null; }));
        };

        ph.nameProperty().addListener(dirty);
        ph.startProperty().addListener(dirty);
        ph.endProperty().addListener(dirty);
    }

    private void attachListenersForMember(Project p, Member m) {
        if (m == null || p == null) return;

        ChangeListener<Object> dirty = (obs, o, n) -> {
            if (loading) return;
            submitWrite(() -> db.tx(conn -> {
                upsertMemberIdentity(conn, m);
                upsertProjectMemberRole(conn, p.getId(), m.getId(), m.getRole());
                return null;
            }));
        };

        m.nameProperty().addListener(dirty);
        m.roleProperty().addListener(dirty);
    }

    private void attachListenersForMilestone(Project p, Milestone ms) {
        if (ms == null || p == null) return;

        ChangeListener<Object> dirty = (obs, o, n) -> {
            if (loading) return;
            submitWrite(() -> db.tx(conn -> { upsertMilestone(conn, p, ms); return null; }));
        };

        ms.nameProperty().addListener(dirty);
        ms.dueDateProperty().addListener(dirty);
        ms.completedProperty().addListener(dirty);
    }

    private void attachListenersForResource(Project p, ResourceItem r) {
        if (r == null || p == null) return;

        ChangeListener<Object> dirty = (obs, o, n) -> {
            if (loading) return;
            submitWrite(() -> db.tx(conn -> { upsertResource(conn, p, r); return null; }));
        };

        r.taskIdProperty().addListener(dirty);
        r.typeProperty().addListener(dirty);
        r.titleProperty().addListener(dirty);
        r.targetProperty().addListener(dirty);
        r.notesProperty().addListener(dirty);
        r.addedByProperty().addListener(dirty);
        r.updatedAtProperty().addListener(dirty);
    }

    private void attachListenersForNote(Project p, PersonalNote note) {
        if (note == null || p == null) return;

        ChangeListener<Object> dirty = (obs, o, n) -> {
            if (loading) return;
            submitWrite(() -> db.tx(conn -> { upsertNote(conn, p, note); return null; }));
        };

        note.taskIdProperty().addListener(dirty);
        note.ownerIdProperty().addListener(dirty);
        note.titleProperty().addListener(dirty);
        note.bodyProperty().addListener(dirty);
        note.updatedAtProperty().addListener(dirty);
    }

    private void detachAllProjectListeners() {
        for (String pid : new ArrayList<>(detachByProjectId.keySet())) detachProjectListeners(pid);
    }

    private void detachProjectListeners(String pid) {
        Runnable r = detachByProjectId.remove(pid);
        if (r != null) r.run();
    }

    // -----------------------------------------
    // DB write scheduling
    // -----------------------------------------

    private void submitWrite(Runnable job) {
        if (dbExec.isShutdown() || dbExec.isTerminated()) {
            return;
        }
        try {
            dbExec.submit(() -> {
                try {
                    job.run();
                } catch (Exception ex) {
                    AppLog.error("db", "Write failed: " + ex.getMessage(), ex);
                }
            });
        } catch (RejectedExecutionException ignored) {
            // shutting down
        }
    }

    private void persistInitialProjectContent(Connection conn, Project p) {
        if (p == null) return;

        for (Member m : p.getMembers()) {
            if (m == null) continue;
            upsertMemberIdentity(conn, m);
            upsertProjectMemberRole(conn, p.getId(), m.getId(), m.getRole());
        }

        int phaseIdx = 0;
        for (Phase ph : p.getPhases()) {
            if (ph == null) continue;
            upsertPhase(conn, p, ph, phaseIdx++);
        }

        for (Task t : p.getTasks()) {
            if (t == null) continue;
            upsertTask(conn, p, t);
        }

        for (Milestone ms : p.getMilestones()) {
            if (ms == null) continue;
            upsertMilestone(conn, p, ms);
        }

        for (ResourceItem r : p.getResources()) {
            if (r == null) continue;
            upsertResource(conn, p, r);
        }

        for (PersonalNote note : p.getNotes()) {
            if (note == null) continue;
            upsertNote(conn, p, note);
        }
    }

    private void flushWrites(long timeout, TimeUnit unit) {
        try {
            Future<?> f = dbExec.submit(() -> {});
            f.get(timeout, unit);
        } catch (RejectedExecutionException ignored) {
            // already shutting down
        } catch (TimeoutException e) {
            AppLog.warn("db", "flush timeout: " + (e == null ? "" : e.getMessage()));
        } catch (Exception e) {
            AppLog.warn("db", "flush failed: " + (e == null ? "" : e.getMessage()));
        }
    }

    // -----------------------------------------
    // Project members / directory
    // -----------------------------------------

    private void upsertProjectMemberRole(Connection conn, String projectId, String memberId, ProjectRole role) {
        ProjectRole r = (role == null) ? ProjectRole.MEMBER : role;
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO project_members(project_id, member_id, project_role, added_at)
            VALUES(?,?,?,?)
            ON CONFLICT(project_id, member_id)
            DO UPDATE SET project_role = excluded.project_role
            """)) {

            ps.setString(1, projectId);
            ps.setString(2, memberId);
            ps.setString(3, r.name());
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();

        } catch (Exception e) {
            throw new DbException("Failed to upsert project member role", e);
        }
    }

    private void upsertMemberIdentity(Connection conn, Member m) {
        try (PreparedStatement ps = conn.prepareStatement("""
        INSERT INTO members (id, name, role, email, created_at, updated_at)
        VALUES (?, ?, ?, '', ?, ?)
        ON CONFLICT(id) DO UPDATE SET
          name=excluded.name,
          updated_at=excluded.updated_at
        """)) {
            long now = System.currentTimeMillis();
            ps.setString(1, m.getId());
            ps.setString(2, nullToEmpty(m.getName()));
            // keep whatever you pass (used as a "default role" when adding to other projects)
            ps.setString(3, (m.getRole() == null ? ProjectRole.MEMBER : m.getRole()).name());
            ps.setLong(4, now);
            ps.setLong(5, now);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new DbException("Failed to upsert member identity " + m.getId(), e);
        }
    }

    /**
     * Directory = all ACTIVE login accounts mapped to members.
     * Returned Member.role is used as a *default role* when adding to a project.
     */
    public List<Member> listDirectoryUsers() {
        return db.tx(conn -> {
            List<Member> out = new ArrayList<>();

            try (PreparedStatement ps = conn.prepareStatement("""
            SELECT m.id AS id,
                   COALESCE(NULLIF(m.name,''), au.username) AS display,
                   COALESCE(NULLIF(m.role,''), 'MEMBER') AS default_role
            FROM auth_users au
            JOIN members m ON m.id = au.member_id
            WHERE au.is_active = 1
            ORDER BY display COLLATE NOCASE
            """);
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    String id = rs.getString("id");
                    String display = rs.getString("display");
                    String roleStr = rs.getString("default_role");
                    ProjectRole role = safeEnum(ProjectRole.class, roleStr, ProjectRole.MEMBER);

                    out.add(new Member(id, display, role));
                }
            } catch (Exception e) {
                throw new DbException("listDirectoryUsers failed", e);
            }

            return out;
        });
    }

    // -----------------------------------------
    // Minimal DB row structs + SQL
    // -----------------------------------------

    private static final class ProjectRow {
        String id, name, description, stakeholders, phaseTemplate, health, status;
        Long startDate, endDate, completedDate;
        long createdAt, updatedAt;
    }

    private static final class PhaseRow {
        String id, projectId, name;
        int sortIndex;
        Long startAt, endAt;
    }

    private static final class MemberRow {
        String id, name, role;
    }

    private static final class ProjectMemberRow {
        String projectId, memberId, projectRole;
    }

    private static final class TaskRow {
        String id, projectId, phaseId, title, details, status, priority, assigneeMemberId;
        String checklistJson;
        Long dueAt;
        int sortIndex;
    }

    private static final class MilestoneRow {
        String id, projectId, title;
        Long targetAt;
        boolean isDone;
    }

    private static final class ResourceRow {
        String id, projectId, taskId, type, title, target, notes, addedBy;
        long createdAt, updatedAt;
    }

    private static final class NoteRow {
        String id, projectId, taskId, ownerId, title, body;
        long createdAt, updatedAt;
    }

    private static final class ActivityRow {
        String id, projectId, entityType, entityId, action, details;
        long at;
    }

    private List<ProjectRow> selectProjects(Connection conn) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                SELECT id, name, description, stakeholders, phase_template, start_date, end_date, health, status, completed_date, created_at, updated_at
                FROM projects
                """)) {

            List<ProjectRow> out = new ArrayList<>();
            while (rs.next()) {
                ProjectRow r = new ProjectRow();
                r.id = rs.getString("id");
                r.name = rs.getString("name");
                r.description = rs.getString("description");
                r.stakeholders = rs.getString("stakeholders");
                r.phaseTemplate = rs.getString("phase_template");
                r.startDate = readNullableLong(rs, "start_date");
                r.endDate = readNullableLong(rs, "end_date");
                r.health = rs.getString("health");
                r.status = rs.getString("status");
                r.completedDate = readNullableLong(rs, "completed_date");
                r.createdAt = rs.getLong("created_at");
                r.updatedAt = rs.getLong("updated_at");
                out.add(r);
            }
            return out;
        } catch (Exception e) {
            throw new DbException("Failed to select projects", e);
        }
    }

    private List<PhaseRow> selectPhases(Connection conn) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                SELECT id, project_id, name, sort_index, start_at, end_at
                FROM phases
                ORDER BY project_id, sort_index
                """)) {

            List<PhaseRow> out = new ArrayList<>();
            while (rs.next()) {
                PhaseRow r = new PhaseRow();
                r.id = rs.getString("id");
                r.projectId = rs.getString("project_id");
                r.name = rs.getString("name");
                r.sortIndex = rs.getInt("sort_index");
                r.startAt = readNullableLong(rs, "start_at");
                r.endAt = readNullableLong(rs, "end_at");
                out.add(r);
            }
            return out;
        } catch (Exception e) {
            throw new DbException("Failed to select phases", e);
        }
    }

    private List<MemberRow> selectMembers(Connection conn) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, name, role FROM members")) {

            List<MemberRow> out = new ArrayList<>();
            while (rs.next()) {
                MemberRow r = new MemberRow();
                r.id = rs.getString("id");
                r.name = rs.getString("name");
                r.role = rs.getString("role");
                out.add(r);
            }
            return out;
        } catch (Exception e) {
            throw new DbException("Failed to select members", e);
        }
    }

    private List<ProjectMemberRow> selectProjectMembers(Connection conn) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                SELECT project_id, member_id, project_role
                FROM project_members
                """)) {

            List<ProjectMemberRow> out = new ArrayList<>();
            while (rs.next()) {
                ProjectMemberRow r = new ProjectMemberRow();
                r.projectId = rs.getString("project_id");
                r.memberId = rs.getString("member_id");
                r.projectRole = rs.getString("project_role");
                out.add(r);
            }
            return out;
        } catch (Exception e) {
            throw new DbException("Failed to select project_members", e);
        }
    }

    private List<TaskRow> selectTasks(Connection conn) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                SELECT id, project_id, phase_id, title, details, status, priority, assignee_member_id, due_at, sort_index, checklist_json
                FROM tasks
                ORDER BY project_id, sort_index
                """)) {

            List<TaskRow> out = new ArrayList<>();
            while (rs.next()) {
                TaskRow r = new TaskRow();
                r.id = rs.getString("id");
                r.projectId = rs.getString("project_id");
                r.phaseId = rs.getString("phase_id");
                r.title = rs.getString("title");
                r.details = rs.getString("details");
                r.status = rs.getString("status");
                r.priority = rs.getString("priority");
                r.assigneeMemberId = rs.getString("assignee_member_id");
                r.dueAt = readNullableLong(rs, "due_at");
                r.sortIndex = rs.getInt("sort_index");
                r.checklistJson = rs.getString("checklist_json");
                out.add(r);
            }
            return out;
        } catch (Exception e) {
            throw new DbException("Failed to select tasks", e);
        }
    }

    private List<MilestoneRow> selectMilestones(Connection conn) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                SELECT id, project_id, title, target_at, is_done
                FROM milestones
                ORDER BY project_id
                """)) {

            List<MilestoneRow> out = new ArrayList<>();
            while (rs.next()) {
                MilestoneRow r = new MilestoneRow();
                r.id = rs.getString("id");
                r.projectId = rs.getString("project_id");
                r.title = rs.getString("title");
                r.targetAt = readNullableLong(rs, "target_at");
                r.isDone = rs.getInt("is_done") != 0;
                out.add(r);
            }
            return out;
        } catch (Exception e) {
            throw new DbException("Failed to select milestones", e);
        }
    }

    private List<ResourceRow> selectResources(Connection conn) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                SELECT id, project_id, task_id, type, title, target, notes, added_by, created_at, updated_at
                FROM resources
                ORDER BY project_id, created_at
                """)) {

            List<ResourceRow> out = new ArrayList<>();
            while (rs.next()) {
                ResourceRow r = new ResourceRow();
                r.id = rs.getString("id");
                r.projectId = rs.getString("project_id");
                r.taskId = rs.getString("task_id");
                r.type = rs.getString("type");
                r.title = rs.getString("title");
                r.target = rs.getString("target");
                r.notes = rs.getString("notes");
                r.addedBy = rs.getString("added_by");
                r.createdAt = rs.getLong("created_at");
                r.updatedAt = rs.getLong("updated_at");
                out.add(r);
            }
            return out;
        } catch (Exception e) {
            throw new DbException("Failed to select resources", e);
        }
    }

    private List<NoteRow> selectNotes(Connection conn) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                SELECT id, project_id, task_id, owner_id, title, body, created_at, updated_at
                FROM notes
                ORDER BY project_id, updated_at DESC
                """)) {

            List<NoteRow> out = new ArrayList<>();
            while (rs.next()) {
                NoteRow r = new NoteRow();
                r.id = rs.getString("id");
                r.projectId = rs.getString("project_id");
                r.taskId = rs.getString("task_id");
                r.ownerId = rs.getString("owner_id");
                r.title = rs.getString("title");
                r.body = rs.getString("body");
                r.createdAt = rs.getLong("created_at");
                r.updatedAt = rs.getLong("updated_at");
                out.add(r);
            }
            return out;
        } catch (Exception e) {
            throw new DbException("Failed to select notes", e);
        }
    }

    private List<ActivityRow> selectRecentActivity(Connection conn, int limit) {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT id, at, project_id, entity_type, entity_id, action, details
                FROM activity_log
                ORDER BY at DESC
                LIMIT ?
                """)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<ActivityRow> out = new ArrayList<>();
                while (rs.next()) {
                    ActivityRow r = new ActivityRow();
                    r.id = rs.getString("id");
                    r.at = rs.getLong("at");
                    r.projectId = rs.getString("project_id");
                    r.entityType = rs.getString("entity_type");
                    r.entityId = rs.getString("entity_id");
                    r.action = rs.getString("action");
                    r.details = rs.getString("details");
                    out.add(r);
                }
                return out;
            }
        } catch (Exception e) {
            throw new DbException("Failed to select activity", e);
        }
    }

    private Project buildProject(ProjectRow pr) {
        Project p = new Project(pr.id, pr.name);
        p.setDescription(nullToEmpty(pr.description));
        p.setStakeholders(nullToEmpty(pr.stakeholders));
        p.setPhaseTemplate(nullToEmpty(pr.phaseTemplate));
        p.setHealth(safeEnum(ProjectHealth.class, pr.health, ProjectHealth.ON_TRACK));
        p.setStatus(safeEnum(Project.ProjectStatus.class, pr.status, Project.ProjectStatus.ACTIVE));

        LocalDate sd = fromEpochMillisToLocalDate(pr.startDate);
        LocalDate ed = fromEpochMillisToLocalDate(pr.endDate);
        LocalDate cd = fromEpochMillisToLocalDate(pr.completedDate);

        if (sd != null) p.setStartDate(sd);
        if (ed != null) p.setEndDate(ed);
        p.setCompletedDate(cd);

        return p;
    }

    private void upsertProject(Connection conn, Project p, long now) {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO projects (id, name, description, stakeholders, phase_template, start_date, end_date, health, status, completed_date, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
              name=excluded.name,
              description=excluded.description,
              stakeholders=excluded.stakeholders,
              phase_template=excluded.phase_template,
              start_date=excluded.start_date,
              end_date=excluded.end_date,
              health=excluded.health,
              status=excluded.status,
              completed_date=excluded.completed_date,
              updated_at=excluded.updated_at
            """)) {

            ps.setString(1, p.getId());
            ps.setString(2, nullToEmpty(p.getName()));
            ps.setString(3, nullToEmpty(p.getDescription()));
            ps.setString(4, nullToEmpty(p.getStakeholders()));
            ps.setString(5, nullToEmpty(p.getPhaseTemplate()));
            bindNullableLong(ps, 6, toEpochMillis(p.getStartDate()));
            bindNullableLong(ps, 7, toEpochMillis(p.getEndDate()));
            ps.setString(8, p.getHealth() == null ? ProjectHealth.ON_TRACK.name() : p.getHealth().name());
            ps.setString(9, p.getStatus() == null ? Project.ProjectStatus.ACTIVE.name() : p.getStatus().name());
            bindNullableLong(ps, 10, toEpochMillis(p.getCompletedDate()));
            ps.setLong(11, now);
            ps.setLong(12, now);

            ps.executeUpdate();
        } catch (Exception e) {
            throw new DbException("Failed to upsert project " + p.getId(), e);
        }
    }

    private void deleteProjectById(Connection conn, String id) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM projects WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new DbException("Failed to delete project " + id, e);
        }
    }

    private void upsertPhase(Connection conn, Project p, Phase ph, int sort) {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO phases (id, project_id, name, sort_index, start_at, end_at, is_done, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
              name=excluded.name,
              sort_index=excluded.sort_index,
              start_at=excluded.start_at,
              end_at=excluded.end_at,
              updated_at=excluded.updated_at
            """)) {
            long now = System.currentTimeMillis();
            ps.setString(1, ph.getId());
            ps.setString(2, p.getId());
            ps.setString(3, nullToEmpty(ph.getName()));
            ps.setInt(4, Math.max(0, sort));
            bindNullableLong(ps, 5, toEpochMillis(ph.startProperty().get()));
            bindNullableLong(ps, 6, toEpochMillis(ph.endProperty().get()));
            ps.setLong(7, now);
            ps.setLong(8, now);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new DbException("Failed to upsert phase " + ph.getId(), e);
        }
    }

    private void deletePhaseById(Connection conn, String id) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM phases WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new DbException("Failed to delete phase " + id, e);
        }
    }

    private void unlinkProjectMember(Connection conn, String projectId, String memberId) {
        try (PreparedStatement ps = conn.prepareStatement("""
            DELETE FROM project_members WHERE project_id = ? AND member_id = ?
            """)) {
            ps.setString(1, projectId);
            ps.setString(2, memberId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new DbException("Failed to unlink project_member " + projectId + "/" + memberId, e);
        }
    }

    private void nullAssigneeForMember(Connection conn, String projectId, String memberId) {
        try (PreparedStatement ps = conn.prepareStatement("""
            UPDATE tasks SET assignee_member_id = NULL
            WHERE project_id = ? AND assignee_member_id = ?
            """)) {
            ps.setString(1, projectId);
            ps.setString(2, memberId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new DbException("Failed to null assignee for member " + memberId, e);
        }
    }

    private void upsertTask(Connection conn, Project p, Task t) {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO tasks (id, project_id, phase_id, title, details, status, priority, assignee_member_id, due_at, sort_index, checklist_json, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
              phase_id=excluded.phase_id,
              title=excluded.title,
              details=excluded.details,
              status=excluded.status,
              priority=excluded.priority,
              assignee_member_id=excluded.assignee_member_id,
              due_at=excluded.due_at,
              sort_index=excluded.sort_index,
              checklist_json=excluded.checklist_json,
              updated_at=excluded.updated_at
            """)) {
            long now = System.currentTimeMillis();
            ps.setString(1, t.getId());
            ps.setString(2, p.getId());
            ps.setString(3, t.getPhase() == null ? null : t.getPhase().getId());
            ps.setString(4, nullToEmpty(t.getTitle()));
            ps.setString(5, nullToEmpty(t.getDescription()));
            ps.setString(6, t.getStatus() == null ? TaskStatus.TODO.name() : t.getStatus().name());
            ps.setString(7, t.getPriority() == null ? Priority.MEDIUM.name() : t.getPriority().name());
            ps.setString(8, t.getAssignee() == null ? null : t.getAssignee().getId());
            bindNullableLong(ps, 9, toEpochMillis(t.getDueDate()));
            ps.setInt(10, Math.max(0, p.getTasks().indexOf(t)));
            ps.setString(11, ChecklistCodec.encode(t.getChecklist()));
            ps.setLong(12, now);
            ps.setLong(13, now);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new DbException("Failed to upsert task " + t.getId(), e);
        }
    }

    private void deleteTaskById(Connection conn, String id) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM tasks WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new DbException("Failed to delete task " + id, e);
        }
    }

    private void upsertMilestone(Connection conn, Project p, Milestone ms) {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO milestones (id, project_id, title, target_at, is_done, done_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
              title=excluded.title,
              target_at=excluded.target_at,
              is_done=excluded.is_done,
              done_at=excluded.done_at,
              updated_at=excluded.updated_at
            """)) {
            long now = System.currentTimeMillis();
            ps.setString(1, ms.getId());
            ps.setString(2, p.getId());
            ps.setString(3, nullToEmpty(ms.nameProperty().get()));
            bindNullableLong(ps, 4, toEpochMillis(ms.dueDateProperty().get()));
            ps.setInt(5, ms.completedProperty().get() ? 1 : 0);
            bindNullableLong(ps, 6, ms.completedProperty().get() ? System.currentTimeMillis() : null);
            ps.setLong(7, now);
            ps.setLong(8, now);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new DbException("Failed to upsert milestone " + ms.getId(), e);
        }
    }

    private void deleteMilestoneById(Connection conn, String id) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM milestones WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new DbException("Failed to delete milestone " + id, e);
        }
    }

    private void upsertResource(Connection conn, Project p, ResourceItem r) {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO resources (id, project_id, task_id, type, title, target, notes, added_by, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
              task_id=excluded.task_id,
              type=excluded.type,
              title=excluded.title,
              target=excluded.target,
              notes=excluded.notes,
              added_by=excluded.added_by,
              updated_at=excluded.updated_at
            """)) {
            long now = System.currentTimeMillis();
            Long createdAt = toEpochMillis(r.getCreatedAt());
            Long updatedAt = toEpochMillis(r.getUpdatedAt());
            if (createdAt == null) createdAt = now;
            if (updatedAt == null) updatedAt = now;

            ps.setString(1, r.getId());
            ps.setString(2, p.getId());
            ps.setString(3, r.getTaskId());
            ps.setString(4, r.getType() == null ? ResourceType.LINK.name() : r.getType().name());
            ps.setString(5, nullToEmpty(r.getTitle()));
            ps.setString(6, nullToEmpty(r.getTarget()));
            ps.setString(7, nullToEmpty(r.getNotes()));
            ps.setString(8, nullToEmpty(r.getAddedBy()));
            ps.setLong(9, createdAt);
            ps.setLong(10, updatedAt);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new DbException("Failed to upsert resource " + r.getId(), e);
        }
    }

    private void deleteResourceById(Connection conn, String id) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM resources WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new DbException("Failed to delete resource " + id, e);
        }
    }

    private void upsertNote(Connection conn, Project p, PersonalNote note) {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO notes (id, project_id, task_id, owner_id, title, body, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
              task_id=excluded.task_id,
              owner_id=excluded.owner_id,
              title=excluded.title,
              body=excluded.body,
              updated_at=excluded.updated_at
            """)) {
            long now = System.currentTimeMillis();
            Long createdAt = toEpochMillis(note.getCreatedAt());
            Long updatedAt = toEpochMillis(note.getUpdatedAt());
            if (createdAt == null) createdAt = now;
            if (updatedAt == null) updatedAt = now;

            ps.setString(1, note.getId());
            ps.setString(2, p.getId());
            ps.setString(3, note.getTaskId());
            ps.setString(4, nullToEmpty(note.getOwnerId()));
            ps.setString(5, nullToEmpty(note.getTitle()));
            ps.setString(6, nullToEmpty(note.getBody()));
            ps.setLong(7, createdAt);
            ps.setLong(8, updatedAt);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new DbException("Failed to upsert note " + note.getId(), e);
        }
    }

    private void deleteNoteById(Connection conn, String id) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM notes WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new DbException("Failed to delete note " + id, e);
        }
    }

    private void appendActivity(Connection conn, long at, String projectId, String entityType, String entityId, String action, String details) {
        try (PreparedStatement ps = conn.prepareStatement("""
            INSERT INTO activity_log (id, at, actor, project_id, entity_type, entity_id, action, details)
            VALUES (?, ?, '', ?, ?, ?, ?, ?)
            """)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setLong(2, at);
            ps.setString(3, projectId);
            ps.setString(4, entityType);
            ps.setString(5, entityId == null ? "-" : entityId);
            ps.setString(6, action);
            ps.setString(7, nullToEmpty(details));
            ps.executeUpdate();

            runOnUi(() -> {
                String pn = "-";
                if (projectId != null) {
                    Project p = findProjectById(projectId);
                    if (p != null) pn = p.getName();
                }
                ActivityItem item = new ActivityItem(
                        projectId,
                        pn,
                        null,
                        entityType,
                        entityId,
                        action,
                        nullToEmpty(details)
                );
                item.timeProperty().set(LocalDateTime.now());
                getActivity().add(0, item);
                if (getActivity().size() > 50) getActivity().remove(getActivity().size() - 1);
            });

        } catch (Exception e) {
            throw new DbException("Failed to append activity", e);
        }
    }

    private void runOnUi(Runnable action) {
        if (action == null) return;
        try {
            if (Platform.isFxApplicationThread()) {
                action.run();
            } else {
                Platform.runLater(action);
            }
        } catch (IllegalStateException e) {
            // JavaFX toolkit not initialized (headless server). Run inline.
            action.run();
        }
    }

    private Project findProjectById(String id) {
        for (Project p : getProjects()) if (p.getId().equals(id)) return p;
        for (Project p : getHistoryProjects()) if (p.getId().equals(id)) return p;
        return null;
    }

    // -----------------------------------------
    // tiny JDBC utils
    // -----------------------------------------

    private static void bindNullableLong(PreparedStatement ps, int idx, Long v) throws Exception {
        if (v == null) ps.setNull(idx, Types.BIGINT);
        else ps.setLong(idx, v);
    }

    private static Long readNullableLong(ResultSet rs, String col) throws Exception {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    private static <E extends Enum<E>> E safeEnum(Class<E> type, String name, E fallback) {
        if (name == null || name.isBlank()) return fallback;
        try { return Enum.valueOf(type, name); } catch (Exception e) { com.projectpilot.util.AppLog.warn("db", "safeEnum parse failed for " + name + ": " + (e == null ? "" : e.getMessage())); return fallback; }
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
    private static String safeStr(String s) { return s == null ? "" : s; }
}
