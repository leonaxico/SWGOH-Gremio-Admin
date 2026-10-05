package com.swgoh.admin.backend.model;

public record Assignment(
        String missionId,
        String missionName,
        String allyCode,
        String playerName,
        String unitBaseId,
        String unitName,
        int unitPower,
        double priorityScore
) {}
