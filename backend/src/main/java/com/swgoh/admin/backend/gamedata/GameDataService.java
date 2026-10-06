package com.swgoh.admin.backend.gamedata;

import com.fasterxml.jackson.databind.JsonNode;
import com.swgoh.admin.backend.client.ComlinkClient;
import com.swgoh.admin.backend.model.MissionRequirement;
import com.swgoh.admin.backend.model.PlatoonZone;
import com.swgoh.admin.backend.model.TBDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads static game data from Comlink once and resolves every Territory
 * Battle into concrete mission requirements:
 * <pre>
 * territoryBattleDefinition.strikeZoneDefinition / covertZoneDefinition
 *   -> campaignElementIdentifier (campaign / map / node / difficulty / mission)
 *   -> campaign[...].campaignNodeMission.entryCategoryAllowed   (the requirement)
 * units[].categoryId                                            (what each unit counts as)
 * category[] + localization                                     (readable names)
 * </pre>
 * Loaded in the background at startup and cached in memory until restart;
 * game data only changes with game updates.
 */
@Service
public class GameDataService {

    private static final Logger log = LoggerFactory.getLogger(GameDataService.class);

    // /data "items" bitmask: category (bit 0), territoryBattleDefinition (29),
    // units (37), campaign (38). Mapping probed with debug/comlink_data_bits.sh.
    static final long ITEMS = (1L) | (1L << 29) | (1L << 37) | (1L << 38);

    private static final Pattern ZONE_PHASE = Pattern.compile("phase(\\d+)_conflict");
    // In-game rich-text markup in localized strings: [c], [/c], [-], [f0ff23] ...
    private static final Pattern MARKUP = Pattern.compile("\\[(?:/?c|-|[0-9a-fA-F]{6})]");
    private static final int RELIC_TIER_OFFSET = 2;
    private static final int UNIT_COMBAT_TYPE_SHIP = 2;

    private final ComlinkClient comlinkClient;
    private final String language;

    private volatile Catalog catalog;

    private record Catalog(Map<String, TBDefinition> tbs, Map<String, UnitInfo> units, Map<String, String> unitsByName) {}

    private final List<Runnable> onLoad = new CopyOnWriteArrayList<>();

    public GameDataService(ComlinkClient comlinkClient,
                           @Value("${comlink.language:ENG_US}") String language) {
        this.comlinkClient = comlinkClient;
        this.language = language;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        Thread thread = new Thread(() -> {
            try {
                catalog();
            } catch (Exception e) {
                log.warn("Game data warm-up failed, will retry on first request: {}", e.getMessage());
            }
        }, "gamedata-warmup");
        thread.setDaemon(true);
        thread.start();
    }

    public List<TBDefinition> tbs() {
        return List.copyOf(catalog().tbs().values());
    }

    public TBDefinition tb(String tbId) {
        TBDefinition tb = catalog().tbs().get(tbId);
        if (tb == null) {
            throw new IllegalArgumentException("Unknown TB id: " + tbId);
        }
        return tb;
    }

    public Optional<UnitInfo> unit(String baseId) {
        return Optional.ofNullable(catalog().units().get(baseId));
    }

    /**
     * Resolves a unit written by a person -- a baseId ("VADER"), a
     * definitionId ("VADER:SEVEN_STAR") or its name in the configured
     * language ("Darth Vader", case-insensitive) -- to its baseId.
     */
    public Optional<String> resolveUnit(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String id = token.trim().split(":")[0];
        if (catalog().units().containsKey(id)) {
            return Optional.of(id);
        }
        return Optional.ofNullable(catalog().unitsByName().get(normalizeName(token)));
    }

    /** Runs once game data is loaded (immediately if it already is). */
    public void onLoaded(Runnable callback) {
        if (catalog != null) {
            callback.run();
        } else {
            onLoad.add(callback);
        }
    }

    private Catalog catalog() {
        Catalog current = catalog;
        if (current == null) {
            synchronized (this) {
                current = catalog;
                if (current == null) {
                    current = load();
                    catalog = current;
                    onLoad.forEach(Runnable::run);
                    onLoad.clear();
                }
            }
        }
        return current;
    }

