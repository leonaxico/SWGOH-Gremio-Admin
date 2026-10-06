package com.swgoh.admin.frontend.dto;

import java.util.List;

public record FarmReportDto(
        String tbId,
        String tbName,
        String platoonSource,
        List<String> platoonWarnings,
        List<PhaseReadinessDto> phases,
        List<FarmTargetDto> farm
) {
    public record PhaseReadinessDto(String phase, int completePlatoons, int totalPlatoons) {}
}
