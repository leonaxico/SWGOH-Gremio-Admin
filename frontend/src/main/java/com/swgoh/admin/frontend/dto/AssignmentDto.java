package com.swgoh.admin.frontend.dto;

public record AssignmentDto(
        String missionId,
        String missionName,
        String allyCode,
        String playerName,
        String unitBaseId,
        String unitName,
        int unitPower,
        double priorityScore
) {}
