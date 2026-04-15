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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class TransitService {

    private final WebClient odsayWebClient;
    private final WebClient kakaoWebClient;

    // 좌표 캐시: 24시간 TTL
    private final ConcurrentHashMap<String, Mono<double[]>> coordCache = new ConcurrentHashMap<>();
    // 경로 캐시: 30분 TTL
    private final ConcurrentHashMap<String, Mono<TransitRouteResponse>> routeCache = new ConcurrentHashMap<>();

    public TransitService(
            @Qualifier("odsayWebClient") WebClient odsayWebClient,
            @Qualifier("kakaoWebClient") WebClient kakaoWebClient
    ) {
        this.odsayWebClient = odsayWebClient;
        this.kakaoWebClient = kakaoWebClient;
        log.info("TransitService initialized with ODsay API");
    }

    public Mono<TransitRouteResponse> getRoutes(String origin, String destination, boolean refresh) {
        String cacheKey = origin + "|" + destination;
        if (refresh) {
            log.info("Refresh requested, clearing route cache for: {}", cacheKey);
            routeCache.remove(cacheKey);
        }
        return routeCache.computeIfAbsent(cacheKey, k ->
                fetchRoutes(origin, destination).cache(Duration.ofMinutes(30))
        );
    }

    private Mono<TransitRouteResponse> fetchRoutes(String origin, String destination) {
        Mono<double[]> originCoord = getCoordinates(origin);
        Mono<double[]> destCoord = getCoordinates(destination);

        return Mono.zip(originCoord, destCoord)
                .flatMap(tuple -> {
                    double[] oCoord = tuple.getT1();
                    double[] dCoord = tuple.getT2();
                    return searchPath(oCoord[0], oCoord[1], dCoord[0], dCoord[1]);
                })
                .map(pathResult -> buildResponse(origin, destination, pathResult))
                .onErrorResume(e -> {
                    log.warn("ODsay route search failed: {}", e.getMessage());
                    return Mono.just(new TransitRouteResponse(origin, destination, List.of()));
                });
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
    private Mono<Map<String, Object>> searchPath(double sx, double sy, double ex, double ey) {
        log.info("ODsay searchPath: SX={}, SY={}, EX={}, EY={}", sx, sy, ex, ey);

        return odsayWebClient.get()
                .uri(uri -> uri.path("/searchPubTransPathT")
                        .queryParam("SX", sx)
                        .queryParam("SY", sy)
                        .queryParam("EX", ex)
                        .queryParam("EY", ey)
                        .build())
                .retrieve()
                .bodyToMono((Class<Map<String, Object>>) (Class) Map.class)
                .doOnNext(response -> log.info("ODsay response keys: {}", response.keySet()));
    }

    @SuppressWarnings("unchecked")
    private TransitRouteResponse buildResponse(String origin, String destination, Map<String, Object> response) {
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        if (result == null) {
            log.warn("ODsay response has no 'result'. Full response: {}", response);
            return new TransitRouteResponse(origin, destination, List.of());
        }

        // ODsay 오류 응답 처리
        if (result.containsKey("error")) {
            log.warn("ODsay returned error: {}", result.get("error"));
            return new TransitRouteResponse(origin, destination, List.of());
        }

        List<Map<String, Object>> paths = (List<Map<String, Object>>) result.get("path");
        if (paths == null || paths.isEmpty()) {
            log.warn("ODsay returned no paths");
            return new TransitRouteResponse(origin, destination, List.of());
        }

        TransitRoute optimal = null;      // pathType 1: 최적 경로
        TransitRoute lessTransfer = null; // pathType 3: 환승 적은 경로

        for (Map<String, Object> path : paths) {
            int pathType = ((Number) path.get("pathType")).intValue();
            if (pathType == 1 && optimal == null) {
                optimal = mapPath(path);
            } else if (pathType == 3 && lessTransfer == null) {
                lessTransfer = mapPath(path);
            }
        }

        List<TransitRoute> routes;
        if (optimal == null && lessTransfer == null) {
            routes = List.of();
        } else if (optimal == null) {
            routes = List.of(lessTransfer);
        } else if (lessTransfer == null || optimal.equals(lessTransfer)) {
            routes = List.of(optimal);
        } else {
            routes = List.of(optimal, lessTransfer);
        }

        return new TransitRouteResponse(origin, destination, routes);
    }

    @SuppressWarnings("unchecked")
    private TransitRoute mapPath(Map<String, Object> path) {
        Map<String, Object> info = (Map<String, Object>) path.get("info");
        int totalTime = ((Number) info.get("totalTime")).intValue();
        // ODsay 요금 필드는 info.payment (totalFare 아님)
        int totalFare = ((Number) info.getOrDefault("payment", 0)).intValue();
        int busCount = ((Number) info.getOrDefault("busTransitCount", 0)).intValue();
        int subwayCount = ((Number) info.getOrDefault("subwayTransitCount", 0)).intValue();
        int transferCount = Math.max(0, busCount + subwayCount - 1);

        List<Map<String, Object>> subPaths = (List<Map<String, Object>>) path.get("subPath");
        List<TransitLeg> legs = new ArrayList<>();
        List<String> lineNames = new ArrayList<>();
        int walkTime = 0;

        if (subPaths != null) {
            for (Map<String, Object> subPath : subPaths) {
                int trafficType = ((Number) subPath.get("trafficType")).intValue();
                int sectionTime = ((Number) subPath.getOrDefault("sectionTime", 0)).intValue();

                if (trafficType == 3) {
                    // 도보
                    walkTime += sectionTime;
                    if (sectionTime > 0) {
                        legs.add(TransitLeg.ofBasic("walk", null, null, null, null, 0, sectionTime, null, null));
                    }
                } else if (trafficType == 1) {
                    // 지하철
                    // ODsay: startName/endName은 subPath 직속 필드
                    List<Map<String, Object>> lanes = (List<Map<String, Object>>) subPath.get("lane");
                    int stationCount = ((Number) subPath.getOrDefault("stationCount", 0)).intValue();
                    String startName = (String) subPath.getOrDefault("startName", "");
                    String endName   = (String) subPath.getOrDefault("endName", "");

                    String lineName = "";
                    String lineColor = "#888888";
                    if (lanes != null && !lanes.isEmpty()) {
                        lineName = (String) lanes.get(0).get("name");
                        String color = (String) lanes.get(0).get("color");
                        if (color != null && !color.isBlank()) {
                            lineColor = "#" + color.replaceFirst("^#", "");
                        } else {
                            // color 필드가 비어 있을 때 subwayCode로 표준 색상 매핑
                            Object codeObj = lanes.get(0).get("subwayCode");
                            if (codeObj != null) {
                                lineColor = subwayColorByCode(((Number) codeObj).intValue());
                            }
                        }
                    }

                    lineNames.add(lineName != null ? lineName : "지하철");
                    legs.add(TransitLeg.ofBasic("subway", lineName, lineColor, startName, endName, stationCount, sectionTime, null, null));

                } else if (trafficType == 2) {
                    // 버스
                    // ODsay: startName/endName/startX(경도)/startY(위도)는 subPath 직속 필드
                    List<Map<String, Object>> lanes = (List<Map<String, Object>>) subPath.get("lane");
                    int stationCount = ((Number) subPath.getOrDefault("stationCount", 0)).intValue();
                    String startName = (String) subPath.getOrDefault("startName", "");
                    String endName   = (String) subPath.getOrDefault("endName", "");
                    Double busStopLat = parseDouble(subPath.get("startY")); // Y = 위도(latitude)
                    Double busStopLon = parseDouble(subPath.get("startX")); // X = 경도(longitude)

                    String lineName = "";
                    String lineColor = "#888888";
                    if (lanes != null && !lanes.isEmpty()) {
                        lineName = (String) lanes.get(0).get("busNo");
                        String color = (String) lanes.get(0).get("color");
                        if (color != null && !color.isBlank()) {
                            lineColor = "#" + color.replaceFirst("^#", "");
                        }
                    }

                    lineNames.add(lineName != null ? lineName : "버스");
                    legs.add(TransitLeg.ofBasic("bus", lineName, lineColor, startName, endName, stationCount, sectionTime, busStopLat, busStopLon));
                }
            }
        }

        String summary = String.join(" → ", lineNames);
        return TransitRoute.ofBasic(totalTime, transferCount, totalFare, walkTime, summary, legs);
    }

    /** ODsay subwayCode → 서울 지하철 표준 노선 색상 */
    private static String subwayColorByCode(int code) {
        return switch (code) {
            case 1   -> "#0052A4"; // 1호선 (진파랑)
            case 2   -> "#00A84D"; // 2호선 (초록)
            case 3   -> "#EF7C1C"; // 3호선 (주황)
            case 4   -> "#00A5DE"; // 4호선 (하늘)
            case 5   -> "#996CAC"; // 5호선 (보라)
            case 6   -> "#CD7C2F"; // 6호선 (갈색)
            case 7   -> "#747F00"; // 7호선 (올리브)
            case 8   -> "#E6186C"; // 8호선 (분홍)
            case 9   -> "#BDB092"; // 9호선 (금색)
            case 101 -> "#0090D2"; // 공항철도
            case 104 -> "#77C4A3"; // 경의중앙선
            case 107 -> "#789F39"; // 에버라인
            case 108 -> "#179C74"; // 경춘선
            case 109 -> "#D4003B"; // 신분당선
            case 110 -> "#FDA600"; // 의정부경전철
            case 111 -> "#F5A200"; // 수인분당선
            case 112 -> "#B7C452"; // 우이신설선
            case 113 -> "#8FC2E8"; // 서해선
            case 114 -> "#947DC4"; // 김포골드라인
            default  -> "#888888";
        };
    }

    private Double parseDouble(Object value) {
        if (value == null) return null;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
