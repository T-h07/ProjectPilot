package com.projectpilot.chat;

public record ChatThread(
        String id,
        ChatType type,
        String title,
        String subtitle,
        String teamId,
        String otherUserId,
        Long lastAt
) {
}
