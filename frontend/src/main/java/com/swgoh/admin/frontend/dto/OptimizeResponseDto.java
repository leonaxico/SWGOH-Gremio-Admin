package com.swgoh.admin.frontend.dto;

import java.util.List;

public record OptimizeResponseDto(
        String tbId,
        String tbName,
        String phase,
        int guildMembers,
        String platoonSource,
        List<String> platoonWarnings,
        List<PlatoonResultDto> platoons,
        List<FarmTargetDto> farm,
        List<MissionSummaryDto> missions,
        List<AssignmentDto> assignments
) {}
