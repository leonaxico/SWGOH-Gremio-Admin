package com.swgoh.admin.backend.web;

import com.swgoh.admin.backend.dto.FarmReport;
import com.swgoh.admin.backend.dto.OptimizeRequest;
import com.swgoh.admin.backend.dto.OptimizeResponse;
import com.swgoh.admin.backend.gamedata.GameDataService;
import com.swgoh.admin.backend.model.FarmTarget;
import com.swgoh.admin.backend.model.Guild;
import com.swgoh.admin.backend.model.MissionRequirement;
import com.swgoh.admin.backend.model.PlatoonResult;
import com.swgoh.admin.backend.model.TBDefinition;
import com.swgoh.admin.backend.optimizer.OptimizerService;
import com.swgoh.admin.backend.platoon.PlatoonPlanner;
import com.swgoh.admin.backend.platoon.PlatoonService;
import com.swgoh.admin.backend.service.GuildCacheService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api")
public class OptimizeController {

    private final GameDataService gameData;
    private final GuildCacheService cacheService;
    private final OptimizerService optimizerService;
    private final PlatoonService platoonService;
    private final PlatoonPlanner platoonPlanner;

    public OptimizeController(GameDataService gameData, GuildCacheService cacheService, OptimizerService optimizerService,
                              PlatoonService platoonService, PlatoonPlanner platoonPlanner) {
        this.gameData = gameData;
        this.cacheService = cacheService;
        this.optimizerService = optimizerService;
        this.platoonService = platoonService;
        this.platoonPlanner = platoonPlanner;
    }

    /** Plans one phase: platoons first, then missions with the units left over. */
    @PostMapping("/optimize")
    public OptimizeResponse optimize(@RequestBody OptimizeRequest request) {
        Guild guild = currentGuild();
        TBDefinition tb = gameData.tb(request.tbId());
        List<MissionRequirement> missions = tb.missionsForPhase(request.phase());
        if (missions.isEmpty() && tb.platoonZonesForPhase(request.phase()).isEmpty()) {
            throw new IllegalArgumentException(
                    tb.name() + " has nothing in '" + request.phase() + "' -- phases are " + tb.phases()
            );
        }

        PlatoonService.PlatoonSet platoons = platoonService.requirements(tb);
        PlatoonPlanner.Plan platoonPlan = platoonPlanner.plan(guild, tb.platoonZonesForPhase(request.phase()), platoons);
        OptimizerService.Result result = optimizerService.optimizePhase(guild, missions, platoonPlan.usedUnits());

        return new OptimizeResponse(tb.tbId(), tb.name(), request.phase(), guild.getPlayers().size(),
                platoons.source(), platoons.warnings(), platoonPlan.platoons(), platoonPlan.farm(),
                result.missions(), result.assignments());
    }

    /** Platoon readiness and farm list across every phase of a TB. */
    @GetMapping("/tbs/{tbId}/farm")
    public FarmReport farm(@PathVariable String tbId) {
        Guild guild = currentGuild();
        TBDefinition tb = gameData.tb(tbId);
        PlatoonService.PlatoonSet platoons = platoonService.requirements(tb);

        List<FarmReport.PhaseReadiness> phases = new ArrayList<>();
        List<FarmTarget> farm = new ArrayList<>();
        for (String phase : tb.phases()) {
            PlatoonPlanner.Plan plan = platoonPlanner.plan(guild, tb.platoonZonesForPhase(phase), platoons);
            phases.add(new FarmReport.PhaseReadiness(phase,
                    (int) plan.platoons().stream().filter(PlatoonResult::complete).count(),
                    plan.platoons().size()));
            farm.addAll(plan.farm());
        }
        farm.sort(Comparator.comparingInt(FarmTarget::slotsShort).reversed());
        return new FarmReport(tb.tbId(), tb.name(), platoons.source(), platoons.warnings(), phases, farm);
    }

    private Guild currentGuild() {
        return cacheService.getCurrent()
                .orElseThrow(() -> new IllegalStateException("No guild loaded -- call /api/guild/fetch first"));
    }
}
