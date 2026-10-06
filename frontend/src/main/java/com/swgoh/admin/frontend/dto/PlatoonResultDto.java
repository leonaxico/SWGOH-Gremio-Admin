package com.swgoh.admin.frontend.dto;

import java.util.List;

public record PlatoonResultDto(
        String zoneId,
        String territory,
        String floor,
        int platoon,
        boolean complete,
        int missingSlots,
        List<PlatoonSlotResultDto> slots
) {}
