package com.projectpilot.chat;

public record ChatMessage(
        String id,
        String threadId,
        String senderId,
        String senderName,
        String body,
        long createdAt
) {
}
