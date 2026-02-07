package com.projectpilot.lan.dto;

import com.projectpilot.model.enums.ProjectRole;

public record DirectoryUserDto(String id, String name, ProjectRole role) {}
