package com.swgoh.admin.backend.model;

import java.util.Map;

public class Player {
    private final String allyCode;
    private final String playerId;
    private final String name;
    private final int totalGp;
    private final Map<String, Unit> units; // baseId -> Unit

    public Player(String allyCode, String playerId, String name, int totalGp, Map<String, Unit> units) {
        this.allyCode = allyCode;
        this.playerId = playerId;
        this.name = name;
        this.totalGp = totalGp;
        this.units = units;
    }

    public String getAllyCode() {
        return allyCode;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getName() {
        return name;
    }

    public int getTotalGp() {
        return totalGp;
    }

    public Map<String, Unit> getUnits() {
        return units;
    }
}
