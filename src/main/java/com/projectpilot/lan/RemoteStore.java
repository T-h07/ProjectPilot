package com.projectpilot.lan;

import com.projectpilot.data.InMemoryStore;
import com.projectpilot.data.db.TeamService;
import com.projectpilot.lan.dto.*;
import com.projectpilot.model.*;
import com.projectpilot.model.enums.Priority;
import com.projectpilot.model.enums.ProjectRole;
import com.projectpilot.model.enums.TaskStatus;
import com.projectpilot.util.ChecklistCodec;
import javafx.application.Platform;
import javafx.beans.property.Property;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.*;
import com.projectpilot.util.AppLog;

public final class RemoteStore extends InMemoryStore {

    private final LanClient client;
    private boolean syncing = false;

    // tombstones for recently locally-deleted entities to avoid immediate resurrection
    private final Map<String, Long> tombstoneAt = new HashMap<>();
    private static final long TOMBSTONE_TTL_MS = 60_000L; // 1 minute

    private final Map<String, Runnable> detachByProjectId = new HashMap<>();
    private final Map<String, Map<String, Runnable>> taskDetachers = new HashMap<>();
    private final Map<String, Map<String, Runnable>> memberDetachers = new HashMap<>();
    private final Map<String, Map<String, Runnable>> phaseDetachers = new HashMap<>();
    private final Map<String, Map<String, Runnable>> milestoneDetachers = new HashMap<>();
    private final Map<String, Map<String, Runnable>> resourceDetachers = new HashMap<>();
    private final Map<String, Map<String, Runnable>> noteDetachers = new HashMap<>();

    public RemoteStore(LanClient client) {
        this.client = client;
        attachProjectListListeners();
    }

    public void applySnapshot(SnapshotDto snapshot) {
        if (snapshot == null) return;

        syncing = true;
        try {
            applyProjects(getProjects(), snapshot.projects(), false);
            applyProjects(getHistoryProjects(), snapshot.history(), true);
            applyActivity(snapshot.activity());
        } finally {
            syncing = false;
        }
    }

    public Project findProjectById(String id) {
        if (id == null) return null;
        for (Project p : getProjects()) if (id.equals(p.getId())) return p;
        for (Project p : getHistoryProjects()) if (id.equals(p.getId())) return p;
        return null;
    }

    public List<Member> listDirectoryUsers() {
        try {
            List<DirectoryUserDto> rows = client.fetchDirectoryUsers();
            List<Member> out = new ArrayList<>();
            if (rows != null) {
                for (DirectoryUserDto row : rows) {
                    if (row == null) continue;
                    ProjectRole role = row.role() == null ? ProjectRole.MEMBER : row.role();
                    out.add(new Member(row.id(), row.name(), role));
                }
            }
            return out;
        } catch (Exception e) {
            AppLog.warn("remote-store", "Failed to fetch directory users: " + (e == null ? "unknown" : e.getMessage()));
            return List.of();
        }
    }

    public List<TeamService.TeamRow> listTeams() {
        try {
            return client.fetchTeams();
        } catch (Exception e) {
            AppLog.warn("remote-store", "Failed to fetch teams: " + (e == null ? "unknown" : e.getMessage()));
            return List.of();
        }
    }

    public List<TeamService.TeamMemberRow> listTeamMembers(String teamId) {
        try {
            return client.fetchTeamMembers(teamId);
        } catch (Exception e) {
            AppLog.warn("remote-store", "Failed to fetch team members: " + (e == null ? "unknown" : e.getMessage()));
            return List.of();
        }
    }

    public void assignTeamToProject(String teamId, String projectId) {
        client.assignTeam(teamId, projectId);
    }

    public List<String> listTeamNamesForMemberInProject(String memberId, String projectId) {
        try {
            return client.fetchTeamNamesForMemberInProject(memberId, projectId);
        } catch (Exception e) {
            AppLog.warn("remote-store", "Failed to fetch team names: " + (e == null ? "unknown" : e.getMessage()));
            return List.of();
        }
    }

    private void attachProjectListListeners() {
        getProjects().addListener((ListChangeListener<Project>) c -> handleProjectListChange(c));
        getHistoryProjects().addListener((ListChangeListener<Project>) c -> handleProjectListChange(c));
    }

    private void handleProjectListChange(ListChangeListener.Change<? extends Project> c) {
        while (c.next()) {
            if (c.wasAdded()) {
                for (Project p : c.getAddedSubList()) {
                    attachProjectListeners(p);
                    if (!syncing) sendProjectUpsert(p);
                }
            }
            if (c.wasRemoved()) {
                for (Project p : c.getRemoved()) {
                    detachProjectListeners(p.getId());
                    if (!syncing) scheduleProjectDeleteCheck(p);
                }
            }
        }
    }

