package com.swgoh.admin.backend.web;

import com.swgoh.admin.backend.dto.TbSummaryDto;
import com.swgoh.admin.backend.gamedata.GameDataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tbs")
public class TbController {

    private final GameDataService gameData;

    public TbController(GameDataService gameData) {
        this.gameData = gameData;
    }

    @GetMapping
    public List<TbSummaryDto> list() {
        return gameData.tbs().stream()
                .map(tb -> new TbSummaryDto(tb.tbId(), tb.name(), tb.phases()))
                .toList();
    }
}
