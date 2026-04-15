package com.ready4work.subway;

import com.ready4work.subway.dto.SubwayArrival;
import com.ready4work.subway.dto.SubwayArrivalResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SubwayArrivalService {

    private final WebClient seoulWebClient;
    private final String apiKey;
    private final ConcurrentHashMap<String, Mono<SubwayArrivalResponse>> cache = new ConcurrentHashMap<>();

    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public SubwayArrivalService(
            @Qualifier("seoulWebClient") WebClient seoulWebClient,
            @Value("${seoul.api.key}") String apiKey
    ) {
        this.seoulWebClient = seoulWebClient;
        this.apiKey = apiKey;
        log.info("SubwayArrivalService initialized, API key present: {}", apiKey != null && !apiKey.isBlank());
    }

    public Mono<SubwayArrivalResponse> getArrivals(String stationName, boolean refresh) {
        String normalized = normalizeStationName(stationName);
        if (refresh) {
            cache.remove(normalized);
        }
        return cache.computeIfAbsent(normalized, k ->
                fetchArrivals(normalized).cache(Duration.ofSeconds(15))
        );
    }

    @SuppressWarnings("unchecked")
    private Mono<SubwayArrivalResponse> fetchArrivals(String stationName) {
        return seoulWebClient.get()
                .uri("/{apiKey}/json/realtimeStationArrival/0/20/{stationName}", apiKey, stationName)
                .retrieve()
                .bodyToMono(Map.class)
                .map(body -> {
                    List<SubwayArrival> arrivals = new ArrayList<>();

                    List<Map<String, Object>> arrivalList =
                            (List<Map<String, Object>>) body.get("realtimeArrivalList");

                    if (arrivalList == null || arrivalList.isEmpty()) {
                        log.info("No arrival data for station: {} (body keys: {})", stationName, body.keySet());
                        return new SubwayArrivalResponse(stationName, List.of(), nowTimestamp());
                    }

                    for (Map<String, Object> item : arrivalList) {
                        arrivals.add(new SubwayArrival(
                                (String) item.getOrDefault("trainLineNm", ""),
                                (String) item.getOrDefault("arvlMsg2", ""),
                                (String) item.getOrDefault("updnLine", ""),
                                (String) item.getOrDefault("bstatnNm", ""),
                                (String) item.getOrDefault("arvlCd", ""),
                                String.valueOf(item.getOrDefault("subwayId", ""))
                        ));
                    }

                    log.info("Fetched {} arrivals for station: {}", arrivals.size(), stationName);
                    return new SubwayArrivalResponse(stationName, arrivals, nowTimestamp());
                })
                .onErrorResume(e -> {
                    log.warn("Failed to fetch arrivals for {}: {}", stationName, e.getMessage());
                    return Mono.just(new SubwayArrivalResponse(stationName, List.of(), nowTimestamp()));
                });
    }

    private String normalizeStationName(String name) {
        if (name == null) return "";
        String trimmed = name.trim();
        if (trimmed.endsWith("역")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String nowTimestamp() {
        return LocalDateTime.now().format(TIMESTAMP_FMT);
    }
}
