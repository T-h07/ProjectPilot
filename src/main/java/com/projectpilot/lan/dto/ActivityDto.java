package com.projectpilot.lan.dto;

import java.time.LocalDateTime;

public record ActivityDto(
        String projectName,
        String message,
        LocalDateTime time
) {}
