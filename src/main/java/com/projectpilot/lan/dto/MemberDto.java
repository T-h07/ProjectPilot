package com.projectpilot.lan.dto;

import com.projectpilot.model.enums.ProjectRole;

public record MemberDto(
        String id,
        String name,
        ProjectRole role
) {}
