package com.swgoh.admin.backend.loader;

import com.fasterxml.jackson.databind.JsonNode;
import com.swgoh.admin.backend.gamedata.GameDataService;
import com.swgoh.admin.backend.gamedata.UnitInfo;
import com.swgoh.admin.backend.model.Player;
import com.swgoh.admin.backend.model.Unit;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps raw Comlink /player JSON -> Player/Unit.
 * <p>
 * Verified against swgoh-comlink 4.5.0 (see debug/comlink_dump.sh):
 * <ul>
 *   <li>Player GP lives in profileStat[] under
 *       STAT_GALACTIC_POWER_ACQUIRED_NAME, as a string.</li>
 *   <li>rosterUnit[].definitionId is "BASEID:SEVEN_STAR" -- the suffix
 *       duplicates currentRarity.</li>
 *   <li>currentTier is the gear level (always 1 for ships).</li>
 *   <li>relic is null for ships. For characters relic.currentTier is
 *       offset by 2 (1 = locked, 2 = R0, 3 = R1 ...) and only meaningful
 *       at G13.</li>
 *   <li>No per-unit power, name or category tags are present in this
 *       payload (unitStat is always null) -- name and categories come from
 *       the units game-data collection via GameDataService.</li>
 * </ul>
 */
@Component
public class PlayerMapper {

    private static final String GP_STAT = "STAT_GALACTIC_POWER_ACQUIRED_NAME";
    private static final int RELIC_TIER_OFFSET = 2;
    private static final Map<String, Integer> STAR_SUFFIX = Map.of(
            "ONE_STAR", 1, "TWO_STAR", 2, "THREE_STAR", 3, "FOUR_STAR", 4,
            "FIVE_STAR", 5, "SIX_STAR", 6, "SEVEN_STAR", 7
    );

    private final GameDataService gameData;

    public PlayerMapper(GameDataService gameData) {
        this.gameData = gameData;
    }

    public Player fromRaw(JsonNode raw) {
        String allyCode = raw.path("allyCode").asText("");
        String name = raw.path("name").asText(allyCode);
        int totalGp = extractGp(raw);

        Map<String, Unit> units = new LinkedHashMap<>();
        JsonNode roster = raw.path("rosterUnit");
        if (roster.isArray()) {
            for (JsonNode rawUnit : roster) {
                String[] defId = rawUnit.path("definitionId").asText("").split(":");
                String baseId = defId[0];
                if (baseId.isBlank()) {
                    continue;
                }
                boolean ship = rawUnit.path("relic").isNull() || rawUnit.path("relic").isMissingNode();
                int rarity = rawUnit.path("currentRarity").asInt(
                        defId.length > 1 ? STAR_SUFFIX.getOrDefault(defId[1], 0) : 0);
                int gear = ship ? 0 : rawUnit.path("currentTier").asInt(0);
                int relic = (ship || gear < 13) ? 0
                        : Math.max(0, rawUnit.path("relic").path("currentTier").asInt(0) - RELIC_TIER_OFFSET);

                UnitInfo info = gameData.unit(baseId).orElse(null);
                units.put(baseId, new Unit(
                        baseId,
                        info != null ? info.name() : baseId,
                        strength(ship, rarity, gear, relic),
                        rarity,
                        gear,
                        relic,
                        ship,
                        info != null ? List.copyOf(info.categories()) : List.of()
                ));
            }
        }

        return new Player(allyCode, name, totalGp, units);
    }

    private int extractGp(JsonNode raw) {
        for (JsonNode stat : raw.path("profileStat")) {
            if (GP_STAT.equals(stat.path("nameKey").asText())) {
                try {
                    return (int) Long.parseLong(stat.path("value").asText("0"));
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }

    /**
     * Ranking proxy, not in-game GP. Gear dominates, each relic level beyond
     * G13 is worth more than a gear level, stars break ties. Ships only have
     * stars to go on, so they're only comparable with other ships.
     */
    private int strength(boolean ship, int rarity, int gear, int relic) {
        if (ship) {
            return rarity * 1000;
        }
        return gear * 1000 + relic * 1500 + rarity * 100;
    }
}
