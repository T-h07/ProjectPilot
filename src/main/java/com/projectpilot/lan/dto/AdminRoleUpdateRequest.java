package com.projectpilot.lan.dto;

import com.projectpilot.model.enums.ProjectRole;

public record AdminRoleUpdateRequest(String projectId, String userId, ProjectRole role) {}
