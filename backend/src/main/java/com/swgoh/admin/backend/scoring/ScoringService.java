package com.swgoh.admin.backend.scoring;

import com.swgoh.admin.backend.model.Guild;
import com.swgoh.admin.backend.model.MissionRequirement;
import com.swgoh.admin.backend.model.Player;
import com.swgoh.admin.backend.model.Unit;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-(player, unit) scoring used to drive assignment.
 * <p>
 * dependencyScores: allyCode -> 1 / (# guild members with a viable copy for
 * a given requirement). Higher = fewer alternatives = harder to replace
 * this player for that requirement.
 * rosterShare: unit power / player's own average roster power. Greater
 * than 1 means this unit is a standout piece of their roster.
 */
@Service
public class ScoringService {

    public boolean unitMeetsRequirement(Unit unit, MissionRequirement req) {
        boolean hasSpecific = !req.specificUnits().isEmpty();
        boolean hasTags = !req.requiredTags().isEmpty();
        boolean matchesSpecific = hasSpecific && req.specificUnits().contains(unit.baseId());
        boolean matchesTag = hasTags && unit.tags().stream().anyMatch(req.requiredTags()::contains);

        if (hasSpecific && hasTags) {
            if (!matchesSpecific && !matchesTag) {
                return false;
            }
        } else if (hasSpecific) {
            if (!matchesSpecific) {
                return false;
            }
        } else if (hasTags) {
            if (!matchesTag) {
                return false;
            }
        }

        return unit.power() >= req.minUnitGp();
    }

    public Map<String, List<Unit>> viableOwners(Guild guild, MissionRequirement req) {
        Map<String, List<Unit>> result = new LinkedHashMap<>();
        for (Player player : guild.getPlayers().values()) {
            List<Unit> matches = player.getUnits().values().stream()
                    .filter(u -> unitMeetsRequirement(u, req))
                    .toList();
            if (!matches.isEmpty()) {
                result.put(player.getAllyCode(), matches);
            }
        }
        return result;
    }

    public Map<String, Double> dependencyScores(Guild guild, MissionRequirement req) {
        Map<String, List<Unit>> owners = viableOwners(guild, req);
        if (owners.isEmpty()) {
            return Map.of();
        }
        double base = 1.0 / owners.size();
        Map<String, Double> result = new LinkedHashMap<>();
        owners.keySet().forEach(ac -> result.put(ac, base));
        return result;
    }

    public double rosterShare(double playerAvgPower, int unitPower) {
        if (playerAvgPower <= 0) {
            return 0.0;
        }
        return unitPower / playerAvgPower;
    }
}
