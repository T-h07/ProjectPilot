package com.projectpilot.lan.dto;

public record SyncAction(
        SyncType type,
        String projectId,
        String entityId,
        ProjectDto project,
        TaskDto task,
        MemberDto member,
        PhaseDto phase,
        MilestoneDto milestone,
        ResourceDto resource,
        NoteDto note
) {}
