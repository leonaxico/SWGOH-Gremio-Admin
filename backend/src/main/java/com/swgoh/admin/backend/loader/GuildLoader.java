package com.swgoh.admin.backend.loader;

import com.fasterxml.jackson.databind.JsonNode;
import com.swgoh.admin.backend.client.ComlinkClient;
import com.swgoh.admin.backend.model.Guild;
import com.swgoh.admin.backend.model.Player;
import org.springframework.stereotype.Component;

@Component
public class GuildLoader {

    private final ComlinkClient comlinkClient;
    private final PlayerMapper playerMapper;

    public GuildLoader(ComlinkClient comlinkClient, PlayerMapper playerMapper) {
        this.comlinkClient = comlinkClient;
        this.playerMapper = playerMapper;
    }

    /**
     * Look up a guild via any member's ally code, then pull every member's
     * full roster. One /player call per member -- sequential on purpose to
     * stay well under Comlink's upstream rate limits. For a large guild
     * this can take a while; there's no caching layer yet (see README).
     */
    public Guild load(String seedAllyCode) {
        JsonNode seedRaw = comlinkClient.getPlayerByAllyCode(seedAllyCode);
        String guildId = seedRaw.path("guildId").asText(null);
        if (guildId == null || guildId.isBlank()) {
            throw new IllegalStateException(
                    "Player " + seedAllyCode + " has no guildId in the Comlink response " +
                            "(not in a guild, or the field name has changed -- inspect the raw response)."
            );
        }

        JsonNode guildRaw = comlinkClient.getGuild(guildId);
        JsonNode guildNode = guildRaw.has("guild") ? guildRaw.path("guild") : guildRaw;
        String guildName = guildNode.path("name").asText(guildId);

        Guild guild = new Guild(guildId, guildName);

        Player seedPlayer = playerMapper.fromRaw(seedRaw);
        guild.getPlayers().put(seedPlayer.getAllyCode(), seedPlayer);

        JsonNode members = guildNode.path("member");
        if (members.isArray()) {
            for (JsonNode member : members) {
                String playerId = member.path("playerId").asText(null);
                if (playerId == null || playerId.isBlank()) {
                    continue;
                }
                JsonNode raw = comlinkClient.getPlayerById(playerId);
                Player p = playerMapper.fromRaw(raw);
                if (p.getAllyCode().equals(seedPlayer.getAllyCode())) {
                    continue;
                }
                guild.getPlayers().put(p.getAllyCode(), p);
            }
        }

        return guild;
    }
}
