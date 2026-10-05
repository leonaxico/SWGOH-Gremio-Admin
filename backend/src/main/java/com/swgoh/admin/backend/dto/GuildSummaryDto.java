package com.swgoh.admin.backend.dto;

import java.util.List;

public record GuildSummaryDto(String guildId, String name, List<PlayerSummaryDto> players) {}
