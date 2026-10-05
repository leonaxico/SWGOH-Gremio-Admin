package com.swgoh.admin.frontend.dto;

import java.util.List;

public record OptimizeResponseDto(List<AssignmentDto> assignments, List<UnfilledSlotDto> unfilled) {}
