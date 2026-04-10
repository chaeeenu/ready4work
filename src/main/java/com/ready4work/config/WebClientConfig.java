package com.ready4work.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient weatherWebClient(@Value("${weather.api.base-url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Bean
    public WebClient tmapWebClient(@Value("${tmap.api.base-url}") String baseUrl,
                                    @Value("${tmap.api.key}") String appKey) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("appKey", appKey)
                .build();
    }

    @Bean
    public WebClient kakaoWebClient(@Value("${kakao.api.base-url}") String baseUrl,
                                    @Value("${kakao.api.key}") String apiKey) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "KakaoAK " + apiKey)
                .build();
    }

    @Bean
    public WebClient seoulWebClient(@Value("${seoul.api.base-url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Bean
    public WebClient subwayAlertWebClient(@Value("${datagokr.api.subway.alert.base-url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}
