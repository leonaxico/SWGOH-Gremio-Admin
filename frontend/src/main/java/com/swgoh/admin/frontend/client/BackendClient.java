package com.swgoh.admin.frontend.client;

import com.swgoh.admin.frontend.dto.FarmReportDto;
import com.swgoh.admin.frontend.dto.GuildSummaryDto;
import com.swgoh.admin.frontend.dto.OptimizeRequestDto;
import com.swgoh.admin.frontend.dto.OptimizeResponseDto;
import com.swgoh.admin.frontend.dto.TbSummaryDto;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class BackendClient {

    private final RestClient restClient;

    public BackendClient(RestClient backendRestClient) {
        this.restClient = backendRestClient;
    }

    public GuildSummaryDto fetchGuild(String allyCode) {
        return restClient.post()
                .uri(uriBuilder -> uriBuilder.path("/api/guild/fetch").queryParam("allyCode", allyCode).build())
                .retrieve()
                .body(GuildSummaryDto.class);
    }

    public List<TbSummaryDto> listTbs() {
        return restClient.get()
                .uri("/api/tbs")
                .retrieve()
                .body(new ParameterizedTypeReference<List<TbSummaryDto>>() {});
    }

    public FarmReportDto farm(String tbId) {
        return restClient.get()
                .uri("/api/tbs/{tbId}/farm", tbId)
                .retrieve()
                .body(FarmReportDto.class);
    }

    public OptimizeResponseDto optimize(String tbId, String phase) {
        return restClient.post()
                .uri("/api/optimize")
                .body(new OptimizeRequestDto(tbId, phase))
                .retrieve()
                .body(OptimizeResponseDto.class);
    }
}
