package com.swgoh.admin.backend.client;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.swgoh.admin.backend.gamedata.GameData;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

    /**
     * @param version latestGamedataVersion from /metadata
     * @param items   bitmask of collections to include (bit per collection,
     *                see debug/comlink_data_bits.sh for the mapping)
     */
    public GameData getGameData(String version, long items) {
        Map<String, Object> body = Map.of(
                "payload", Map.of(
                        "version", version,
                        "includePveUnits", false,
                        "items", Long.toString(items)
                ),
                "enums", false
        );
        return restClient.post()
                .uri("/data")
                .body(body)
                .retrieve()
                .body(GameData.class);
    }

    /**
     * Fetches the zipped localization bundle and returns the key -> text map
     * for one language (e.g. "ENG_US", "SPA_XM"). The zip holds every
     * language; only the requested file is decoded. The bundle is one ~45 MB
     * base64 string, over Jackson's default 20 MB string limit, so it's read
     * with a parser that lifts that limit.
     */
    public Map<String, String> getLocalization(String bundleId, String language) {
        Map<String, Object> body = Map.of("payload", Map.of("id", bundleId), "unzip", false);
        byte[] response = restClient.post()
                .uri("/localization")
                .body(body)
                .retrieve()
                .body(byte[].class);

        byte[] zip = extractBundle(response);
        String wanted = "Loc_" + language + ".txt";
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry; (entry = in.getNextEntry()) != null; ) {
                if (!entry.getName().equals(wanted)) {
                    continue;
                }
                Map<String, String> result = new HashMap<>();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String line; (line = reader.readLine()) != null; ) {
                    int sep = line.indexOf('|');
                    if (sep > 0 && !line.startsWith("#")) {
                        result.put(line.substring(0, sep), line.substring(sep + 1));
                    }
                }
                return result;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        throw new IllegalStateException("Localization bundle has no " + wanted);
    }

    private static byte[] extractBundle(byte[] json) {
        JsonFactory factory = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build())
                .build();
        try (JsonParser parser = factory.createParser(json)) {
            for (JsonToken t; (t = parser.nextToken()) != null; ) {
                if (t == JsonToken.FIELD_NAME && "localizationBundle".equals(parser.currentName())) {
                    parser.nextToken();
                    return parser.getBinaryValue();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        throw new IllegalStateException("Comlink /localization response has no localizationBundle");
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
