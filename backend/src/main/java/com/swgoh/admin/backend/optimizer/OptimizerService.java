package com.swgoh.admin.backend.optimizer;

import com.swgoh.admin.backend.model.Assignment;
import com.swgoh.admin.backend.model.Guild;
import com.swgoh.admin.backend.model.MissionRequirement;
import com.swgoh.admin.backend.model.Player;
import com.swgoh.admin.backend.model.Unit;
import com.swgoh.admin.backend.scoring.ScoringService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Greedy assignment: players -> mission requirement slots.
 * <p>
 * Sorts every (requirement, candidate player) pair by a priority score
 * (dependency score dominant, roster share as a tiebreaker), then walks
 * the sorted list assigning players to open slots -- each player deploys
 * to at most one mission for the phase being optimized.
 */
@Service
public class OptimizerService {

    private final ScoringService scoringService;

    public OptimizerService(ScoringService scoringService) {
        this.scoringService = scoringService;
    }

    public record Result(List<Assignment> assignments, List<UnfilledSlot> unfilled) {}

    public record UnfilledSlot(String missionId, String missionName, int shortBy) {}

    private record Candidate(double priority, MissionRequirement req, String allyCode, Unit unit) {}

    public Result optimizePhase(Guild guild, List<MissionRequirement> missions) {
        List<Candidate> candidates = new ArrayList<>();

        for (MissionRequirement req : missions) {
            Map<String, Double> depScores = scoringService.dependencyScores(guild, req);
            Map<String, List<Unit>> owners = scoringService.viableOwners(guild, req);

            for (var entry : owners.entrySet()) {
                String allyCode = entry.getKey();
                Player player = guild.getPlayers().get(allyCode);
                Unit bestUnit = entry.getValue().stream()
                        .max(Comparator.comparingInt(Unit::power))
                        .orElseThrow();
                double share = scoringService.rosterShare(player.avgRosterPower(), bestUnit.power());
                double priority = depScores.getOrDefault(allyCode, 0.0) * (1 + Math.min(share, 3.0) / 10);
                candidates.add(new Candidate(priority, req, allyCode, bestUnit));
            }
        }

        candidates.sort((a, b) -> Double.compare(b.priority(), a.priority()));

        Set<String> usedPlayers = new HashSet<>();
        Map<String, Integer> slotsFilled = new HashMap<>();
        missions.forEach(m -> slotsFilled.put(m.missionId(), 0));

        List<Assignment> assignments = new ArrayList<>();
        for (Candidate c : candidates) {
            if (usedPlayers.contains(c.allyCode())) {
                continue;
            }
            if (slotsFilled.get(c.req().missionId()) >= c.req().minCount()) {
                continue;
            }

            Player player = guild.getPlayers().get(c.allyCode());
            assignments.add(new Assignment(
                    c.req().missionId(),
                    c.req().name(),
                    c.allyCode(),
                    player.getName(),
                    c.unit().baseId(),
                    c.unit().name(),
                    c.unit().power(),
                    Math.round(c.priority() * 10000.0) / 10000.0
            ));
            usedPlayers.add(c.allyCode());
            slotsFilled.merge(c.req().missionId(), 1, Integer::sum);
        }

        List<UnfilledSlot> unfilled = new ArrayList<>();
        for (MissionRequirement req : missions) {
            int filled = slotsFilled.get(req.missionId());
            if (filled < req.minCount()) {
                unfilled.add(new UnfilledSlot(req.missionId(), req.name(), req.minCount() - filled));
            }
        }

        return new Result(assignments, unfilled);
    }
}
