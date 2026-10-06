package com.swgoh.admin.backend.platoon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.swgoh.admin.backend.gamedata.GameDataService;
import com.swgoh.admin.backend.model.PlatoonZone;
import com.swgoh.admin.backend.model.TBDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which units each platoon needs. Comlink's static game data only has the
 * platoon structure, so requirements come from, in order:
 * <ol>
 *   <li><b>live</b> -- guild.territoryBattleStatus[].reconZoneStatus from the
 *       last guild fetch while a TB was running (also says which slots are
 *       already filled, and by whom);</li>
 *   <li><b>file</b> -- {@code platoons/{tbId}.json}, filled in by hand (e.g.
 *       from swgoh.gg) or captured from a previous live TB.</li>
 * </ol>
 * A live capture overwrites the file; a hand-made file is kept as
 * {@code {tbId}.manual.json} the first time. Templates are written for TBs
 * that have no file yet, with every zone and platoon listed and units empty.
 */
@Service
public class PlatoonService {

    private static final Logger log = LoggerFactory.getLogger(PlatoonService.class);

    static final String SOURCE_TEMPLATE = "template";
    static final String SOURCE_MANUAL = "manual";
    static final String SOURCE_LIVE_PREFIX = "live capture ";

    private static final String INSTRUCTIONS =
            "Fill each platoon's \"units\" with the units it requires: names as shown in game or on swgoh.gg "
                    + "(English), or base ids like VADER. Repeat a unit when the platoon needs it more than once "
                    + "(usually 15 units per platoon: 3 squads of 5). Set \"source\" to \"manual\" once filled. "
                    + "This file is overwritten by live data when the guild is fetched during an active TB; "
                    + "your version is then kept as <tbId>.manual.json.";

    /** One unit slot of a platoon; filledByPlayerId is set only from live data once someone placed it. */
    public record Slot(String baseId, String filledByPlayerId) {}

    /** Requirements for one TB: zoneId -> platoons (in-game order) -> slots. */
    public record PlatoonSet(String source, Map<String, List<List<Slot>>> zones, List<String> warnings) {

        public boolean isEmpty() {
            return zones.values().stream().allMatch(ps -> ps.stream().allMatch(List::isEmpty));
        }
    }

