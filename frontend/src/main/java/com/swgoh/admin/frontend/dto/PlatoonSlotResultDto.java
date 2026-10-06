package com.swgoh.admin.frontend.dto;

import java.util.List;

public record PlatoonSlotResultDto(
        String baseId,
        String unitName,
        String playerName,
        boolean preFilled,
        boolean missing,
        List<String> closest
) {}