    private void scheduleProjectDeleteCheck(Project p) {
        if (p == null) return;
        long now = System.currentTimeMillis();
        tombstoneAt.put(projectKey(p.getId()), now);
        Platform.runLater(() -> {
            if (syncing) return;
            boolean stillThere = getProjects().contains(p) || getHistoryProjects().contains(p);
            if (!stillThere) {
                client.sendAction(new SyncAction(
                        SyncType.PROJECT_DELETE,
                        p.getId(),
                        p.getId(),
                        null, null, null, null, null, null, null
                ));
            }
        });
    }

    private void attachProjectListeners(Project p) {
        if (p == null) return;
        if (detachByProjectId.containsKey(p.getId())) return;

        List<Runnable> detach = new ArrayList<>();

        ChangeListener<Object> projectDirty = (obs, o, n) -> {
            if (syncing) return;
            sendProjectUpsert(p);
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
            while (ch.next()) {
                if (ch.wasAdded()) {
                    for (Task t : ch.getAddedSubList()) {
                        attachTaskListeners(p, t);
                        if (!syncing) sendTaskUpsert(p, t);
                    }
                }
                if (ch.wasRemoved()) {
                    for (Task t : ch.getRemoved()) {
                        detachTaskListeners(p, t);
                        if (t != null && t.getId() != null) {
                            tombstoneAt.put(taskKey(p.getId(), t.getId()), System.currentTimeMillis());
                        }
                        if (!syncing) sendTaskDelete(p, t);
                    }
                }
            }
        };
        p.getTasks().addListener(tasksListener);
        detach.add(() -> p.getTasks().removeListener(tasksListener));

        for (Task t : p.getTasks()) attachTaskListeners(p, t);

        ListChangeListener<Member> membersListener = ch -> {
            while (ch.next()) {
                if (ch.wasAdded()) {
                    for (Member m : ch.getAddedSubList()) {
                        attachMemberListeners(p, m);
                        if (!syncing) sendMemberUpsert(p, m);
                    }
                }
                if (ch.wasRemoved()) {
                        for (Member m : ch.getRemoved()) {
                            detachMemberListeners(p, m);
                            if (m != null && m.getId() != null) {
                                tombstoneAt.put(memberKey(p.getId(), m.getId()), System.currentTimeMillis());
                            }
                            if (!syncing) sendMemberDelete(p, m);
                        }
                }
            }
        };
        p.getMembers().addListener(membersListener);
        detach.add(() -> p.getMembers().removeListener(membersListener));

        for (Member m : p.getMembers()) attachMemberListeners(p, m);

        ListChangeListener<Phase> phasesListener = ch -> {
            while (ch.next()) {
                if (ch.wasAdded()) {
                    for (Phase ph : ch.getAddedSubList()) {
                        attachPhaseListeners(p, ph);
                        if (!syncing) sendPhaseUpsert(p, ph);
                    }
                }
                if (ch.wasRemoved()) {
                        for (Phase ph : ch.getRemoved()) {
                            detachPhaseListeners(p, ph);
                            if (ph != null && ph.getId() != null) {
                                tombstoneAt.put(phaseKey(p.getId(), ph.getId()), System.currentTimeMillis());
                            }
                            if (!syncing) sendPhaseDelete(p, ph);
                        }
                }
            }
        };
        p.getPhases().addListener(phasesListener);
        detach.add(() -> p.getPhases().removeListener(phasesListener));

        for (Phase ph : p.getPhases()) attachPhaseListeners(p, ph);

        ListChangeListener<Milestone> milestonesListener = ch -> {
            while (ch.next()) {
                if (ch.wasAdded()) {
                    for (Milestone ms : ch.getAddedSubList()) {
                        attachMilestoneListeners(p, ms);
                        if (!syncing) sendMilestoneUpsert(p, ms);
                    }
                }
                if (ch.wasRemoved()) {
                        for (Milestone ms : ch.getRemoved()) {
                            detachMilestoneListeners(p, ms);
                            if (ms != null && ms.getId() != null) {
                                tombstoneAt.put(milestoneKey(p.getId(), ms.getId()), System.currentTimeMillis());
                            }
                            if (!syncing) sendMilestoneDelete(p, ms);
                        }
                }
            }
        };
        p.getMilestones().addListener(milestonesListener);
        detach.add(() -> p.getMilestones().removeListener(milestonesListener));

        for (Milestone ms : p.getMilestones()) attachMilestoneListeners(p, ms);

        ListChangeListener<ResourceItem> resourcesListener = ch -> {
            while (ch.next()) {
                if (ch.wasAdded()) {
                    for (ResourceItem r : ch.getAddedSubList()) {
                        attachResourceListeners(p, r);
                        if (!syncing) sendResourceUpsert(p, r);
                    }
                }
                if (ch.wasRemoved()) {
                        for (ResourceItem r : ch.getRemoved()) {
                            detachResourceListeners(p, r);
                            if (r != null && r.getId() != null) {
                                tombstoneAt.put(resourceKey(p.getId(), r.getId()), System.currentTimeMillis());
                            }
                            if (!syncing) sendResourceDelete(p, r);
                        }
                }
            }
        };
        p.getResources().addListener(resourcesListener);
        detach.add(() -> p.getResources().removeListener(resourcesListener));

        for (ResourceItem r : p.getResources()) attachResourceListeners(p, r);

        ListChangeListener<PersonalNote> notesListener = ch -> {
            while (ch.next()) {
                if (ch.wasAdded()) {
                    for (PersonalNote note : ch.getAddedSubList()) {
                        attachNoteListeners(p, note);
                        if (!syncing) sendNoteUpsert(p, note);
                    }
                }
                if (ch.wasRemoved()) {
                        for (PersonalNote note : ch.getRemoved()) {
                            detachNoteListeners(p, note);
                            if (note != null && note.getId() != null) {
                                tombstoneAt.put(noteKey(p.getId(), note.getId()), System.currentTimeMillis());
                            }
                            if (!syncing) sendNoteDelete(p, note);
                        }
                }
            }
        };
        p.getNotes().addListener(notesListener);
        detach.add(() -> p.getNotes().removeListener(notesListener));

        for (PersonalNote note : p.getNotes()) attachNoteListeners(p, note);

        detachByProjectId.put(p.getId(), () -> detach.forEach(Runnable::run));
    }

