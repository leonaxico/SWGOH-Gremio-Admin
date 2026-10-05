package com.swgoh.admin.backend.web;

import com.swgoh.admin.backend.config.TbRegistry;
import com.swgoh.admin.backend.dto.OptimizeRequest;
import com.swgoh.admin.backend.dto.OptimizeResponse;
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

    private final TbRegistry tbRegistry;
    private final GuildCacheService cacheService;
    private final OptimizerService optimizerService;

    public OptimizeController(TbRegistry tbRegistry, GuildCacheService cacheService, OptimizerService optimizerService) {
        this.tbRegistry = tbRegistry;
        this.cacheService = cacheService;
        this.optimizerService = optimizerService;
    }

    @PostMapping
    public OptimizeResponse optimize(@RequestBody OptimizeRequest request) {
        Guild guild = cacheService.getCurrent()
                .orElseThrow(() -> new IllegalStateException("No guild loaded -- call /api/guild/fetch first"));

        TBDefinition tb = tbRegistry.get(request.tbId());
        List<MissionRequirement> missions = tb.missionsForPhase(request.phase());
        if (missions.isEmpty()) {
            throw new IllegalArgumentException(
                    "No missions configured for phase '" + request.phase() + "' -- add some in TbRegistry."
            );
        }

        OptimizerService.Result result = optimizerService.optimizePhase(guild, missions);
        return new OptimizeResponse(result.assignments(), result.unfilled());
    }
}
