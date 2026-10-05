package com.swgoh.admin.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient comlinkRestClient(@Value("${comlink.url:http://localhost:3000}") String comlinkUrl) {
        return RestClient.builder()
                .baseUrl(comlinkUrl)
                .build();
    }
}
