package com.projectpilot.data.db.auth;

public interface AuthProvider {
    boolean needsInitialAdmin();
    UserSession login(String username, String password);
    UserSession createInitialAdmin(String displayName, String username, String email, String password);
}
