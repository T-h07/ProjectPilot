package com.projectpilot.lan.dto;

import com.projectpilot.data.db.auth.GlobalRole;

public record AdminUpdateUserRequest(
        String id,
        String displayName,
        String username,
        String email,
        String newPassword,
        GlobalRole globalRole,
        boolean active
) {}
