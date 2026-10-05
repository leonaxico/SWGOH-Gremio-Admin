package com.swgoh.admin.backend.model;

import java.util.Map;

public class Player {
    private final String allyCode;
    private final String name;
    private final int totalGp;
    private final Map<String, Unit> units; // baseId -> Unit

    public Player(String allyCode, String name, int totalGp, Map<String, Unit> units) {
        this.allyCode = allyCode;
        this.name = name;
        this.totalGp = totalGp;
        this.units = units;
    }

    public String getAllyCode() {
        return allyCode;
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

    public double avgRosterPower() {
        if (units.isEmpty()) {
            return 0.0;
        }
        return units.values().stream().mapToInt(Unit::power).average().orElse(0.0);
    }
}
