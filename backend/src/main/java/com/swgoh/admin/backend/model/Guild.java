package com.swgoh.admin.backend.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class Guild {
    private final String guildId;
    private final String name;
    private final Map<String, Player> players = new LinkedHashMap<>(); // allyCode -> Player

    public Guild(String guildId, String name) {
        this.guildId = guildId;
        this.name = name;
    }

    public String getGuildId() {
        return guildId;
    }

    public String getName() {
        return name;
    }

    public Map<String, Player> getPlayers() {
        return players;
    }
}
