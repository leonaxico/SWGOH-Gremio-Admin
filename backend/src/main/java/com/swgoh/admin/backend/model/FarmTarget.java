package com.swgoh.admin.backend.model;

import java.util.List;

/**
 * A unit that keeps platoons from being completed: slotsShort slots need it
 * at {@code floor} and nobody spare has it there. closest are the guild
 * members nearest to the floor -- the ones to ask to farm it.
 */
public record FarmTarget(
        String baseId,
        String unitName,
        String floor,
        String phase,
        int slotsShort,
        List<String> closest
) {}