    private void detachProjectListeners(String projectId) {
        if (projectId == null) return;
        Runnable detach = detachByProjectId.remove(projectId);
        if (detach != null) detach.run();

        taskDetachers.remove(projectId);
        memberDetachers.remove(projectId);
        phaseDetachers.remove(projectId);
        milestoneDetachers.remove(projectId);
        resourceDetachers.remove(projectId);
        noteDetachers.remove(projectId);
    }

    private void attachTaskListeners(Project p, Task t) {
        if (p == null || t == null || t.getId() == null) return;
        Map<String, Runnable> map = taskDetachers.computeIfAbsent(p.getId(), k -> new HashMap<>());
        if (map.containsKey(t.getId())) return;

        ChangeListener<Object> taskDirty = (obs, o, n) -> {
            if (syncing) return;
            sendTaskUpsert(p, t);
        };

        t.titleProperty().addListener(taskDirty);
        t.descriptionProperty().addListener(taskDirty);
        t.statusProperty().addListener(taskDirty);
        t.priorityProperty().addListener(taskDirty);
        t.dueDateProperty().addListener(taskDirty);
        t.assigneeProperty().addListener(taskDirty);
        t.phaseProperty().addListener(taskDirty);
        t.checklistVersionProperty().addListener(taskDirty);

        map.put(t.getId(), () -> {
            t.titleProperty().removeListener(taskDirty);
            t.descriptionProperty().removeListener(taskDirty);
            t.statusProperty().removeListener(taskDirty);
            t.priorityProperty().removeListener(taskDirty);
            t.dueDateProperty().removeListener(taskDirty);
            t.assigneeProperty().removeListener(taskDirty);
            t.phaseProperty().removeListener(taskDirty);
            t.checklistVersionProperty().removeListener(taskDirty);
        });
    }

    private void detachTaskListeners(Project p, Task t) {
        if (p == null || t == null) return;
        Map<String, Runnable> map = taskDetachers.get(p.getId());
        if (map == null) return;
        Runnable detach = map.remove(t.getId());
        if (detach != null) detach.run();
    }

    private void attachMemberListeners(Project p, Member m) {
        if (p == null || m == null || m.getId() == null) return;
        Map<String, Runnable> map = memberDetachers.computeIfAbsent(p.getId(), k -> new HashMap<>());
        if (map.containsKey(m.getId())) return;

        ChangeListener<Object> memberDirty = (obs, o, n) -> {
            if (syncing) return;
            sendMemberUpsert(p, m);
        };

        m.nameProperty().addListener(memberDirty);
        m.roleProperty().addListener(memberDirty);

        map.put(m.getId(), () -> {
            m.nameProperty().removeListener(memberDirty);
            m.roleProperty().removeListener(memberDirty);
        });
    }

    private void detachMemberListeners(Project p, Member m) {
        if (p == null || m == null) return;
        Map<String, Runnable> map = memberDetachers.get(p.getId());
        if (map == null) return;
        Runnable detach = map.remove(m.getId());
        if (detach != null) detach.run();
    }

    private void attachPhaseListeners(Project p, Phase ph) {
        if (p == null || ph == null || ph.getId() == null) return;
        Map<String, Runnable> map = phaseDetachers.computeIfAbsent(p.getId(), k -> new HashMap<>());
        if (map.containsKey(ph.getId())) return;

        ChangeListener<Object> phaseDirty = (obs, o, n) -> {
            if (syncing) return;
            sendPhaseUpsert(p, ph);
        };

        ph.nameProperty().addListener(phaseDirty);
        ph.startProperty().addListener(phaseDirty);
        ph.endProperty().addListener(phaseDirty);

        map.put(ph.getId(), () -> {
            ph.nameProperty().removeListener(phaseDirty);
            ph.startProperty().removeListener(phaseDirty);
            ph.endProperty().removeListener(phaseDirty);
        });
    }

