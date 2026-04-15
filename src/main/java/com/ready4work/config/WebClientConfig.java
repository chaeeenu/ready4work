package com.ready4work.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient weatherWebClient(@Value("${weather.api.base-url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Bean
    public WebClient odsayWebClient(@Value("${odsay.api.base-url}") String baseUrl,
                                     @Value("${odsay.api.key}") String apiKey) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .filter((request, next) -> {
                    URI uri = UriComponentsBuilder.fromUri(request.url())
                            .queryParam("apiKey", apiKey).build().toUri();
                    return next.exchange(ClientRequest.from(request).url(uri).build());
                })
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

    @Bean
    public WebClient busStationWebClient(@Value("${datagokr.api.bus.station.base-url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Bean
    public WebClient busArrivalWebClient(@Value("${datagokr.api.bus.arrival.base-url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
}
