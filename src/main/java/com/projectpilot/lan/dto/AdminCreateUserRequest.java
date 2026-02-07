package com.projectpilot.lan.dto;

import com.projectpilot.data.db.auth.GlobalRole;

public record AdminCreateUserRequest(
        String displayName,
        String username,
        String email,
        String password,
        GlobalRole globalRole
) {}