    // ---- file format ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlatoonFile(String tbId, String tbName, String source, String instructions, List<ZoneEntry> zones) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ZoneEntry(String zoneId, String phase, String territory, String unitFloor,
                            int maxUnitsPerPlayer, List<PlatoonEntry> platoons) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlatoonEntry(int platoon, List<String> units) {}

    private final GameDataService gameData;
    private final ObjectMapper mapper;
    private final Path dir;
    private final Map<String, PlatoonSet> live = new ConcurrentHashMap<>();

    public PlatoonService(GameDataService gameData, ObjectMapper mapper,
                          @Value("${platoons.dir:platoons}") String dir) {
        this.gameData = gameData;
        this.mapper = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        this.dir = Path.of(dir);
        gameData.onLoaded(this::writeMissingTemplates);
    }

    public PlatoonSet requirements(TBDefinition tb) {
        PlatoonSet current = live.get(tb.tbId());
        if (current != null) {
            return current;
        }
        PlatoonFile file = readFile(tb.tbId());
        if (file == null) {
            return new PlatoonSet("none", Map.of(), List.of("No platoons/" + tb.tbId() + ".json file"));
        }

        List<String> warnings = new ArrayList<>();
        Map<String, List<List<Slot>>> zones = new LinkedHashMap<>();
        for (ZoneEntry zone : nz(file.zones())) {
            List<List<Slot>> platoons = new ArrayList<>();
            for (PlatoonEntry platoon : nz(zone.platoons())) {
                List<Slot> slots = new ArrayList<>();
                for (String token : nz(platoon.units())) {
                    gameData.resolveUnit(token).ifPresentOrElse(
                            id -> slots.add(new Slot(id, null)),
                            () -> warnings.add("Unknown unit \"" + token + "\" in " + zone.territory()
                                    + " " + zone.phase() + " platoon " + platoon.platoon()));
                }
                platoons.add(slots);
            }
            zones.put(zone.zoneId(), platoons);
        }
        return new PlatoonSet(file.source() == null ? SOURCE_MANUAL : file.source(), zones, warnings);
    }

    /**
     * Captures platoon requirements from a raw /guild response if a TB is
     * running. Keeps them in memory (with who filled what) and saves them to
     * the TB's file for future runs.
     */
    public void captureLive(JsonNode guildNode) {
        for (JsonNode status : guildNode.path("territoryBattleStatus")) {
            String tbId = status.path("definitionId").asText("");
            if (tbId.isBlank() || !status.path("reconZoneStatus").isArray()) {
                continue;
            }
            Map<String, List<List<Slot>>> zones = new LinkedHashMap<>();
            for (JsonNode zone : status.path("reconZoneStatus")) {
                List<List<Slot>> platoons = new ArrayList<>();
                for (JsonNode platoon : zone.path("platoon")) {
                    List<Slot> slots = new ArrayList<>();
                    for (JsonNode squad : platoon.path("squad")) {
                        for (JsonNode unit : squad.path("unit")) {
                            String baseId = unit.path("unitIdentifier").asText("").split(":")[0];
                            String member = unit.path("memberId").asText("");
                            if (!baseId.isBlank()) {
                                slots.add(new Slot(baseId, member.isBlank() ? null : member));
                            }
                        }
                    }
                    platoons.add(slots);
                }
                zones.put(zone.path("zoneStatus").path("zoneId").asText(""), platoons);
            }
            PlatoonSet set = new PlatoonSet("live", zones, List.of());
            if (set.isEmpty()) {
                continue;
            }
            live.put(tbId, set);
            log.info("Captured live platoons for {}: {} zones", tbId, zones.size());
            saveCapture(tbId, zones);
        }
    }

    private void saveCapture(String tbId, Map<String, List<List<Slot>>> zones) {
        try {
            TBDefinition tb = gameData.tb(tbId);
            Path file = dir.resolve(tbId + ".json");
            PlatoonFile existing = readFile(tbId);
            if (existing != null && SOURCE_MANUAL.equals(existing.source())) {
                Files.copy(file, dir.resolve(tbId + ".manual.json"), StandardCopyOption.REPLACE_EXISTING);
            }
            Map<String, List<String>> units = new HashMap<>();
            zones.forEach((zoneId, platoons) -> {
                for (int i = 0; i < platoons.size(); i++) {
                    units.put(zoneId + "#" + i, platoons.get(i).stream().map(Slot::baseId).toList());
                }
            });
            write(tb, SOURCE_LIVE_PREFIX + LocalDate.now(), units);
        } catch (RuntimeException | IOException e) {
            log.warn("Could not save live platoons for {}: {}", tbId, e.getMessage());
        }
    }

    private void writeMissingTemplates() {
        for (TBDefinition tb : gameData.tbs()) {
            if (tb.platoonZones().isEmpty() || Files.exists(dir.resolve(tb.tbId() + ".json"))) {
                continue;
            }
            try {
                write(tb, SOURCE_TEMPLATE, Map.of());
                log.info("Wrote platoon template {}", dir.resolve(tb.tbId() + ".json"));
            } catch (IOException | RuntimeException e) {
                log.warn("Could not write platoon template for {}: {}", tb.tbId(), e.getMessage());
            }
        }
    }

    /** Writes the TB's file; units keyed "zoneId#platoonIndex", missing keys left empty. */
    private void write(TBDefinition tb, String source, Map<String, List<String>> units) throws IOException {
        List<ZoneEntry> zones = new ArrayList<>();
        for (PlatoonZone zone : tb.platoonZones()) {
            List<PlatoonEntry> platoons = new ArrayList<>();
            for (int i = 0; i < zone.platoonIds().size(); i++) {
                platoons.add(new PlatoonEntry(i + 1, units.getOrDefault(zone.zoneId() + "#" + i, List.of())));
            }
            zones.add(new ZoneEntry(zone.zoneId(), zone.phase(), zone.territory() + (zone.bonus() ? " (bonus)" : ""),
                    zone.floorLabel(), zone.maxUnitsPerPlayer(), platoons));
        }
        Files.createDirectories(dir);
        mapper.writeValue(dir.resolve(tb.tbId() + ".json").toFile(),
                new PlatoonFile(tb.tbId(), tb.name(), source, INSTRUCTIONS, zones));
    }

    private PlatoonFile readFile(String tbId) {
        Path file = dir.resolve(tbId + ".json");
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return mapper.readValue(file.toFile(), PlatoonFile.class);
        } catch (IOException e) {
            throw new IllegalStateException("Invalid platoon file " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }

    private static <T> List<T> nz(List<T> list) {
        return list == null ? List.of() : list;
    }
}
