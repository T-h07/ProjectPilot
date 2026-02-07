package com.projectpilot.chat;

import java.util.List;

public interface ChatService {
    List<ChatThread> listThreads(String memberId);
    List<ChatUser> listUsers(String excludeMemberId);
    ChatThread getOrCreateDirect(String memberId, String otherMemberId);
    List<ChatMessage> listMessages(String threadId, String viewerId, int limit);
    ChatMessage sendMessage(String threadId, String senderId, String body);
}
