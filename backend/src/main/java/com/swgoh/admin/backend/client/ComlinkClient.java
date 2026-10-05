package com.swgoh.admin.backend.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Thin wrapper around a self-hosted SWGoH Comlink instance.
 * Comlink docs / setup: https://github.com/swgoh-utils/swgoh-comlink
 * <p>
 * All endpoints are POSTs with a JSON body shaped like:
 * {"payload": {...}, "enums": false}
 */
@Component
public class ComlinkClient {

    private final RestClient restClient;

    public ComlinkClient(RestClient comlinkRestClient) {
        this.restClient = comlinkRestClient;
    }

    public JsonNode getPlayerByAllyCode(String allyCode) {
        return post("/player", Map.of("allyCode", allyCode));
    }

    public JsonNode getPlayerById(String playerId) {
        return post("/player", Map.of("playerId", playerId));
    }

    public JsonNode getGuild(String guildId) {
        return post("/guild", Map.of(
                "guildId", guildId,
                "includeRecentGuildActivityInfo", false
        ));
    }

    public JsonNode getMetadata() {
        return post("/metadata", Map.of());
    }

    private JsonNode post(String endpoint, Map<String, Object> payload) {
        Map<String, Object> body = new HashMap<>();
        body.put("payload", payload);
        body.put("enums", false);

        return restClient.post()
                .uri(endpoint)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }
}
