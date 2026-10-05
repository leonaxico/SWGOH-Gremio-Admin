package com.swgoh.admin.backend.model;

import java.util.List;
import java.util.stream.Collectors;

public record TBDefinition(
        String tbId,
        String name,
        List<String> phases,
        List<MissionRequirement> missions
) {
    public List<MissionRequirement> missionsForPhase(String phase) {
        return missions.stream()
                .filter(m -> m.phase().equals(phase))
                .collect(Collectors.toList());
    }
}
