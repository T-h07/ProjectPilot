package com.projectpilot.lan.dto;

import java.time.LocalDate;

public record MilestoneDto(
        String id,
        String title,
        LocalDate dueDate,
        boolean done
) {}
