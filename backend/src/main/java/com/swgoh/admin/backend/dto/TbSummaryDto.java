package com.swgoh.admin.backend.dto;

import java.util.List;

public record TbSummaryDto(String tbId, String name, List<String> phases) {}
