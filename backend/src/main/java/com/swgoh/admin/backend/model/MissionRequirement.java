package com.swgoh.admin.backend.model;

import java.util.List;

/**
 * One TB mission, resolved from Comlink game data: the TB zone points at a
 * campaign mission, whose entryCategoryAllowed is the squad requirement.
 * <p>
 * A unit fits the squad when it has at least one of requiredCategories,
 * none of excludedCategories, and meets the star / gear / relic floors
 * (gear and relic only apply to characters). Every unit in mandatoryUnits
 * must be in the squad. Fleet missions with commanderCategories also need a
 * capital ship from one of those categories. minRelic is the real relic
 * level (5 = R5); minGearLevel is 13 whenever a relic floor applies.
 */
public record MissionRequirement(
        String missionId,
        String phase,
        String territory,
        String name,
        String requirementText,
        Type type,
        boolean bonus,
        List<String> requiredCategories,
        List<String> excludedCategories,
        List<String> commanderCategories,
        List<String> mandatoryUnits,
        int minUnits,
        int maxUnits,
        int minRarity,
        int minGearLevel,
        int minRelic
) {
    public enum Type { COMBAT, SPECIAL, FLEET }

    public boolean ship() {
        return type == Type.FLEET;
    }
}