    private Catalog load() {
        long start = System.currentTimeMillis();
        JsonNode metadata = comlinkClient.getMetadata();
        String version = metadata.path("latestGamedataVersion").asText();
        String bundleId = metadata.path("latestLocalizationBundleVersion").asText();

        GameData data = comlinkClient.getGameData(version, ITEMS);
        Map<String, String> loc = comlinkClient.getLocalization(bundleId, language);

        Map<String, UnitInfo> units = new HashMap<>();
        for (GameData.UnitDef u : nz(data.units())) {
            units.putIfAbsent(u.baseId(), new UnitInfo(
                    u.baseId(),
                    loc.getOrDefault(u.nameKey(), u.baseId()),
                    u.combatType() == UNIT_COMBAT_TYPE_SHIP,
                    Set.copyOf(nz(u.categoryId()))
            ));
        }

        // Names aren't unique (event variants reuse them): prefer units obtainable now, then the shortest id.
        Set<String> obtainableNow = new HashSet<>();
        nz(data.units()).stream()
                .filter(u -> "0".equals(u.obtainableTime()))
                .forEach(u -> obtainableNow.add(u.baseId()));
        Map<String, String> unitsByName = new HashMap<>();
        units.values().stream()
                .sorted(Comparator.comparing((UnitInfo u) -> !obtainableNow.contains(u.baseId()))
                        .thenComparingInt(u -> u.baseId().length()))
                .forEach(u -> unitsByName.putIfAbsent(normalizeName(u.name()), u.baseId()));

        Map<String, String> categoryNames = new HashMap<>();
        for (GameData.Category c : nz(data.category())) {
            String text = loc.get(c.descKey());
            categoryNames.put(c.id(), text != null ? text : c.id());
        }

        Map<String, GameData.CampaignMission> campaignMissions = indexCampaignMissions(data);

        Map<String, TBDefinition> tbs = new LinkedHashMap<>();
        nz(data.territoryBattleDefinition()).stream()
                .sorted(Comparator.comparing(GameData.TbDef::id).reversed()) // newest TB first
                .forEach(tb -> tbs.put(tb.id(), buildTb(tb, campaignMissions, units, categoryNames, loc)));

        log.info("Game data {} loaded in {} ms: {} units, {} TBs, {} missions, {} platoon zones",
                version, System.currentTimeMillis() - start, units.size(), tbs.size(),
                tbs.values().stream().mapToInt(t -> t.missions().size()).sum(),
                tbs.values().stream().mapToInt(t -> t.platoonZones().size()).sum());
        return new Catalog(tbs, units, unitsByName);
    }

    private Map<String, GameData.CampaignMission> indexCampaignMissions(GameData data) {
        Map<String, GameData.CampaignMission> index = new HashMap<>();
        for (GameData.Campaign campaign : nz(data.campaign())) {
            for (GameData.CampaignMap map : nz(campaign.campaignMap())) {
                for (GameData.DifficultyGroup group : nz(map.campaignNodeDifficultyGroup())) {
                    for (GameData.CampaignNode node : nz(group.campaignNode())) {
                        for (GameData.CampaignMission mission : nz(node.campaignNodeMission())) {
                            index.put(key(campaign.id(), map.id(), node.id(),
                                    group.campaignNodeDifficulty(), mission.id()), mission);
                        }
                    }
                }
            }
        }
        return index;
    }

