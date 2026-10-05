package com.swgoh.admin.backend.model;

import java.util.List;

/** One player attempting one mission with a specific squad. */
public record Assignment(
        String missionId,
        String missionName,
        String allyCode,
        String playerName,
        List<SquadUnit> squad,
        double priorityScore
) {}
