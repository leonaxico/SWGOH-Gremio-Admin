package com.swgoh.admin.backend.model;

import java.util.List;

/**
 * One unit slot of a planned platoon. playerName is who places it (null when
 * the platoon can't be completed); preFilled = already placed in game (live
 * data). missing = nobody can place it; closest lists the owners nearest to
 * the floor, e.g. "Yuri (7★ G12)".
 */
public record PlatoonSlotResult(
        String baseId,
        String unitName,
        String playerName,
        boolean preFilled,
        boolean missing,
        List<String> closest
) {}
