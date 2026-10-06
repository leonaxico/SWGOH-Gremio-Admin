package com.swgoh.admin.backend.dto;

import com.swgoh.admin.backend.model.Assignment;
import com.swgoh.admin.backend.model.FarmTarget;
import com.swgoh.admin.backend.model.MissionSummary;
import com.swgoh.admin.backend.model.PlatoonResult;

import java.util.List;

/**
 * platoonSource: "live", "manual", "live capture <date>", "template" (file not
 * filled in yet) or "none".
 */
public record OptimizeResponse(
        String tbId,
        String tbName,
        String phase,
        int guildMembers,
        String platoonSource,
        List<String> platoonWarnings,
        List<PlatoonResult> platoons,
        List<FarmTarget> farm,
        List<MissionSummary> missions,
        List<Assignment> assignments
) {}
