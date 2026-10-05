package com.swgoh.admin.frontend.web;

import com.swgoh.admin.frontend.client.BackendClient;
import com.swgoh.admin.frontend.dto.GuildSummaryDto;
import com.swgoh.admin.frontend.dto.OptimizeResponseDto;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {

    private final BackendClient backendClient;

    public HomeController(BackendClient backendClient) {
        this.backendClient = backendClient;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("allyCode", "191483497");
        model.addAttribute("tbs", backendClient.listTbs());
        return "index";
    }

    @PostMapping("/guild/fetch")
    public String fetchGuild(@RequestParam String allyCode, Model model) {
        GuildSummaryDto guild = backendClient.fetchGuild(allyCode);
        model.addAttribute("guild", guild);
        model.addAttribute("allyCode", allyCode);
        model.addAttribute("tbs", backendClient.listTbs());
        return "index";
    }

    @PostMapping("/optimize")
    public String optimize(@RequestParam String tbId, @RequestParam String phase, Model model) {
        OptimizeResponseDto result = backendClient.optimize(tbId, phase);
        model.addAttribute("result", result);
        model.addAttribute("tbs", backendClient.listTbs());
        model.addAttribute("selectedTb", tbId);
        model.addAttribute("selectedPhase", phase);
        model.addAttribute("allyCode", "191483497");
        return "index";
    }
}
