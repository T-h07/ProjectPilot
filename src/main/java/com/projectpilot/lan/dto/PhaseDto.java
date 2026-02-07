package com.projectpilot.lan.dto;

import java.time.LocalDate;

public record PhaseDto(
        String id,
        String name,
        LocalDate start,
        LocalDate end,
        int sortIndex
) {}
