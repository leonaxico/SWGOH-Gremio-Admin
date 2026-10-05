package com.swgoh.admin.backend.gamedata;

import java.util.Set;

/** Static game-data facts about a unit, keyed by baseId. */
public record UnitInfo(String baseId, String name, boolean ship, Set<String> categories) {}
