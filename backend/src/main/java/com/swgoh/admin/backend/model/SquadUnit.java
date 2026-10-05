package com.swgoh.admin.backend.model;

public record SquadUnit(String baseId, String name, String level, int power) {

    public static SquadUnit of(Unit unit) {
        return new SquadUnit(unit.baseId(), unit.name(), unit.levelLabel(), unit.power());
    }
}
