package com.projectpilot.lan;

import com.projectpilot.data.InMemoryStore;
import com.projectpilot.lan.dto.*;
import com.projectpilot.model.*;
import com.projectpilot.util.ChecklistCodec;

import java.util.ArrayList;
import java.util.List;

public final class LanMapper {

    private LanMapper() {}

    public static SnapshotDto toSnapshot(InMemoryStore store) {
        List<ProjectDto> projects = new ArrayList<>();
        for (Project p : safeProjects(store.getProjects())) {
            projects.add(toProjectDto(p));
        }

        List<ProjectDto> history = new ArrayList<>();
        for (Project p : safeProjects(store.getHistoryProjects())) {
            history.add(toProjectDto(p));
        }

        List<ActivityDto> activity = new ArrayList<>();
        for (ActivityItem it : safeActivity(store.getActivity())) {
            activity.add(new ActivityDto(
                    it.getProjectId(),
                    it.getProjectName(),
                    it.getActor(),
                    it.getEntityType(),
                    it.getEntityId(),
                    it.getAction(),
                    it.getMessage(),
                    it.getTime()
            ));
        }

        return new SnapshotDto(projects, history, activity);
    }

    public static ProjectDto toProjectDto(Project p) {
        if (p == null) return null;

        List<PhaseDto> phases = new ArrayList<>();
        if (p.getPhases() != null) {
            for (int i = 0; i < p.getPhases().size(); i++) {
                Phase ph = p.getPhases().get(i);
                if (ph == null) continue;
                phases.add(new PhaseDto(ph.getId(), ph.getName(), ph.startProperty().get(), ph.endProperty().get(), i));
            }
        }

        List<MemberDto> members = new ArrayList<>();
        if (p.getMembers() != null) {
            for (Member m : p.getMembers()) {
                if (m == null) continue;
                members.add(new MemberDto(m.getId(), m.getName(), m.getRole()));
            }
        }

        List<TaskDto> tasks = new ArrayList<>();
        if (p.getTasks() != null) {
            for (Task t : p.getTasks()) {
                if (t == null) continue;
                String assigneeId = t.getAssignee() == null ? null : t.getAssignee().getId();
                String phaseId = t.getPhase() == null ? null : t.getPhase().getId();
                tasks.add(new TaskDto(
                        t.getId(),
                        t.getTitle(),
                        t.getDescription(),
                        t.getStatus(),
                        t.getPriority(),
                        t.getDueDate(),
                        assigneeId,
                        phaseId,
                        ChecklistCodec.encode(t.getChecklist())
                ));
            }
        }

        List<MilestoneDto> milestones = new ArrayList<>();
        if (p.getMilestones() != null) {
            for (Milestone ms : p.getMilestones()) {
                if (ms == null) continue;
                milestones.add(new MilestoneDto(
                        ms.getId(),
                        ms.nameProperty().get(),
                        ms.dueDateProperty().get(),
                        ms.completedProperty().get()
                ));
            }
        }

        List<ResourceDto> resources = new ArrayList<>();
        if (p.getResources() != null) {
            for (ResourceItem r : p.getResources()) {
                if (r == null) continue;
                resources.add(new ResourceDto(
                        r.getId(),
                        r.getTaskId(),
                        r.getType(),
                        r.getTitle(),
                        r.getTarget(),
                        r.getNotes(),
                        r.getAddedBy(),
                        r.getCreatedAt(),
                        r.getUpdatedAt()
                ));
            }
        }

        List<NoteDto> notes = new ArrayList<>();
        if (p.getNotes() != null) {
            for (PersonalNote note : p.getNotes()) {
                if (note == null) continue;
                notes.add(new NoteDto(
                        note.getId(),
                        note.getTaskId(),
                        note.getOwnerId(),
                        note.getTitle(),
                        note.getBody(),
                        note.getCreatedAt(),
                        note.getUpdatedAt()
                ));
            }
        }

        return new ProjectDto(
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
                phases,
                tasks,
                members,
                milestones,
                resources,
                notes
        );
    }

    private static List<Project> safeProjects(List<Project> list) {
        return list == null ? List.of() : list;
    }

    private static List<ActivityItem> safeActivity(List<ActivityItem> list) {
        return list == null ? List.of() : list;
    }
}
