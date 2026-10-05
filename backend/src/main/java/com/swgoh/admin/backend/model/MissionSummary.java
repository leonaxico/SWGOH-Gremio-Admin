package com.swgoh.admin.backend.model;

/**
 * Per-mission outcome of a phase optimization. eligiblePlayers counts who
 * could field a squad with their whole roster; assignedPlayers counts who
 * still can once units are shared out across the phase's missions.
 */
public record MissionSummary(
        String missionId,
        String name,
        String territory,
        MissionRequirement.Type type,
        boolean bonus,
        String requirement,
        int eligiblePlayers,
        int assignedPlayers
) {}
