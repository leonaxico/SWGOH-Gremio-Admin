package com.swgoh.admin.backend.web;

import com.swgoh.admin.backend.dto.GuildSummaryDto;
import com.swgoh.admin.backend.dto.PlayerSummaryDto;
import com.swgoh.admin.backend.loader.GuildLoader;
import com.swgoh.admin.backend.model.Guild;
import com.swgoh.admin.backend.service.GuildCacheService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/guild")
public class GuildController {

    private final GuildLoader guildLoader;
    private final GuildCacheService cacheService;

    public GuildController(GuildLoader guildLoader, GuildCacheService cacheService) {
        this.guildLoader = guildLoader;
        this.cacheService = cacheService;
    }

    @PostMapping("/fetch")
    public GuildSummaryDto fetch(@RequestParam String allyCode) {
        Guild guild = guildLoader.load(allyCode);
        cacheService.setCurrent(guild);
        return toDto(guild);
    }

    @GetMapping("/current")
    public GuildSummaryDto current() {
        Guild guild = cacheService.getCurrent()
                .orElseThrow(() -> new IllegalStateException("No guild loaded yet -- call /api/guild/fetch first"));
        return toDto(guild);
    }

    private GuildSummaryDto toDto(Guild guild) {
        List<PlayerSummaryDto> players = guild.getPlayers().values().stream()
                .map(p -> new PlayerSummaryDto(p.getAllyCode(), p.getName(), p.getTotalGp(), p.getUnits().size()))
                .sorted(Comparator.comparingInt(PlayerSummaryDto::gp).reversed())
                .toList();
        return new GuildSummaryDto(guild.getGuildId(), guild.getName(), players);
    }
}
