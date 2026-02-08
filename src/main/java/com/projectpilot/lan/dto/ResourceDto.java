package com.projectpilot.lan.dto;

import com.projectpilot.model.enums.ResourceType;

import java.time.LocalDateTime;

public record ResourceDto(
        String id,
        String taskId,
        ResourceType type,
        String title,
        String target,
        String notes,
        String addedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
