package com.swgoh.admin.frontend.dto;

import java.util.List;

public record AssignmentDto(
        String missionId,
        String missionName,
        String allyCode,
        String playerName,
        List<SquadUnitDto> squad,
        double priorityScore
) {}
