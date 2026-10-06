package com.swgoh.admin.backend.platoon;

import com.swgoh.admin.backend.gamedata.GameDataService;
import com.swgoh.admin.backend.gamedata.UnitInfo;
import com.swgoh.admin.backend.model.FarmTarget;
import com.swgoh.admin.backend.model.Guild;
import com.swgoh.admin.backend.model.PlatoonResult;
import com.swgoh.admin.backend.model.PlatoonSlotResult;
import com.swgoh.admin.backend.model.PlatoonZone;
import com.swgoh.admin.backend.model.Player;
import com.swgoh.admin.backend.model.Unit;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plans the platoons of one TB phase.
 * <p>
 * Rules: a unit placed in a platoon is spent for the phase; a player can
 * place at most maxUnitsPerPlayer units per zone; a platoon only pays out
 * when every slot is filled, so a platoon that can't be completed gets no
 * units at all (they stay free for missions) and its missing slots go to the
 * farm list.
 * <p>
 * Within a platoon the scarcest slots (fewest players able to fill them) are
 * assigned first. A slot goes to the player who has placed the fewest units
 * so far this phase, then to the weakest qualifying copy, to keep strong
 * units for combat missions.
 */
@Service
public class PlatoonPlanner {

    private static final int CLOSEST_SHOWN = 3;

    /** usedUnits: allyCode -> baseIds spent in platoons (feed these to the mission optimizer). */
    public record Plan(List<PlatoonResult> platoons, List<FarmTarget> farm, Map<String, Set<String>> usedUnits) {}

    private record Pick(Player player, Unit unit) {}

    private final GameDataService gameData;

    public PlatoonPlanner(GameDataService gameData) {
        this.gameData = gameData;
    }

    public Plan plan(Guild guild, List<PlatoonZone> zones, PlatoonService.PlatoonSet requirements) {
        Map<String, Set<String>> used = new HashMap<>();
        Map<String, Integer> placedInZone = new HashMap<>();
        Map<String, Player> byPlayerId = new HashMap<>();
        guild.getPlayers().values().forEach(p -> byPlayerId.put(p.getPlayerId(), p));

        List<PlatoonResult> results = new ArrayList<>();
        Map<String, FarmTarget> farm = new LinkedHashMap<>();

        for (PlatoonZone zone : zones) {
            List<List<PlatoonService.Slot>> platoons = requirements.zones().getOrDefault(zone.zoneId(), List.of());
            for (int p = 0; p < platoons.size(); p++) {
                List<PlatoonService.Slot> slots = platoons.get(p);
                if (slots.isEmpty()) {
                    continue;
                }
                Pick[] picks = new Pick[slots.size()];
                boolean[] preFilled = new boolean[slots.size()];
                List<Integer> missing = new ArrayList<>();

                // Slots already placed in game are spent regardless of completion.
                for (int i = 0; i < slots.size(); i++) {
                    Player owner = slots.get(i).filledByPlayerId() == null ? null
                            : byPlayerId.get(slots.get(i).filledByPlayerId());
                    if (owner != null) {
                        preFilled[i] = true;
                        picks[i] = new Pick(owner, owner.getUnits().get(slots.get(i).baseId()));
                        spend(used, placedInZone, zone, owner, slots.get(i).baseId());
                    }
                }

                List<Integer> open = new ArrayList<>();
                for (int i = 0; i < slots.size(); i++) {
                    if (!preFilled[i]) {
                        open.add(i);
                    }
                }
                open.sort(Comparator.comparingInt(i -> candidates(guild, zone, slots.get(i).baseId(), used, placedInZone).size()));

                for (int i : open) {
                    String baseId = slots.get(i).baseId();
                    Pick pick = candidates(guild, zone, baseId, used, placedInZone).stream()
                            .min(Comparator.<Pick>comparingInt(c -> used.getOrDefault(c.player().getAllyCode(), Set.of()).size())
                                    .thenComparingInt(c -> c.unit().power()))
                            .orElse(null);
                    if (pick == null) {
                        missing.add(i);
                        continue;
                    }
                    picks[i] = pick;
                    spend(used, placedInZone, zone, pick.player(), baseId);
                }

                boolean complete = missing.isEmpty();
                if (!complete) {
                    // Incomplete platoon pays nothing: give back the units we tentatively placed.
                    for (int i : open) {
                        if (picks[i] != null) {
                            unspend(used, placedInZone, zone, picks[i].player(), slots.get(i).baseId());
                            picks[i] = null;
                        }
                    }
                }

                List<PlatoonSlotResult> slotResults = new ArrayList<>();
                for (int i = 0; i < slots.size(); i++) {
                    String baseId = slots.get(i).baseId();
                    boolean isMissing = missing.contains(i);
                    List<String> closest = isMissing ? closest(guild, zone, baseId) : List.of();
                    slotResults.add(new PlatoonSlotResult(baseId, unitName(guild, baseId),
                            picks[i] == null ? null : picks[i].player().getName(), preFilled[i], isMissing, closest));
                    if (isMissing) {
                        farm.merge(baseId + "|" + zone.floorLabel(),
                                new FarmTarget(baseId, unitName(guild, baseId), zone.floorLabel(), zone.phase(), 1, closest),
                                (a, b) -> new FarmTarget(a.baseId(), a.unitName(), a.floor(), a.phase(),
                                        a.slotsShort() + 1, a.closest()));
                    }
                }
                results.add(new PlatoonResult(zone.zoneId(), zone.territory() + (zone.bonus() ? " (bonus)" : ""),
                        zone.floorLabel(), p + 1, complete, missing.size(), slotResults));
            }
        }

        List<FarmTarget> farmList = farm.values().stream()
                .sorted(Comparator.comparingInt(FarmTarget::slotsShort).reversed())
                .toList();
        return new Plan(results, farmList, used);
    }

