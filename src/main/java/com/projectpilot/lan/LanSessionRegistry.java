package com.projectpilot.lan;

import com.projectpilot.data.db.auth.UserSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LanSessionRegistry {

    private final Map<String, UserSession> sessions = new ConcurrentHashMap<>();

    public void put(String token, UserSession session) {
        if (token == null || token.isBlank() || session == null) return;
        sessions.put(token, session);
    }

    public UserSession get(String token) {
        if (token == null || token.isBlank()) return null;
        return sessions.get(token.trim());
    }
}
