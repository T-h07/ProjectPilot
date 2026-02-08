package com.projectpilot.lan.dto;

import java.time.LocalDateTime;

public record ActivityDto(
        String projectId,
        String projectName,
        String actor,
        String entityType,
        String entityId,
        String action,
        String message,
        LocalDateTime time
) {}
