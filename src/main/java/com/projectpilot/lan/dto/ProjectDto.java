package com.projectpilot.lan.dto;

import com.projectpilot.model.Project;
import com.projectpilot.model.enums.ProjectHealth;

import java.time.LocalDate;
import java.util.List;

public record ProjectDto(
        String id,
        String name,
        String description,
        String stakeholders,
        String phaseTemplate,
        ProjectHealth health,
        Project.ProjectStatus status,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate completedDate,
        List<PhaseDto> phases,
        List<TaskDto> tasks,
        List<MemberDto> members,
        List<MilestoneDto> milestones,
        List<ResourceDto> resources,
        List<NoteDto> notes
) {}
