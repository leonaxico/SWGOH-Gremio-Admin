package com.swgoh.admin.backend.web;

import com.swgoh.admin.backend.dto.OptimizeRequest;
import com.swgoh.admin.backend.dto.OptimizeResponse;
import com.swgoh.admin.backend.gamedata.GameDataService;
import com.swgoh.admin.backend.model.Guild;
import com.swgoh.admin.backend.model.MissionRequirement;
import com.swgoh.admin.backend.model.TBDefinition;
import com.swgoh.admin.backend.optimizer.OptimizerService;
import com.swgoh.admin.backend.service.GuildCacheService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/optimize")
public class OptimizeController {

    private final GameDataService gameData;
    private final GuildCacheService cacheService;
    private final OptimizerService optimizerService;

    public OptimizeController(GameDataService gameData, GuildCacheService cacheService, OptimizerService optimizerService) {
        this.gameData = gameData;
        this.cacheService = cacheService;
        this.optimizerService = optimizerService;
    }

    @PostMapping
    public OptimizeResponse optimize(@RequestBody OptimizeRequest request) {
        Guild guild = cacheService.getCurrent()
                .orElseThrow(() -> new IllegalStateException("No guild loaded -- call /api/guild/fetch first"));

        TBDefinition tb = gameData.tb(request.tbId());
        List<MissionRequirement> missions = tb.missionsForPhase(request.phase());
        if (missions.isEmpty()) {
            throw new IllegalArgumentException(
                    tb.name() + " has no missions in '" + request.phase() + "' -- phases are " + tb.phases()
            );
        }

        OptimizerService.Result result = optimizerService.optimizePhase(guild, missions);
        return new OptimizeResponse(tb.tbId(), tb.name(), request.phase(), guild.getPlayers().size(),
                result.missions(), result.assignments());
    }
}
