package com.swgoh.admin.backend.model;

import java.util.List;

public record Unit(
        String baseId,
        String name,
        int power,
        int rarity,
        int gearLevel,
        int relicTier,
        List<String> tags
) {}
