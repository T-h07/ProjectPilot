package com.projectpilot.lan.dto;

import java.util.List;

public record SnapshotDto(
        List<ProjectDto> projects,
        List<ProjectDto> history,
        List<ActivityDto> activity
) {}
