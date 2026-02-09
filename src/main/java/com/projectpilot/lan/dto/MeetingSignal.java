package com.projectpilot.lan.dto;

public record MeetingSignal(
        String type,
        String room,
        String from,
        String to,
        String name,
        String payload
) {}
