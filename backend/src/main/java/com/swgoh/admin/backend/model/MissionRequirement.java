package com.swgoh.admin.backend.model;

import java.util.List;

/**
 * One deployable slot (or set of slots, via minCount) in a TB combat
 * mission. Satisfied either by any unit in specificUnits, by minCount
 * units matching any of requiredTags, or both -- combined with minUnitGp
 * as an additional floor on the deployed unit's power.
 */
public record MissionRequirement(
        String missionId,
        String phase,
        String name,
        List<String> requiredTags,
        List<String> specificUnits,
        int minCount,
        int minUnitGp
) {}
