package com.projectpilot.lan.dto;

import com.projectpilot.data.db.auth.UserSession;

public record LoginResponse(
        String token,
        UserSession session
) {}
