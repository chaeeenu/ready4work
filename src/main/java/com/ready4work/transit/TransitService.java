package com.ready4work.transit;

import com.ready4work.transit.dto.TransitLeg;
import com.ready4work.transit.dto.TransitRoute;
import com.ready4work.transit.dto.TransitRouteResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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
public class TransitService {

    private final WebClient tmapWebClient;
    private final WebClient kakaoWebClient;

    private static final DateTimeFormatter DTIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    // 좌표 캐시: 24시간 TTL
    private final ConcurrentHashMap<String, Mono<double[]>> coordCache = new ConcurrentHashMap<>();
    // 경로 캐시: 5분 TTL
    private final ConcurrentHashMap<String, Mono<TransitRouteResponse>> routeCache = new ConcurrentHashMap<>();

    public TransitService(
            @Qualifier("tmapWebClient") WebClient tmapWebClient,
            @Qualifier("kakaoWebClient") WebClient kakaoWebClient
    ) {
        this.tmapWebClient = tmapWebClient;
        this.kakaoWebClient = kakaoWebClient;
        log.info("TransitService initialized with TMAP API");
    }

    public Mono<TransitRouteResponse> getRoutes(String origin, String destination, String departureTime, boolean refresh) {
        if (departureTime == null || departureTime.isBlank()) {
            departureTime = LocalDateTime.now().format(DTIME_FORMAT);
        }
        // 분 단위로 캐시 키 생성 (YYYYMMDDHHmm)
        String cacheKey = origin + "|" + destination + "|" + departureTime;
        if (refresh) {
            log.info("Refresh requested, clearing route cache for: {}", cacheKey);
            routeCache.remove(cacheKey);
        }
        String finalDepartureTime = departureTime;
        return routeCache.computeIfAbsent(cacheKey, k ->
                fetchRoutes(origin, destination, finalDepartureTime).cache(Duration.ofMinutes(5))
        );
    }

    private Mono<TransitRouteResponse> fetchRoutes(String origin, String destination, String departureTime) {
        Mono<double[]> originCoord = getCoordinates(origin);
        Mono<double[]> destCoord = getCoordinates(destination);

        return Mono.zip(originCoord, destCoord)
                .flatMap(tuple -> {
                    double[] oCoord = tuple.getT1();
                    double[] dCoord = tuple.getT2();
                    return searchPath(oCoord[0], oCoord[1], dCoord[0], dCoord[1], departureTime);
                })
                .map(pathResult -> buildResponse(origin, destination, pathResult));
    }

