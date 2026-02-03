package com.projectpilot.data.db.auth;

public record UserAccount(
        long id,
        String email,
        String username,
        GlobalRole globalRole
) {
    @Override public String toString() {
        if (username != null && !username.isBlank()) return username;
        return email == null ? ("User#" + id) : email;
    }
}
