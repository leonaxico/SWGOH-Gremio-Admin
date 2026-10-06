package com.swgoh.admin.backend.dto;

import com.swgoh.admin.backend.model.FarmTarget;

import java.util.List;

/** Platoon readiness for a whole TB: each phase planned on its own (units reset every phase). */
public record FarmReport(
        String tbId,
        String tbName,
        String platoonSource,
        List<String> platoonWarnings,
        List<PhaseReadiness> phases,
        List<FarmTarget> farm
) {
    public record PhaseReadiness(String phase, int completePlatoons, int totalPlatoons) {}
}
