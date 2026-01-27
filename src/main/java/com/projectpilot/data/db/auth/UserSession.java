package com.projectpilot.data.db.auth;

public record UserSession(String id, String username, String displayName, GlobalRole globalRole) {}
