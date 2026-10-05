package com.swgoh.admin.backend.dto;

import com.swgoh.admin.backend.model.Assignment;
import com.swgoh.admin.backend.model.MissionSummary;

import java.util.List;

public record OptimizeResponse(
        String tbId,
        String tbName,
        String phase,
        int guildMembers,
        List<MissionSummary> missions,
        List<Assignment> assignments
) {}
