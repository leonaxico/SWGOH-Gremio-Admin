package com.swgoh.admin.backend.model;

import java.util.List;

public record PlatoonResult(
        String zoneId,
        String territory,
        String floor,
        int platoon,
        boolean complete,
        int missingSlots,
        List<PlatoonSlotResult> slots
) {}
