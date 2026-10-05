package com.swgoh.admin.backend.dto;

import com.swgoh.admin.backend.model.Assignment;
import com.swgoh.admin.backend.optimizer.OptimizerService;

import java.util.List;

public record OptimizeResponse(List<Assignment> assignments, List<OptimizerService.UnfilledSlot> unfilled) {}
