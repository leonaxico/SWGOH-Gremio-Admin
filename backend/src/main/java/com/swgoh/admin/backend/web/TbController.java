package com.swgoh.admin.backend.web;

import com.swgoh.admin.backend.config.TbRegistry;
import com.swgoh.admin.backend.dto.TbSummaryDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tbs")
public class TbController {

    private final TbRegistry tbRegistry;

    public TbController(TbRegistry tbRegistry) {
        this.tbRegistry = tbRegistry;
    }

    @GetMapping
    public List<TbSummaryDto> list() {
        return tbRegistry.list().stream()
                .map(tb -> new TbSummaryDto(tb.tbId(), tb.name(), tb.phases()))
                .toList();
    }
}
