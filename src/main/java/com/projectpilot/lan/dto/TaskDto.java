package com.projectpilot.lan.dto;

import com.projectpilot.model.enums.Priority;
import com.projectpilot.model.enums.TaskStatus;

import java.time.LocalDate;

public record TaskDto(
        String id,
        String title,
        String description,
        TaskStatus status,
        Priority priority,
        LocalDate dueDate,
        String assigneeId,
        String phaseId
) {}