    private void detachPhaseListeners(Project p, Phase ph) {
        if (p == null || ph == null) return;
        Map<String, Runnable> map = phaseDetachers.get(p.getId());
        if (map == null) return;
        Runnable detach = map.remove(ph.getId());
        if (detach != null) detach.run();
    }

    private void attachMilestoneListeners(Project p, Milestone ms) {
        if (p == null || ms == null || ms.getId() == null) return;
        Map<String, Runnable> map = milestoneDetachers.computeIfAbsent(p.getId(), k -> new HashMap<>());
        if (map.containsKey(ms.getId())) return;

        ChangeListener<Object> msDirty = (obs, o, n) -> {
            if (syncing) return;
            sendMilestoneUpsert(p, ms);
        };

        ms.nameProperty().addListener(msDirty);
        ms.dueDateProperty().addListener(msDirty);
        ms.completedProperty().addListener(msDirty);

        map.put(ms.getId(), () -> {
            ms.nameProperty().removeListener(msDirty);
            ms.dueDateProperty().removeListener(msDirty);
            ms.completedProperty().removeListener(msDirty);
        });
    }

    private void detachMilestoneListeners(Project p, Milestone ms) {
        if (p == null || ms == null) return;
        Map<String, Runnable> map = milestoneDetachers.get(p.getId());
        if (map == null) return;
        Runnable detach = map.remove(ms.getId());
        if (detach != null) detach.run();
    }

    private void attachResourceListeners(Project p, ResourceItem r) {
        if (p == null || r == null || r.getId() == null) return;
        Map<String, Runnable> map = resourceDetachers.computeIfAbsent(p.getId(), k -> new HashMap<>());
        if (map.containsKey(r.getId())) return;

        ChangeListener<Object> resourceDirty = (obs, o, n) -> {
            if (syncing) return;
            sendResourceUpsert(p, r);
        };

        r.taskIdProperty().addListener(resourceDirty);
        r.typeProperty().addListener(resourceDirty);
        r.titleProperty().addListener(resourceDirty);
        r.targetProperty().addListener(resourceDirty);
        r.notesProperty().addListener(resourceDirty);
        r.addedByProperty().addListener(resourceDirty);
        r.updatedAtProperty().addListener(resourceDirty);

        map.put(r.getId(), () -> {
            r.taskIdProperty().removeListener(resourceDirty);
            r.typeProperty().removeListener(resourceDirty);
            r.titleProperty().removeListener(resourceDirty);
            r.targetProperty().removeListener(resourceDirty);
            r.notesProperty().removeListener(resourceDirty);
            r.addedByProperty().removeListener(resourceDirty);
            r.updatedAtProperty().removeListener(resourceDirty);
        });
    }

    private void detachResourceListeners(Project p, ResourceItem r) {
        if (p == null || r == null) return;
        Map<String, Runnable> map = resourceDetachers.get(p.getId());
        if (map == null) return;
        Runnable detach = map.remove(r.getId());
        if (detach != null) detach.run();
    }

    private void attachNoteListeners(Project p, PersonalNote note) {
        if (p == null || note == null || note.getId() == null) return;
        Map<String, Runnable> map = noteDetachers.computeIfAbsent(p.getId(), k -> new HashMap<>());
        if (map.containsKey(note.getId())) return;

        ChangeListener<Object> noteDirty = (obs, o, n) -> {
            if (syncing) return;
            sendNoteUpsert(p, note);
        };

        note.taskIdProperty().addListener(noteDirty);
        note.ownerIdProperty().addListener(noteDirty);
        note.titleProperty().addListener(noteDirty);
        note.bodyProperty().addListener(noteDirty);
        note.updatedAtProperty().addListener(noteDirty);

        map.put(note.getId(), () -> {
            note.taskIdProperty().removeListener(noteDirty);
            note.ownerIdProperty().removeListener(noteDirty);
            note.titleProperty().removeListener(noteDirty);
            note.bodyProperty().removeListener(noteDirty);
            note.updatedAtProperty().removeListener(noteDirty);
        });
    }

    private void detachNoteListeners(Project p, PersonalNote note) {
        if (p == null || note == null) return;
        Map<String, Runnable> map = noteDetachers.get(p.getId());
        if (map == null) return;
        Runnable detach = map.remove(note.getId());
        if (detach != null) detach.run();
    }

