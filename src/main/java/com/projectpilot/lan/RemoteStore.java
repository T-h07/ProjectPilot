package com.projectpilot.lan;

import com.projectpilot.data.InMemoryStore;
import com.projectpilot.lan.dto.*;
import com.projectpilot.model.*;
import com.projectpilot.model.enums.Priority;
import com.projectpilot.model.enums.ProjectRole;
import com.projectpilot.model.enums.TaskStatus;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.*;

public final class RemoteStore extends InMemoryStore {

    private final LanClient client;
    private boolean syncing = false;

    private final Map<String, Runnable> detachByProjectId = new HashMap<>();
    private final Map<String, Map<String, Runnable>> taskDetachers = new HashMap<>();
    private final Map<String, Map<String, Runnable>> memberDetachers = new HashMap<>();
    private final Map<String, Map<String, Runnable>> phaseDetachers = new HashMap<>();
    private final Map<String, Map<String, Runnable>> milestoneDetachers = new HashMap<>();

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
        Platform.runLater(() -> {
            if (syncing) return;
            boolean stillThere = getProjects().contains(p) || getHistoryProjects().contains(p);
            if (!stillThere) {
                client.sendAction(new SyncAction(
                        SyncType.PROJECT_DELETE,
                        p.getId(),
                        p.getId(),
                        null, null, null, null, null
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
                        if (!syncing) sendMilestoneDelete(p, ms);
                    }
                }
            }
        };
        p.getMilestones().addListener(milestonesListener);
        detach.add(() -> p.getMilestones().removeListener(milestonesListener));

        for (Milestone ms : p.getMilestones()) attachMilestoneListeners(p, ms);

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

        map.put(t.getId(), () -> {
            t.titleProperty().removeListener(taskDirty);
            t.descriptionProperty().removeListener(taskDirty);
            t.statusProperty().removeListener(taskDirty);
            t.priorityProperty().removeListener(taskDirty);
            t.dueDateProperty().removeListener(taskDirty);
            t.assigneeProperty().removeListener(taskDirty);
            t.phaseProperty().removeListener(taskDirty);
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
                null, null, null, null
        );
        client.sendAction(new SyncAction(
                SyncType.PROJECT_UPSERT,
                p.getId(),
                null,
                dto, null, null, null, null
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
                t.getPhase() == null ? null : t.getPhase().getId()
        );
        client.sendAction(new SyncAction(
                SyncType.TASK_UPSERT,
                p.getId(),
                t.getId(),
                null,
                dto, null, null, null
        ));
    }

    private void sendTaskDelete(Project p, Task t) {
        if (p == null || t == null) return;
        client.sendAction(new SyncAction(
                SyncType.TASK_DELETE,
                p.getId(),
                t.getId(),
                null, null, null, null, null
        ));
    }

    private void sendMemberUpsert(Project p, Member m) {
        if (p == null || m == null) return;
        MemberDto dto = new MemberDto(m.getId(), m.getName(), m.getRole());
        client.sendAction(new SyncAction(
                SyncType.MEMBER_UPSERT,
                p.getId(),
                m.getId(),
                null, null, dto, null, null
        ));
    }

    private void sendMemberDelete(Project p, Member m) {
        if (p == null || m == null) return;
        client.sendAction(new SyncAction(
                SyncType.MEMBER_REMOVE,
                p.getId(),
                m.getId(),
                null, null, null, null, null
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
                null, null, null, dto, null
        ));
    }

    private void sendPhaseDelete(Project p, Phase ph) {
        if (p == null || ph == null) return;
        client.sendAction(new SyncAction(
                SyncType.PHASE_DELETE,
                p.getId(),
                ph.getId(),
                null, null, null, null, null
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
                null, null, null, null, dto
        ));
    }

    private void sendMilestoneDelete(Project p, Milestone ms) {
        if (p == null || ms == null) return;
        client.sendAction(new SyncAction(
                SyncType.MILESTONE_DELETE,
                p.getId(),
                ms.getId(),
                null, null, null, null, null
        ));
    }

    private void applyProjects(ObservableList<Project> target, List<ProjectDto> incoming, boolean history) {
        List<ProjectDto> list = incoming == null ? List.of() : incoming;
        Map<String, Project> existing = new HashMap<>();
        for (Project p : target) existing.put(p.getId(), p);

        List<Project> next = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (ProjectDto dto : list) {
            if (dto == null || dto.id() == null) continue;
            Project p = existing.get(dto.id());
            if (p == null) {
                p = new Project(dto.id(), safe(dto.name()));
            }
            applyProjectDto(p, dto, history);
            next.add(p);
            seen.add(dto.id());
        }

        for (Project p : target) {
            if (!seen.contains(p.getId())) detachProjectListeners(p.getId());
        }

        target.setAll(next);

        for (Project p : next) attachProjectListeners(p);
    }

    private void applyProjectDto(Project p, ProjectDto dto, boolean history) {
        p.setName(safe(dto.name()));
        p.setDescription(safe(dto.description()));
        p.setStakeholders(safe(dto.stakeholders()));
        p.setPhaseTemplate(safe(dto.phaseTemplate()));
        if (dto.health() != null) p.setHealth(dto.health());
        if (dto.startDate() != null) p.setStartDate(dto.startDate());
        if (dto.endDate() != null) p.setEndDate(dto.endDate());
        if (dto.completedDate() != null) p.setCompletedDate(dto.completedDate());

        Project.ProjectStatus status = dto.status();
        if (status == null) status = history ? Project.ProjectStatus.DONE : Project.ProjectStatus.ACTIVE;
        p.setStatus(status);

        Map<String, Member> members = applyMembers(p, dto.members());
        Map<String, Phase> phases = applyPhases(p, dto.phases());
        applyTasks(p, dto.tasks(), members, phases);
        applyMilestones(p, dto.milestones());
    }

    private Map<String, Member> applyMembers(Project p, List<MemberDto> list) {
        Map<String, Member> map = new HashMap<>();
        List<Member> next = new ArrayList<>();
        if (list != null) {
            for (MemberDto dto : list) {
                if (dto == null || dto.id() == null) continue;
                ProjectRole role = dto.role() == null ? ProjectRole.MEMBER : dto.role();
                Member m = new Member(dto.id(), safe(dto.name()), role);
                next.add(m);
                map.put(m.getId(), m);
            }
        }
        p.getMembers().setAll(next);
        return map;
    }

    private Map<String, Phase> applyPhases(Project p, List<PhaseDto> list) {
        Map<String, Phase> map = new HashMap<>();
        List<Phase> next = new ArrayList<>();
        if (list != null) {
            for (PhaseDto dto : list) {
                if (dto == null || dto.id() == null) continue;
                Phase ph = new Phase(dto.id(), safe(dto.name()));
                if (dto.start() != null) ph.startProperty().set(dto.start());
                if (dto.end() != null) ph.endProperty().set(dto.end());
                next.add(ph);
                map.put(ph.getId(), ph);
            }
        }
        p.getPhases().setAll(next);
        return map;
    }

    private void applyTasks(Project p, List<TaskDto> list, Map<String, Member> members, Map<String, Phase> phases) {
        List<Task> next = new ArrayList<>();
        if (list != null) {
            for (TaskDto dto : list) {
                if (dto == null || dto.id() == null) continue;
                Task t = new Task(dto.id(), safe(dto.title()));
                t.setDescription(safe(dto.description()));
                t.setStatus(dto.status() == null ? TaskStatus.TODO : dto.status());
                t.setPriority(dto.priority() == null ? Priority.MEDIUM : dto.priority());
                t.setDueDate(dto.dueDate());
                if (dto.assigneeId() != null && members != null) t.setAssignee(members.get(dto.assigneeId()));
                if (dto.phaseId() != null && phases != null) t.setPhase(phases.get(dto.phaseId()));
                next.add(t);
            }
        }
        p.getTasks().setAll(next);
    }

    private void applyMilestones(Project p, List<MilestoneDto> list) {
        List<Milestone> next = new ArrayList<>();
        if (list != null) {
            for (MilestoneDto dto : list) {
                if (dto == null || dto.id() == null) continue;
                Milestone ms = new Milestone(dto.id(), safe(dto.title()));
                ms.dueDateProperty().set(dto.dueDate());
                ms.completedProperty().set(dto.done());
                next.add(ms);
            }
        }
        p.getMilestones().setAll(next);
    }

    private void applyActivity(List<ActivityDto> list) {
        getActivity().clear();
        if (list == null) return;
        for (ActivityDto dto : list) {
            if (dto == null) continue;
            ActivityItem it = new ActivityItem(safe(dto.projectName()), safe(dto.message()));
            if (dto.time() != null) it.timeProperty().set(dto.time());
            getActivity().add(it);
        }
    }

    @Override
    protected String safe(String v) {
        return v == null ? "" : v;
    }
}
