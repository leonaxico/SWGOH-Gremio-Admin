package com.swgoh.admin.backend.loader;

import com.fasterxml.jackson.databind.JsonNode;
import com.swgoh.admin.backend.model.Player;
import com.swgoh.admin.backend.model.Unit;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps raw Comlink JSON -> Player/Unit.
 * <p>
 * IMPORTANT: Comlink's raw roster units don't ship a single obvious "power"
 * field name across all client libraries -- field naming has moved around
 * (examples seen in the wild: stats.power, power, unit.stats.power).
 * extractPower tries a few likely paths and falls back to a gear/relic-based
 * proxy so the pipeline never silently breaks. Inspect one raw roster entry
 * against your own Comlink instance and adjust if it uses something else
 * (see README "verify the data shape").
 */
@Component
public class PlayerMapper {

    public Player fromRaw(JsonNode raw) {
        String allyCode = raw.path("allyCode").asText("");
        String name = raw.path("name").asText(allyCode);
        int totalGp = raw.path("gp").asInt(0);

        Map<String, Unit> units = new LinkedHashMap<>();
        JsonNode roster = raw.path("rosterUnit");
        if (!roster.isArray()) {
            roster = raw.path("roster");
        }
        if (roster.isArray()) {
            for (JsonNode rawUnit : roster) {
                String baseId = extractBaseId(rawUnit);
                if (baseId == null || baseId.isBlank()) {
                    continue;
                }
                units.put(baseId, new Unit(
                        baseId,
                        rawUnit.path("name").asText(baseId),
                        extractPower(rawUnit),
                        rawUnit.path("currentRarity").asInt(rawUnit.path("rarity").asInt(0)),
                        rawUnit.path("currentTier").asInt(rawUnit.path("gearLevel").asInt(0)),
                        rawUnit.path("relic").path("currentTier").asInt(0),
                        extractTags(rawUnit)
                ));
            }
        }

        return new Player(allyCode, name, totalGp, units);
    }

    private String extractBaseId(JsonNode rawUnit) {
        String defId = rawUnit.path("definitionId").asText("");
        if (!defId.isBlank()) {
            return defId.split(":")[0];
        }
        if (rawUnit.hasNonNull("baseId")) {
            return rawUnit.path("baseId").asText();
        }
        if (rawUnit.hasNonNull("id")) {
            return rawUnit.path("id").asText();
        }
        return null;
    }

    private int extractPower(JsonNode rawUnit) {
        JsonNode power = rawUnit.path("stats").path("power");
        if (power.isMissingNode()) {
            power = rawUnit.path("power");
        }
        if (power.isMissingNode()) {
            power = rawUnit.path("unit").path("stats").path("power");
        }
        if (!power.isMissingNode() && power.isNumber() && power.asInt() > 0) {
            return power.asInt();
        }
        // Fallback proxy: rough weighting by gear + relic. Not real GP, but
        // keeps relative ranking usable until a real power source is wired in.
        int gear = rawUnit.path("gear").asInt(rawUnit.path("gearLevel").asInt(0));
        int relic = rawUnit.path("relic").path("currentTier").asInt(0);
        return gear * 1000 + relic * 2000;
    }

    private List<String> extractTags(JsonNode rawUnit) {
        List<String> tags = new ArrayList<>();
        for (String key : new String[]{"categoryIds", "categories", "tags"}) {
            JsonNode node = rawUnit.path(key);
            if (node.isArray()) {
                node.forEach(n -> tags.add(n.asText()));
            }
        }
        return tags;
    }
}
