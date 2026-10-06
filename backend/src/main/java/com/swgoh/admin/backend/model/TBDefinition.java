package com.swgoh.admin.backend.model;

import java.util.List;

public record TBDefinition(
        String tbId,
        String name,
        List<String> phases,
        List<MissionRequirement> missions,
        List<PlatoonZone> platoonZones
) {
    public List<MissionRequirement> missionsForPhase(String phase) {
        return missions.stream().filter(m -> m.phase().equals(phase)).toList();
    }

    public List<PlatoonZone> platoonZonesForPhase(String phase) {
        return platoonZones.stream().filter(z -> z.phase().equals(phase)).toList();
    }
}
