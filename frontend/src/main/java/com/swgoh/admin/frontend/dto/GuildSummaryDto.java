package com.swgoh.admin.frontend.dto;

import java.util.List;

public record GuildSummaryDto(String guildId, String name, List<PlayerSummaryDto> players) {}
