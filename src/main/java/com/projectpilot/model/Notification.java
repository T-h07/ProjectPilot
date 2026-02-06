package com.projectpilot.model;

import com.projectpilot.model.enums.NotificationType;

import java.time.LocalDateTime;

public record Notification(
        String id,
        String targetUserId,
        LocalDateTime createdAt,
        NotificationType type,
        String title,
        String body,
        String entityKind,     // "TASK", "PROJECT", or null
        String entityId,       // taskId/projectId
        String actorUserId,    // optional
        LocalDateTime readAt   // null => unread
) {
    public boolean isUnread() { return readAt == null; }
}
