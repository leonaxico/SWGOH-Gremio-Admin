package com.swgoh.admin.frontend.dto;

public record MissionSummaryDto(
        String missionId,
        String name,
        String territory,
        String type,
        boolean bonus,
        String requirement,
        int eligiblePlayers,
        int assignedPlayers
) {}
