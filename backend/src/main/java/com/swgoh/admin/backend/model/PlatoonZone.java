package com.swgoh.admin.backend.model;

import java.util.List;

/**
 * One platoon zone of a TB phase, from game data. Every unit placed here must
 * meet the floor (stars; gear/relic for characters). A player may place at
 * most maxUnitsPerPlayer units in the zone. platoonIds are in in-game order
 * (platoon 1..6); which units each platoon needs comes from PlatoonService.
 */
public record PlatoonZone(
        String zoneId,
        String phase,
        String territory,
        boolean bonus,
        boolean ship,
        int minRarity,
        int minGearLevel,
        int minRelic,
        int maxUnitsPerPlayer,
        List<String> platoonIds
) {
    public String floorLabel() {
        if (ship) {
            return minRarity + "★ ships";
        }
        return minRelic > 0 || minGearLevel >= 13
                ? minRarity + "★ R" + minRelic
                : minRarity + "★";
    }
}