    public static boolean meetsFloor(Unit unit, PlatoonZone zone) {
        if (unit == null || unit.ship() != zone.ship() || unit.rarity() < zone.minRarity()) {
            return false;
        }
        return unit.ship() || (unit.gearLevel() >= zone.minGearLevel() && unit.relicTier() >= zone.minRelic());
    }

    private List<Pick> candidates(Guild guild, PlatoonZone zone, String baseId,
                                  Map<String, Set<String>> used, Map<String, Integer> placedInZone) {
        List<Pick> result = new ArrayList<>();
        for (Player player : guild.getPlayers().values()) {
            Unit unit = player.getUnits().get(baseId);
            if (meetsFloor(unit, zone)
                    && !used.getOrDefault(player.getAllyCode(), Set.of()).contains(baseId)
                    && placedInZone.getOrDefault(zone.zoneId() + "|" + player.getAllyCode(), 0) < zone.maxUnitsPerPlayer()) {
                result.add(new Pick(player, unit));
            }
        }
        return result;
    }

    /** Owners below the floor, strongest copy first -- who is closest to unlocking the slot. */
    private List<String> closest(Guild guild, PlatoonZone zone, String baseId) {
        return guild.getPlayers().values().stream()
                .filter(p -> p.getUnits().containsKey(baseId) && !meetsFloor(p.getUnits().get(baseId), zone))
                .sorted(Comparator.comparingInt((Player p) -> p.getUnits().get(baseId).power()).reversed())
                .limit(CLOSEST_SHOWN)
                .map(p -> p.getName() + " (" + p.getUnits().get(baseId).levelLabel() + ")")
                .toList();
    }

    private String unitName(Guild guild, String baseId) {
        return gameData.unit(baseId).map(UnitInfo::name).orElse(baseId);
    }

    private static void spend(Map<String, Set<String>> used, Map<String, Integer> placedInZone,
                              PlatoonZone zone, Player player, String baseId) {
        used.computeIfAbsent(player.getAllyCode(), k -> new HashSet<>()).add(baseId);
        placedInZone.merge(zone.zoneId() + "|" + player.getAllyCode(), 1, Integer::sum);
    }

    private static void unspend(Map<String, Set<String>> used, Map<String, Integer> placedInZone,
                                PlatoonZone zone, Player player, String baseId) {
        used.getOrDefault(player.getAllyCode(), new HashSet<>()).remove(baseId);
        placedInZone.merge(zone.zoneId() + "|" + player.getAllyCode(), -1, Integer::sum);
    }
}
