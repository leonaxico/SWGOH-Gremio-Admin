package com.swgoh.admin.frontend.dto;

import java.util.List;

public record OptimizeResponseDto(
        String tbId,
        String tbName,
        String phase,
        int guildMembers,
        List<MissionSummaryDto> missions,
        List<AssignmentDto> assignments
) {}