    private void sendProjectUpsert(Project p) {
        if (p == null) return;
        ProjectDto dto = new ProjectDto(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getStakeholders(),
                p.getPhaseTemplate(),
                p.getHealth(),
                p.getStatus(),
                p.getStartDate(),
                p.getEndDate(),
                p.getCompletedDate(),
                null, null, null, null, null, null
        );
        client.sendAction(new SyncAction(
                SyncType.PROJECT_UPSERT,
                p.getId(),
                null,
                dto, null, null, null, null, null, null
        ));
    }

    private void sendTaskUpsert(Project p, Task t) {
        if (p == null || t == null) return;
        TaskDto dto = new TaskDto(
                t.getId(),
                t.getTitle(),
                t.getDescription(),
                t.getStatus(),
                t.getPriority(),
                t.getDueDate(),
                t.getAssignee() == null ? null : t.getAssignee().getId(),
                t.getPhase() == null ? null : t.getPhase().getId(),
                ChecklistCodec.encode(t.getChecklist())
        );
        client.sendAction(new SyncAction(
                SyncType.TASK_UPSERT,
                p.getId(),
                t.getId(),
                null,
                dto, null, null, null, null, null
        ));
    }

    private void sendTaskDelete(Project p, Task t) {
        if (p == null || t == null) return;
        client.sendAction(new SyncAction(
                SyncType.TASK_DELETE,
                p.getId(),
                t.getId(),
                null, null, null, null, null, null, null
        ));
    }

    private void sendMemberUpsert(Project p, Member m) {
        if (p == null || m == null) return;
        MemberDto dto = new MemberDto(m.getId(), m.getName(), m.getRole());
        client.sendAction(new SyncAction(
                SyncType.MEMBER_UPSERT,
                p.getId(),
                m.getId(),
                null, null, dto, null, null, null, null
        ));
    }

    private void sendMemberDelete(Project p, Member m) {
        if (p == null || m == null) return;
        client.sendAction(new SyncAction(
                SyncType.MEMBER_REMOVE,
                p.getId(),
                m.getId(),
                null, null, null, null, null, null, null
        ));
    }

    private void sendPhaseUpsert(Project p, Phase ph) {
        if (p == null || ph == null) return;
        int idx = Math.max(0, p.getPhases().indexOf(ph));
        PhaseDto dto = new PhaseDto(ph.getId(), ph.getName(), ph.startProperty().get(), ph.endProperty().get(), idx);
        client.sendAction(new SyncAction(
                SyncType.PHASE_UPSERT,
                p.getId(),
                ph.getId(),
                null, null, null, dto, null, null, null
        ));
    }

    private void sendPhaseDelete(Project p, Phase ph) {
        if (p == null || ph == null) return;
        client.sendAction(new SyncAction(
                SyncType.PHASE_DELETE,
                p.getId(),
                ph.getId(),
                null, null, null, null, null, null, null
        ));
    }

    private void sendMilestoneUpsert(Project p, Milestone ms) {
        if (p == null || ms == null) return;
        MilestoneDto dto = new MilestoneDto(
                ms.getId(),
                ms.nameProperty().get(),
                ms.dueDateProperty().get(),
                ms.completedProperty().get()
        );
        client.sendAction(new SyncAction(
                SyncType.MILESTONE_UPSERT,
                p.getId(),
                ms.getId(),
                null, null, null, null, dto, null, null
        ));
    }

    private void sendMilestoneDelete(Project p, Milestone ms) {
        if (p == null || ms == null) return;
        client.sendAction(new SyncAction(
                SyncType.MILESTONE_DELETE,
                p.getId(),
                ms.getId(),
                null, null, null, null, null, null, null
        ));
    }