    private Mono<double[]> getCoordinates(String placeName) {
        return coordCache.computeIfAbsent(placeName, k ->
                geocode(placeName).cache(Duration.ofHours(24))
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Mono<double[]> geocode(String query) {
        return searchByAddress(query)
                .switchIfEmpty(searchByKeyword(query))
                .doOnNext(coord -> log.info("Geocode '{}' → lon={}, lat={}", query, coord[0], coord[1]))
                .switchIfEmpty(Mono.error(new RuntimeException("장소를 찾을 수 없습니다: " + query)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Mono<double[]> searchByAddress(String query) {
        return kakaoWebClient.get()
                .uri(uri -> uri.path("/v2/local/search/address.json")
                        .queryParam("query", query)
                        .build())
                .retrieve()
                .bodyToMono((Class<Map<String, Object>>) (Class) Map.class)
                .flatMap(response -> {
                    List<Map<String, Object>> documents =
                            (List<Map<String, Object>>) response.get("documents");
                    if (documents == null || documents.isEmpty()) {
                        return Mono.empty();
                    }
                    Map<String, Object> first = documents.getFirst();
                    double x = Double.parseDouble(first.get("x").toString());
                    double y = Double.parseDouble(first.get("y").toString());
                    return Mono.just(new double[]{x, y});
                });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Mono<double[]> searchByKeyword(String query) {
        return kakaoWebClient.get()
                .uri(uri -> uri.path("/v2/local/search/keyword.json")
                        .queryParam("query", query)
                        .build())
                .retrieve()
                .bodyToMono((Class<Map<String, Object>>) (Class) Map.class)
                .flatMap(response -> {
                    List<Map<String, Object>> documents =
                            (List<Map<String, Object>>) response.get("documents");
                    if (documents == null || documents.isEmpty()) {
                        return Mono.empty();
                    }
                    Map<String, Object> first = documents.getFirst();
                    double x = Double.parseDouble(first.get("x").toString());
                    double y = Double.parseDouble(first.get("y").toString());
                    return Mono.just(new double[]{x, y});
                });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Mono<Map<String, Object>> searchPath(double sx, double sy, double ex, double ey, String searchDtime) {
        log.info("TMAP searchPath: startX={}, startY={}, endX={}, endY={}, searchDtime={}", sx, sy, ex, ey, searchDtime);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("startX", String.valueOf(sx));
        body.put("startY", String.valueOf(sy));
        body.put("endX", String.valueOf(ex));
        body.put("endY", String.valueOf(ey));
        body.put("format", "json");
        body.put("count", 5);
        body.put("searchDtime", searchDtime);

        return tmapWebClient.post()
                .uri("/transit/routes")
                .bodyValue(body)
                .retrieve()
                .bodyToMono((Class<Map<String, Object>>) (Class) Map.class)
                .doOnNext(response -> log.info("TMAP response keys: {}", response.keySet()));
    }

    @SuppressWarnings("unchecked")
    private TransitRouteResponse buildResponse(String origin, String destination, Map<String, Object> response) {
        Map<String, Object> metaData = (Map<String, Object>) response.get("metaData");
        if (metaData == null) {
            log.warn("TMAP response has no 'metaData'. Full response: {}", response);
            return new TransitRouteResponse(origin, destination, List.of());
        }

        Map<String, Object> plan = (Map<String, Object>) metaData.get("plan");
        if (plan == null) {
            log.warn("TMAP response has no 'plan'. metaData keys: {}", metaData.keySet());
            return new TransitRouteResponse(origin, destination, List.of());
        }

        List<Map<String, Object>> itineraries = (List<Map<String, Object>>) plan.get("itineraries");
        if (itineraries == null || itineraries.isEmpty()) {
            log.warn("TMAP returned no itineraries");
            return new TransitRouteResponse(origin, destination, List.of());
        }

        List<TransitRoute> allRoutes = itineraries.stream()
                .map(this::mapItinerary)
                .toList();

        TransitRoute fastest = allRoutes.stream()
                .min(Comparator.comparingInt(TransitRoute::totalTime))
                .orElse(null);

        TransitRoute leastTransfer = allRoutes.stream()
                .min(Comparator.comparingInt(TransitRoute::transferCount)
                        .thenComparingInt(TransitRoute::totalTime))
                .orElse(null);

        List<TransitRoute> routes;
        if (fastest == null) {
            routes = List.of();
        } else if (fastest.equals(leastTransfer)) {
            routes = List.of(fastest);
        } else {
            routes = List.of(fastest, leastTransfer);
        }

        return new TransitRouteResponse(origin, destination, routes);
    }

    @SuppressWarnings("unchecked")
    private TransitRoute mapItinerary(Map<String, Object> itinerary) {
        int totalTimeSec = ((Number) itinerary.get("totalTime")).intValue();
        int totalTime = totalTimeSec / 60;
        int transferCount = ((Number) itinerary.get("transferCount")).intValue();

        Map<String, Object> fare = (Map<String, Object>) itinerary.get("fare");
        int totalCost = 0;
        if (fare != null) {
            Map<String, Object> regular = (Map<String, Object>) fare.get("regular");
            if (regular != null) {
                totalCost = ((Number) regular.get("totalFare")).intValue();
            }
        }

        int totalWalkTimeSec = ((Number) itinerary.get("totalWalkTime")).intValue();
        int walkTime = totalWalkTimeSec / 60;

        List<Map<String, Object>> legs = (List<Map<String, Object>>) itinerary.get("legs");
        List<TransitLeg> transitLegs = new ArrayList<>();
        List<String> lineNames = new ArrayList<>();

        for (Map<String, Object> leg : legs) {
            String mode = ((String) leg.get("mode")).toLowerCase();
            int sectionTimeSec = ((Number) leg.get("sectionTime")).intValue();
            int sectionTime = sectionTimeSec / 60;

            if ("walk".equals(mode)) {
                if (sectionTime > 0) {
                    transitLegs.add(new TransitLeg("walk", null, null, null, null, 0, sectionTime));
                }
            } else {
                String route = (String) leg.get("route");
                String routeColor = (String) leg.get("routeColor");
                String lineColor = (routeColor != null && !routeColor.isBlank())
                        ? "#" + routeColor.replaceFirst("^#", "")
                        : "#888888";

                Map<String, Object> start = (Map<String, Object>) leg.get("start");
                Map<String, Object> end = (Map<String, Object>) leg.get("end");
                String startName = start != null ? (String) start.get("name") : "";
                String endName = end != null ? (String) end.get("name") : "";

                int stationCount = 0;
                Map<String, Object> passStopList = (Map<String, Object>) leg.get("passStopList");
                if (passStopList != null) {
                    List<?> stations = (List<?>) passStopList.get("stations");
                    if (stations != null && stations.size() > 1) {
                        stationCount = stations.size() - 1;
                    }
                }

                String type = "bus".equals(mode) ? "bus" : "subway";
                String lineName = route != null ? route : (type.equals("bus") ? "버스" : "지하철");
                lineNames.add(lineName);
                transitLegs.add(new TransitLeg(type, lineName, lineColor, startName, endName, stationCount, sectionTime));
            }
        }

        String summary = String.join(" → ", lineNames);
        return new TransitRoute(totalTime, transferCount, totalCost, walkTime, summary, transitLegs);
    }
}
