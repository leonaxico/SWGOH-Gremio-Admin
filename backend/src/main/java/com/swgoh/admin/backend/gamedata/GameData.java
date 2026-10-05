package com.swgoh.admin.backend.gamedata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Typed slice of Comlink's /data response -- only the fields this app reads.
 * Everything else (rewards, enemy previews, abilities...) is skipped while
 * streaming, which keeps the ~160 MB payload from ever being held in memory.
 * <p>
 * Collections requested (see GameDataService#ITEMS): category,
 * territoryBattleDefinition, units, campaign.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GameData(
        List<Category> category,
        List<UnitDef> units,
        List<TbDef> territoryBattleDefinition,
        List<Campaign> campaign
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Category(String id, String descKey, boolean visible) {}

    /** One entry per (unit, rarity) -- dedupe by baseId. combatType 1 = character, 2 = ship. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UnitDef(String baseId, String nameKey, int combatType, boolean obtainable, List<String> categoryId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TbDef(
            String id,
            String nameKey,
            int roundCount,
            List<ConflictZone> conflictZoneDefinition,
            List<MissionZone> strikeZoneDefinition,
            List<MissionZone> covertZoneDefinition
    ) {}

    /** A territory. forceAlignment 1 = mixed, 2 = light, 3 = dark. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ConflictZone(
            ZoneDef zoneDefinition,
            int forceAlignment,
            @JsonProperty("isBonus") boolean bonus
    ) {}

    /** A combat/fleet (strike) or special (covert) mission, pointing at its campaign mission. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MissionZone(CampaignElementId campaignElementIdentifier, ZoneDef zoneDefinition, int combatType) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ZoneDef(String zoneId, String nameKey, String linkedConflictId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CampaignElementId(
            String campaignId,
            String campaignMapId,
            String campaignNodeId,
            int campaignNodeDifficulty,
            String campaignMissionId
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Campaign(String id, List<CampaignMap> campaignMap) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CampaignMap(String id, List<DifficultyGroup> campaignNodeDifficultyGroup) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DifficultyGroup(int campaignNodeDifficulty, List<CampaignNode> campaignNode) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CampaignNode(String id, List<CampaignMission> campaignNodeMission) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CampaignMission(String id, String descKey, int combatType, EntryCategory entryCategoryAllowed) {}

    /**
     * The real squad requirement for a mission. minimumRelicTier uses the raw
     * relic scale (1 = none, 2 = R0, 7 = R5 ...), same as roster units.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EntryCategory(
            List<String> categoryId,
            List<String> commanderCategoryId,
            List<String> excludeCategoryId,
            List<MandatoryUnit> mandatoryRosterUnit,
            int matchType,
            int minimumRequiredUnitQuantity,
            int maximumAllowedUnitQuantity,
            int minimumUnitRarity,
            int minimumUnitTier,
            int minimumRelicTier
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MandatoryUnit(String id) {}
}
