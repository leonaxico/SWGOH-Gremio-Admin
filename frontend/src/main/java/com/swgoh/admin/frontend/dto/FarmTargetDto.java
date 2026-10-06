package com.swgoh.admin.frontend.dto;

import java.util.List;

public record FarmTargetDto(String baseId, String unitName, String floor, String phase, int slotsShort, List<String> closest) {}
