package com.swgoh.admin.backend.optimizer;

import com.swgoh.admin.backend.model.Assignment;
import com.swgoh.admin.backend.model.Guild;
import com.swgoh.admin.backend.model.MissionRequirement;
import com.swgoh.admin.backend.model.MissionSummary;
import com.swgoh.admin.backend.model.Player;
import com.swgoh.admin.backend.model.SquadUnit;
import com.swgoh.admin.backend.model.Unit;
import com.swgoh.admin.backend.scoring.ScoringService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Plans one TB phase. In a TB every player may attempt every mission once,
 * but a unit used in one mission is spent for the rest of the phase -- so
 * the problem is, per player, which missions to spend which units on.
 * <ol>
 *   <li>Dependency: for each mission count the players who could field a
 *       squad with their full roster. Fewer eligible players = the guild
 *       depends more on each of them = higher priority (1 / eligible).</li>
 *   <li>Per player, walk missions in priority order and build each squad
 *       from the units still available, preferring units that fit the fewest
 *       of the player's remaining missions (then the strongest), so scarce
 *       units aren't burned on missions anything could fill.</li>
 * </ol>
 * Units already spent on platoons (see PlatoonPlanner) are left out of step 2.
 * Missions nobody can attempt are reported with 0 eligible players.
 */
@Service
public class OptimizerService {

    private final ScoringService scoringService;

    public OptimizerService(ScoringService scoringService) {
        this.scoringService = scoringService;
    }

    public record Result(List<MissionSummary> missions, List<Assignment> assignments) {}

    /**
     * @param reserved allyCode -> baseIds already spent this phase (platoons);
     *                 they still count for eligibility but aren't planned into squads.
     */
    public Result optimizePhase(Guild guild, List<MissionRequirement> missions, Map<String, Set<String>> reserved) {
        Comparator<Unit> strongestFirst = Comparator.comparingInt(Unit::power).reversed();

        Map<String, Integer> eligible = new HashMap<>();
        for (MissionRequirement m : missions) {
            int count = 0;
            for (Player p : guild.getPlayers().values()) {
                if (scoringService.buildSquad(p.getUnits().values(), m, strongestFirst).isPresent()) {
                    count++;
                }
            }
            eligible.put(m.missionId(), count);
        }

        List<MissionRequirement> ordered = missions.stream()
                .filter(m -> eligible.get(m.missionId()) > 0)
                .sorted(Comparator.<MissionRequirement>comparingInt(m -> eligible.get(m.missionId()))
                        .thenComparing(m -> m.type() != MissionRequirement.Type.SPECIAL))
                .toList();

        List<Assignment> assignments = new ArrayList<>();
        Map<String, Integer> assigned = new HashMap<>();
        for (Player player : guild.getPlayers().values()) {
            Map<String, Unit> available = new LinkedHashMap<>(player.getUnits());
            reserved.getOrDefault(player.getAllyCode(), Set.of()).forEach(available::remove);
            for (int i = 0; i < ordered.size(); i++) {
                MissionRequirement mission = ordered.get(i);
                List<MissionRequirement> remaining = ordered.subList(i + 1, ordered.size());
                Comparator<Unit> preference = Comparator
                        .<Unit>comparingLong(u -> remaining.stream().filter(r -> scoringService.qualifies(u, r)).count())
                        .thenComparing(strongestFirst);

                Optional<List<Unit>> squad = scoringService.buildSquad(available.values(), mission, preference);
                if (squad.isEmpty()) {
                    continue;
                }
                squad.get().forEach(u -> available.remove(u.baseId()));
                assigned.merge(mission.missionId(), 1, Integer::sum);
                assignments.add(new Assignment(
                        mission.missionId(),
                        mission.name(),
                        player.getAllyCode(),
                        player.getName(),
                        squad.get().stream().map(SquadUnit::of).toList(),
                        Math.round(10000.0 / eligible.get(mission.missionId())) / 10000.0
                ));
            }
        }

        List<MissionSummary> summaries = missions.stream()
                .map(m -> new MissionSummary(
                        m.missionId(), m.name(), m.territory(), m.type(), m.bonus(), m.requirementText(),
                        eligible.get(m.missionId()), assigned.getOrDefault(m.missionId(), 0)))
                .toList();

        return new Result(summaries, assignments);
    }
}
