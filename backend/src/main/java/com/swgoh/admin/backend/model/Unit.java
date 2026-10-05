package com.swgoh.admin.backend.model;

import java.util.List;

/**
 * One roster unit. Comlink's raw player data carries no per-unit power, so
 * {@code power} is a derived strength score (see PlayerMapper#strength)
 * built from rarity/gear/relic -- good for ranking, not real in-game GP.
 * relicTier is the real relic level (0 = R0 or no relic); it's only ever
 * non-zero for G13 characters. Ships have no gear or relic. tags are the
 * unit's game-data categories (profession_jedi, alignment_light, ...).
 */
public record Unit(
        String baseId,
        String name,
        int power,
        int rarity,
        int gearLevel,
        int relicTier,
        boolean ship,
        List<String> tags
) {
    public boolean capitalShip() {
        return ship && tags.contains("role_capital");
    }

    public String levelLabel() {
        if (ship) {
            return rarity + "★ ship";
        }
        return gearLevel >= 13
                ? rarity + "★ G13 R" + relicTier
                : rarity + "★ G" + gearLevel;
    }
}
