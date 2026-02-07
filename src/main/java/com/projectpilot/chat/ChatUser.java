package com.projectpilot.chat;

public record ChatUser(String id, String displayName) {
    @Override
    public String toString() {
        if (displayName != null && !displayName.isBlank()) return displayName;
        return id == null ? "" : id;
    }
}