    private void sendResourceUpsert(Project p, ResourceItem r) {
        if (p == null || r == null) return;
        ResourceDto dto = new ResourceDto(
                r.getId(),
                r.getTaskId(),
                r.getType(),
                r.getTitle(),
                r.getTarget(),
                r.getNotes(),
                r.getAddedBy(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
        client.sendAction(new SyncAction(
                SyncType.RESOURCE_UPSERT,
                p.getId(),
                r.getId(),
                null, null, null, null, null,
                dto, null
        ));
    }

    private void sendResourceDelete(Project p, ResourceItem r) {
        if (p == null || r == null) return;
        client.sendAction(new SyncAction(
                SyncType.RESOURCE_DELETE,
                p.getId(),
                r.getId(),
                null, null, null, null, null,
                null, null
        ));
    }

    private void sendNoteUpsert(Project p, PersonalNote note) {
        if (p == null || note == null) return;
        NoteDto dto = new NoteDto(
                note.getId(),
                note.getTaskId(),
                note.getOwnerId(),
                note.getTitle(),
                note.getBody(),
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
        client.sendAction(new SyncAction(
                SyncType.NOTE_UPSERT,
                p.getId(),
                note.getId(),
                null, null, null, null, null,
                null, dto
        ));
    }

    private void sendNoteDelete(Project p, PersonalNote note) {
        if (p == null || note == null) return;
        client.sendAction(new SyncAction(
                SyncType.NOTE_DELETE,
                p.getId(),
                note.getId(),
                null, null, null, null, null,
                null, null
        ));
    }

    // --- tombstone helpers ---
    private static String projectKey(String projectId) { return "project:" + (projectId == null ? "" : projectId); }
    private static String taskKey(String projectId, String taskId) { return "project:" + (projectId == null ? "" : projectId) + ":task:" + (taskId == null ? "" : taskId); }
    private static String memberKey(String projectId, String memberId) { return "project:" + (projectId == null ? "" : projectId) + ":member:" + (memberId == null ? "" : memberId); }
    private static String phaseKey(String projectId, String phaseId) { return "project:" + (projectId == null ? "" : projectId) + ":phase:" + (phaseId == null ? "" : phaseId); }
    private static String milestoneKey(String projectId, String msId) { return "project:" + (projectId == null ? "" : projectId) + ":milestone:" + (msId == null ? "" : msId); }
    private static String resourceKey(String projectId, String rId) { return "project:" + (projectId == null ? "" : projectId) + ":resource:" + (rId == null ? "" : rId); }
    private static String noteKey(String projectId, String nId) { return "project:" + (projectId == null ? "" : projectId) + ":note:" + (nId == null ? "" : nId); }

    private boolean isTombstoned(String key, long now) {
        if (key == null) return false;
        Long at = tombstoneAt.get(key);
        if (at == null) return false;
        return now - at < TOMBSTONE_TTL_MS;
    }

    private void applyProjects(ObservableList<Project> target, List<ProjectDto> incoming, boolean history) {
        List<ProjectDto> list = incoming == null ? List.of() : incoming;
        Map<String, Project> existing = new HashMap<>();
        for (Project p : target) {
            if (p == null) continue;
            existing.put(p.getId(), p);
        }

        List<Project> next = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        long now = System.currentTimeMillis();
        for (ProjectDto dto : list) {
            if (dto == null || dto.id() == null) continue;
            // skip incoming projects that were just deleted locally (tombstone TTL)
            if (isTombstoned(projectKey(dto.id()), now)) continue;
            Project p = existing.get(dto.id());
            if (p == null) {
                p = new Project(dto.id(), safe(dto.name()));
            }
            applyProjectDto(p, dto, history);
            next.add(p);
            seen.add(dto.id());
        }

        for (Project p : target) {
            if (p != null && !seen.contains(p.getId())) detachProjectListeners(p.getId());
        }

        if (!sameList(target, next)) {
            target.setAll(next);
        }

        for (Project p : next) attachProjectListeners(p);
    }

    private void applyProjectDto(Project p, ProjectDto dto, boolean history) {
        setIfDifferent(p.nameProperty(), safe(dto.name()));
        setIfDifferent(p.descriptionProperty(), safe(dto.description()));
        setIfDifferent(p.stakeholdersProperty(), safe(dto.stakeholders()));
        setIfDifferent(p.phaseTemplateProperty(), safe(dto.phaseTemplate()));
        if (dto.health() != null) setIfDifferent(p.healthProperty(), dto.health());
        setIfDifferent(p.startDateProperty(), dto.startDate());
        setIfDifferent(p.endDateProperty(), dto.endDate());
        setIfDifferent(p.completedDateProperty(), dto.completedDate());

        Project.ProjectStatus status = dto.status();
        if (status == null) status = history ? Project.ProjectStatus.DONE : Project.ProjectStatus.ACTIVE;
        setIfDifferent(p.statusProperty(), status);

        Map<String, Member> members = applyMembers(p, dto.members());
        Map<String, Phase> phases = applyPhases(p, dto.phases());
        applyTasks(p, dto.tasks(), members, phases);
        applyMilestones(p, dto.milestones());
        applyResources(p, dto.resources());
        applyNotes(p, dto.notes());
    }

    private Map<String, Member> applyMembers(Project p, List<MemberDto> list) {
        Map<String, Member> existing = new HashMap<>();
        for (Member m : p.getMembers()) {
            if (m != null && m.getId() != null) existing.put(m.getId(), m);
        }

        Map<String, Member> map = new HashMap<>();
        List<Member> next = new ArrayList<>();
        if (list != null) {
            long now = System.currentTimeMillis();
            for (MemberDto dto : list) {
                if (dto == null || dto.id() == null) continue;
                if (isTombstoned(memberKey(p.getId(), dto.id()), now)) continue;
                ProjectRole role = dto.role() == null ? ProjectRole.MEMBER : dto.role();
                Member m = existing.get(dto.id());
                if (m == null) {
                    m = new Member(dto.id(), safe(dto.name()), role);
                } else {
                    setIfDifferent(m.nameProperty(), safe(dto.name()));
                    setIfDifferent(m.roleProperty(), role);
                }
                next.add(m);
                map.put(m.getId(), m);
            }
        }

        if (!sameList(p.getMembers(), next)) {
            p.getMembers().setAll(next);
        }
        return map;
    }

    private Map<String, Phase> applyPhases(Project p, List<PhaseDto> list) {
        Map<String, Phase> existing = new HashMap<>();
        for (Phase ph : p.getPhases()) {
            if (ph != null && ph.getId() != null) existing.put(ph.getId(), ph);
        }

        Map<String, Phase> map = new HashMap<>();
        List<Phase> next = new ArrayList<>();
        if (list != null) {
            long now = System.currentTimeMillis();
            for (PhaseDto dto : list) {
                if (dto == null || dto.id() == null) continue;
                if (isTombstoned(phaseKey(p.getId(), dto.id()), now)) continue;
                Phase ph = existing.get(dto.id());
                if (ph == null) {
                    ph = new Phase(dto.id(), safe(dto.name()));
                } else {
                    setIfDifferent(ph.nameProperty(), safe(dto.name()));
                }
                setIfDifferent(ph.startProperty(), dto.start());
                setIfDifferent(ph.endProperty(), dto.end());
                next.add(ph);
                map.put(ph.getId(), ph);
            }
        }

        if (!sameList(p.getPhases(), next)) {
            p.getPhases().setAll(next);
        }
        return map;
    }

    private void applyTasks(Project p, List<TaskDto> list, Map<String, Member> members, Map<String, Phase> phases) {
        Map<String, Task> existing = new HashMap<>();
        for (Task t : p.getTasks()) {
            if (t != null && t.getId() != null) existing.put(t.getId(), t);
        }

        List<Task> next = new ArrayList<>();
        if (list != null) {
            long now = System.currentTimeMillis();
            for (TaskDto dto : list) {
                if (dto == null || dto.id() == null) continue;
                // skip incoming tasks that were just deleted locally
                if (isTombstoned(taskKey(p.getId(), dto.id()), now)) continue;
                Task t = existing.get(dto.id());
                if (t == null) {
                    t = new Task(dto.id(), safe(dto.title()));
                } else {
                    setIfDifferent(t.titleProperty(), safe(dto.title()));
                }
                setIfDifferent(t.descriptionProperty(), safe(dto.description()));
                setIfDifferent(t.statusProperty(), dto.status() == null ? TaskStatus.TODO : dto.status());
                setIfDifferent(t.priorityProperty(), dto.priority() == null ? Priority.MEDIUM : dto.priority());
                setIfDifferent(t.dueDateProperty(), dto.dueDate());

                Member assignee = dto.assigneeId() == null || members == null ? null : members.get(dto.assigneeId());
                Phase phase = dto.phaseId() == null || phases == null ? null : phases.get(dto.phaseId());
                setIfDifferent(t.assigneeProperty(), assignee);
                setIfDifferent(t.phaseProperty(), phase);
                var checklist = ChecklistCodec.decode(dto.checklistJson());
                if (!sameChecklist(t.getChecklist(), checklist)) {
                    t.setChecklist(checklist);
                }
                next.add(t);
            }
        }
        if (!sameList(p.getTasks(), next)) {
            p.getTasks().setAll(next);
        }
    }

    private void applyMilestones(Project p, List<MilestoneDto> list) {
        Map<String, Milestone> existing = new HashMap<>();
        for (Milestone ms : p.getMilestones()) {
            if (ms != null && ms.getId() != null) existing.put(ms.getId(), ms);
        }

        List<Milestone> next = new ArrayList<>();
        if (list != null) {
            long now = System.currentTimeMillis();
            for (MilestoneDto dto : list) {
                if (dto == null || dto.id() == null) continue;
                if (isTombstoned(milestoneKey(p.getId(), dto.id()), now)) continue;
                Milestone ms = existing.get(dto.id());
                if (ms == null) {
                    ms = new Milestone(dto.id(), safe(dto.title()));
                } else {
                    setIfDifferent(ms.nameProperty(), safe(dto.title()));
                }
                setIfDifferent(ms.dueDateProperty(), dto.dueDate());
                setIfDifferent(ms.completedProperty(), dto.done());
                next.add(ms);
            }
        }
        if (!sameList(p.getMilestones(), next)) {
            p.getMilestones().setAll(next);
        }
    }

    private void applyResources(Project p, List<ResourceDto> list) {
        Map<String, ResourceItem> existing = new HashMap<>();
        for (ResourceItem r : p.getResources()) {
            if (r != null && r.getId() != null) existing.put(r.getId(), r);
        }

        List<ResourceItem> next = new ArrayList<>();
        if (list != null) {
            long now = System.currentTimeMillis();
            for (ResourceDto dto : list) {
                if (dto == null || dto.id() == null) continue;
                if (isTombstoned(resourceKey(p.getId(), dto.id()), now)) continue;
                ResourceItem r = existing.get(dto.id());
                if (r == null) {
                    r = new ResourceItem(dto.id(), p.getId());
                }
                setIfDifferent(r.taskIdProperty(), dto.taskId());
                setIfDifferent(r.typeProperty(), dto.type());
                setIfDifferent(r.titleProperty(), safe(dto.title()));
                setIfDifferent(r.targetProperty(), safe(dto.target()));
                setIfDifferent(r.notesProperty(), safe(dto.notes()));
                setIfDifferent(r.addedByProperty(), safe(dto.addedBy()));
                setIfDifferent(r.createdAtProperty(), dto.createdAt());
                setIfDifferent(r.updatedAtProperty(), dto.updatedAt());
                next.add(r);
            }
        }

        if (!sameList(p.getResources(), next)) {
            p.getResources().setAll(next);
        }
    }

    private void applyNotes(Project p, List<NoteDto> list) {
        Map<String, PersonalNote> existing = new HashMap<>();
        for (PersonalNote note : p.getNotes()) {
            if (note != null && note.getId() != null) existing.put(note.getId(), note);
        }

        List<PersonalNote> next = new ArrayList<>();
        if (list != null) {
            long now = System.currentTimeMillis();
            for (NoteDto dto : list) {
                if (dto == null || dto.id() == null) continue;
                if (isTombstoned(noteKey(p.getId(), dto.id()), now)) continue;
                PersonalNote note = existing.get(dto.id());
                if (note == null) {
                    note = new PersonalNote(dto.id(), p.getId(), safe(dto.ownerId()));
                }
                setIfDifferent(note.taskIdProperty(), dto.taskId());
                setIfDifferent(note.ownerIdProperty(), safe(dto.ownerId()));
                setIfDifferent(note.titleProperty(), safe(dto.title()));
                setIfDifferent(note.bodyProperty(), safe(dto.body()));
                setIfDifferent(note.createdAtProperty(), dto.createdAt());
                setIfDifferent(note.updatedAtProperty(), dto.updatedAt());
                next.add(note);
            }
        }

        if (!sameList(p.getNotes(), next)) {
            p.getNotes().setAll(next);
        }
    }

    private void applyActivity(List<ActivityDto> list) {
        if (list == null) {
            if (!getActivity().isEmpty()) getActivity().clear();
            return;
        }

        if (isSameActivity(list)) return;

        List<ActivityItem> next = new ArrayList<>();
        for (ActivityDto dto : list) {
            if (dto == null) continue;
            ActivityItem it = new ActivityItem(
                    safe(dto.projectId()),
                    safe(dto.projectName()),
                    safe(dto.actor()),
                    safe(dto.entityType()),
                    safe(dto.entityId()),
                    safe(dto.action()),
                    safe(dto.message())
            );
            if (dto.time() != null) it.timeProperty().set(dto.time());
            next.add(it);
        }
        if (!sameList(getActivity(), next)) {
            getActivity().setAll(next);
        }
    }

    private boolean isSameActivity(List<ActivityDto> list) {
        if (list == null) return getActivity().isEmpty();
        if (list.size() != getActivity().size()) return false;
        for (int i = 0; i < list.size(); i++) {
            ActivityDto dto = list.get(i);
            ActivityItem cur = getActivity().get(i);
            if (dto == null || cur == null) return false;
            if (!Objects.equals(cur.getProjectId(), safe(dto.projectId()))) return false;
            if (!Objects.equals(cur.getProjectName(), safe(dto.projectName()))) return false;
            if (!Objects.equals(cur.getActor(), safe(dto.actor()))) return false;
            if (!Objects.equals(cur.getEntityType(), safe(dto.entityType()))) return false;
            if (!Objects.equals(cur.getEntityId(), safe(dto.entityId()))) return false;
            if (!Objects.equals(cur.getAction(), safe(dto.action()))) return false;
            if (!Objects.equals(cur.getMessage(), safe(dto.message()))) return false;
            if (!Objects.equals(cur.getTime(), dto.time())) return false;
        }
        return true;
    }

    private static <T> void setIfDifferent(Property<T> prop, T value) {
        if (!Objects.equals(prop.getValue(), value)) prop.setValue(value);
    }

    private static <T> boolean sameList(List<T> left, List<T> right) {
        if (left == right) return true;
        if (left == null || right == null) return false;
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            if (left.get(i) != right.get(i)) return false;
        }
        return true;
    }

    private static boolean sameChecklist(List<ChecklistItem> left, List<ChecklistItem> right) {
        if (left == right) return true;
        if (left == null || right == null) return false;
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            ChecklistItem a = left.get(i);
            ChecklistItem b = right.get(i);
            if (a == null || b == null) return false;
            if (!Objects.equals(a.getText(), b.getText())) return false;
            if (a.isDone() != b.isDone()) return false;
        }
        return true;
    }

    @Override
    protected String safe(String v) {
        return v == null ? "" : v;
    }
}
