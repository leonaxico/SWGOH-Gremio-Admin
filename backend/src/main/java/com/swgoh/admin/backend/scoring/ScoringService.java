package com.swgoh.admin.backend.scoring;

import com.swgoh.admin.backend.model.MissionRequirement;
import com.swgoh.admin.backend.model.Unit;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Decides whether a unit fits a mission and builds a squad from a pool of
 * units.
 * <p>
 * Requirement semantics, taken from campaign entryCategoryAllowed (every
 * live TB mission uses matchType 2, i.e. "any of the listed categories"):
 * <ul>
 *   <li>a filler unit needs at least one of requiredCategories and none of
 *       excludedCategories;</li>
 *   <li>every unit -- mandatory ones included -- must meet the star floor,
 *       and characters the gear/relic floor;</li>
 *   <li>mandatoryUnits must all be in the squad and count toward minUnits;</li>
 *   <li>fleet missions with commanderCategories need a capital ship from one
 *       of them; it also counts toward minUnits.</li>
 * </ul>
 */
@Service
public class ScoringService {

    /** Unit meets the star/gear/relic floors and isn't excluded. Doesn't check categories. */
    public boolean meetsFloors(Unit unit, MissionRequirement req) {
        if (unit.ship() != req.ship() || unit.rarity() < req.minRarity()) {
            return false;
        }
        if (!unit.ship() && (unit.gearLevel() < req.minGearLevel() || unit.relicTier() < req.minRelic())) {
            return false;
        }
        return unit.tags().stream().noneMatch(req.excludedCategories()::contains);
    }

    /** Unit could fill a regular (non-mandatory, non-commander) squad slot. */
    public boolean qualifies(Unit unit, MissionRequirement req) {
        if (!meetsFloors(unit, req) || (req.ship() && !req.commanderCategories().isEmpty() && unit.capitalShip())) {
            return false;
        }
        return hasAny(unit, req.requiredCategories());
    }

    /**
     * Builds the cheapest valid squad: mandatory units, then a commander
     * (fleet), then fillers in {@code preference} order until minUnits is
     * reached. Empty when the pool can't satisfy the mission.
     */
    public Optional<List<Unit>> buildSquad(Collection<Unit> pool, MissionRequirement req, Comparator<Unit> preference) {
        Map<String, Unit> byId = pool.stream().collect(Collectors.toMap(Unit::baseId, Function.identity()));
        List<Unit> squad = new ArrayList<>();

        for (String id : req.mandatoryUnits()) {
            Unit unit = byId.get(id);
            if (unit == null || !meetsFloors(unit, req)) {
                return Optional.empty();
            }
            squad.add(unit);
        }

        if (req.ship() && !req.commanderCategories().isEmpty() && squad.stream().noneMatch(Unit::capitalShip)) {
            Optional<Unit> commander = pool.stream()
                    .filter(Unit::capitalShip)
                    .filter(u -> meetsFloors(u, req) && hasAny(u, req.commanderCategories()))
                    .min(preference);
            if (commander.isEmpty()) {
                return Optional.empty();
            }
            squad.add(commander.get());
        }

        List<Unit> fillers = pool.stream()
                .filter(u -> !squad.contains(u) && qualifies(u, req))
                .sorted(preference)
                .toList();
        for (Unit unit : fillers) {
            if (squad.size() >= req.minUnits()) {
                break;
            }
            squad.add(unit);
        }

        return squad.size() >= req.minUnits() ? Optional.of(squad) : Optional.empty();
    }

    private static boolean hasAny(Unit unit, List<String> categories) {
        return categories.isEmpty() || unit.tags().stream().anyMatch(categories::contains);
    }
}
