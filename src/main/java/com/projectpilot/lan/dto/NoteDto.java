package com.projectpilot.lan.dto;

import java.time.LocalDateTime;

public record NoteDto(
        String id,
        String taskId,
        String ownerId,
        String title,
        String body,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
