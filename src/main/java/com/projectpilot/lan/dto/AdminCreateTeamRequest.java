package com.projectpilot.lan.dto;

import com.projectpilot.data.db.TeamService;

import java.util.List;

public record AdminCreateTeamRequest(
        String name,
        String leaderId,
        List<TeamService.TeamMemberSpec> members
) {}
