package com.swgoh.admin.backend.config;

import com.swgoh.admin.backend.model.MissionRequirement;
import com.swgoh.admin.backend.model.TBDefinition;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand-maintained Territory Battle requirement definitions.
 * <p>
 * Comlink's game-data dump doesn't give you a clean "this mission needs
 * exactly these characters" list -- combat missions are tag/GP criteria
 * deep in the raw TB definitions, and some point at specific named units.
 * This registry is the source of truth your officers maintain directly,
 * not something auto-derived from game data.
 */
@Component
public class TbRegistry {

    private final Map<String, TBDefinition> tbs = new LinkedHashMap<>();

    public TbRegistry() {
        // EXAMPLE -- replace with your guild's actual TB and current mission
        // requirements. Structural example only, not verified live data;
        // confirm tags/unit ids/GP thresholds against your own TB screen.
        TBDefinition example = new TBDefinition(
                "rote",
                "Rise of the Empire (example)",
                List.of("phase_1", "phase_2", "phase_3", "phase_4"),
                List.of(
                        new MissionRequirement(
                                "p1_empire_combat_1", "phase_1", "Empire Combat Mission 1",
                                List.of("Empire"), List.of(), 5, 8000
                        ),
                        new MissionRequirement(
                                "p1_rebel_combat_1", "phase_1", "Rebel Combat Mission 1",
                                List.of("Rebel"), List.of(), 5, 8000
                        ),
                        new MissionRequirement(
                                "p2_special_vader", "phase_2", "Special Mission - Vader Required",
                                List.of(), List.of("DARTHVADER"), 1, 0
                        )
                )
        );
        tbs.put(example.tbId(), example);
    }

    public List<TBDefinition> list() {
        return List.copyOf(tbs.values());
    }

    public TBDefinition get(String tbId) {
        TBDefinition tb = tbs.get(tbId);
        if (tb == null) {
            throw new IllegalArgumentException("Unknown TB id: " + tbId);
        }
        return tb;
    }
}