    private TBDefinition buildTb(GameData.TbDef tb,
                                 Map<String, GameData.CampaignMission> campaignMissions,
                                 Map<String, UnitInfo> units,
                                 Map<String, String> categoryNames,
                                 Map<String, String> loc) {
        Map<String, GameData.ConflictZone> conflicts = new HashMap<>();
        nz(tb.conflictZoneDefinition()).forEach(c -> conflicts.put(c.zoneDefinition().zoneId(), c));

        List<GameData.MissionZone> zones = new ArrayList<>(nz(tb.strikeZoneDefinition()));
        int covertStart = zones.size();
        zones.addAll(nz(tb.covertZoneDefinition()));

        List<MissionRequirement> missions = new ArrayList<>();
        Set<String> seenZones = new HashSet<>();
        Map<String, Integer> counters = new HashMap<>();
        for (int i = 0; i < zones.size(); i++) {
            GameData.MissionZone zone = zones.get(i);
            boolean special = i >= covertStart;
            if (zone.zoneDefinition() == null || zone.campaignElementIdentifier() == null
                    || !seenZones.add(zone.zoneDefinition().zoneId())) {
                continue;
            }
            Matcher phase = ZONE_PHASE.matcher(zone.zoneDefinition().zoneId());
            GameData.CampaignElementId ce = zone.campaignElementIdentifier();
            GameData.CampaignMission mission = campaignMissions.get(key(ce.campaignId(), ce.campaignMapId(),
                    ce.campaignNodeId(), ce.campaignNodeDifficulty(), ce.campaignMissionId()));
            if (!phase.find() || mission == null || mission.entryCategoryAllowed() == null) {
                continue;
            }

            GameData.EntryCategory e = mission.entryCategoryAllowed();
            GameData.ConflictZone conflict = conflicts.get(zone.zoneDefinition().linkedConflictId());
            String territory = conflict == null ? zone.zoneDefinition().linkedConflictId()
                    : loc.getOrDefault(conflict.zoneDefinition().nameKey(), conflict.zoneDefinition().zoneId());
            boolean bonus = conflict != null && conflict.bonus();
            MissionRequirement.Type type = special ? MissionRequirement.Type.SPECIAL
                    : (mission.combatType() == UNIT_COMBAT_TYPE_SHIP ? MissionRequirement.Type.FLEET
                    : MissionRequirement.Type.COMBAT);
            int n = counters.merge(zone.zoneDefinition().linkedConflictId() + type, 1, Integer::sum);
            String name = territory + (bonus ? " (bonus)" : "") + " · " + label(type) + " " + n;

            int relicRaw = e.minimumRelicTier();
            int minGear = relicRaw >= RELIC_TIER_OFFSET ? 13 : e.minimumUnitTier();
            int minRelic = Math.max(0, relicRaw - RELIC_TIER_OFFSET);
            List<String> mandatory = nz(e.mandatoryRosterUnit()).stream().map(GameData.MandatoryUnit::id).toList();

            String requirementText = plainText(loc.get(mission.descKey()));
            if (requirementText.isBlank()) {
                requirementText = describe(e, minRelic, mandatory, units, categoryNames);
            }

            missions.add(new MissionRequirement(
                    zone.zoneDefinition().zoneId(),
                    "phase_" + Integer.parseInt(phase.group(1)),
                    territory,
                    name,
                    requirementText,
                    type,
                    bonus,
                    nz(e.categoryId()),
                    nz(e.excludeCategoryId()),
                    nz(e.commanderCategoryId()),
                    mandatory,
                    Math.max(1, e.minimumRequiredUnitQuantity()),
                    e.maximumAllowedUnitQuantity(),
                    e.minimumUnitRarity(),
                    minGear,
                    minRelic
            ));
        }

        List<PlatoonZone> platoonZones = new ArrayList<>();
        for (GameData.ReconZone recon : nz(tb.reconZoneDefinition())) {
            if (recon.zoneDefinition() == null) {
                continue;
            }
            Matcher phase = ZONE_PHASE.matcher(recon.zoneDefinition().zoneId());
            if (!phase.find()) {
                continue;
            }
            GameData.ConflictZone conflict = conflicts.get(recon.zoneDefinition().linkedConflictId());
            String territory = conflict == null ? recon.zoneDefinition().linkedConflictId()
                    : loc.getOrDefault(conflict.zoneDefinition().nameKey(), conflict.zoneDefinition().zoneId());
            int relicRaw = recon.unitRelicTier();
            boolean ship = recon.combatType() == UNIT_COMBAT_TYPE_SHIP;
            platoonZones.add(new PlatoonZone(
                    recon.zoneDefinition().zoneId(),
                    "phase_" + Integer.parseInt(phase.group(1)),
                    territory,
                    conflict != null && conflict.bonus(),
                    ship,
                    recon.unitRarity(),
                    !ship && relicRaw >= RELIC_TIER_OFFSET ? 13 : 0,
                    ship ? 0 : Math.max(0, relicRaw - RELIC_TIER_OFFSET),
                    recon.zoneDefinition().maxUnitCountPerPlayer() > 0
                            ? recon.zoneDefinition().maxUnitCountPerPlayer() : Integer.MAX_VALUE,
                    nz(recon.platoonDefinition()).stream().map(GameData.PlatoonDef::id).toList()
            ));
        }

        TreeSet<String> phases = new TreeSet<>(Comparator.comparingInt(p -> Integer.parseInt(p.substring(6))));
        missions.forEach(m -> phases.add(m.phase()));
        platoonZones.forEach(z -> phases.add(z.phase()));
        return new TBDefinition(tb.id(), titleCase(loc.getOrDefault(tb.nameKey(), tb.id())),
                List.copyOf(phases), List.copyOf(missions), List.copyOf(platoonZones));
    }

    /** Lower-case, straight quotes, single spaces -- so "Ahsoka Tano (Snips)" typed by hand matches. */
    private static String normalizeName(String s) {
        return s.toLowerCase(Locale.ROOT)
                .replace('\u2019', '\'').replace('\u2018', '\'')
                .replace('\u201c', '"').replace('\u201d', '"')
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Fallback when the campaign mission has no localized requirement line. */
    private String describe(GameData.EntryCategory e, int minRelic, List<String> mandatory,
                            Map<String, UnitInfo> units, Map<String, String> categoryNames) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.minimumRequiredUnitQuantity()).append("x ");
        sb.append(String.join(" / ", nz(e.categoryId()).stream().map(c -> categoryNames.getOrDefault(c, c)).toList()));
        if (minRelic > 0) {
            sb.append(" (Relic ").append(minRelic).append("+)");
        }
        if (!mandatory.isEmpty()) {
            sb.append(", must include ").append(String.join(", ", mandatory.stream()
                    .map(id -> units.containsKey(id) ? units.get(id).name() : id).toList()));
        }
        return sb.toString();
    }

    private static String plainText(String s) {
        if (s == null) {
            return "";
        }
        return MARKUP.matcher(s).replaceAll("").replace("\\n", " · ").trim();
    }

    private static String label(MissionRequirement.Type type) {
        return switch (type) {
            case COMBAT -> "Combat";
            case SPECIAL -> "Special";
            case FLEET -> "Fleet";
        };
    }

    private static String titleCase(String s) {
        StringBuilder sb = new StringBuilder();
        for (String word : s.toLowerCase(Locale.ROOT).split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
    }

    private static String key(String campaign, String map, String node, int difficulty, String mission) {
        return campaign + "|" + map + "|" + node + "|" + difficulty + "|" + mission;
    }

    private static <T> List<T> nz(List<T> list) {
        return list == null ? List.of() : list;
    }
}
