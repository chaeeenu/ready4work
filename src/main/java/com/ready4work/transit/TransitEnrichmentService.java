package com.ready4work.transit;

import com.ready4work.bus.BusArrivalService;
import com.ready4work.bus.dto.BusArrival;
import com.ready4work.bus.dto.BusArrivalResponse;
import com.ready4work.subway.SubwayArrivalService;
import com.ready4work.subway.dto.SubwayArrival;
import com.ready4work.subway.dto.SubwayArrivalResponse;
import com.ready4work.transit.dto.TransitLeg;
import com.ready4work.transit.dto.TransitRoute;
import com.ready4work.transit.dto.TransitRouteResponse;
import com.ready4work.transit.util.ArrivalMessageParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransitEnrichmentService {

    private final SubwayArrivalService subwayArrivalService;
    private final BusArrivalService busArrivalService;

    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern DIRECTION_PATTERN = Pattern.compile("(\\S+(?:방면|행))");

    /**
     * TransitRouteResponse의 모든 경로에 실시간 도착정보를 enrichment한다.
     * 실패 시 원본을 그대로 반환 (fallback).
     */
    public Mono<TransitRouteResponse> enrich(TransitRouteResponse response) {
        if (response.routes() == null || response.routes().isEmpty()) {
            return Mono.just(response);
        }

        // 모든 경로에서 조회가 필요한 역/정류소를 수집 (중복 제거)
        Set<String> subwayStations = new LinkedHashSet<>();
        Set<String> busCoordKeys = new LinkedHashSet<>();
        Map<String, double[]> busCoordMap = new HashMap<>();

        for (TransitRoute route : response.routes()) {
            for (TransitLeg leg : route.legs()) {
                if ("subway".equals(leg.type()) && leg.startName() != null && !leg.startName().isBlank()) {
                    subwayStations.add(leg.startName());
                } else if ("bus".equals(leg.type()) && leg.busStopLat() != null && leg.busStopLon() != null) {
                    String key = leg.busStopLat() + "," + leg.busStopLon();
                    busCoordKeys.add(key);
                    busCoordMap.put(key, new double[]{leg.busStopLat(), leg.busStopLon()});
                }
            }
        }

        // 병렬로 실시간 데이터 조회
        Mono<Map<String, SubwayArrivalResponse>> subwayMono = fetchAllSubwayArrivals(subwayStations);
        Mono<Map<String, BusArrivalResponse>> busMono = fetchAllBusArrivals(busCoordKeys, busCoordMap);

        return Mono.zip(subwayMono, busMono)
                .map(tuple -> {
                    Map<String, SubwayArrivalResponse> subwayMap = tuple.getT1();
                    Map<String, BusArrivalResponse> busMap = tuple.getT2();
                    String enrichedAt = LocalDateTime.now().format(TIMESTAMP_FMT);

                    List<TransitRoute> enrichedRoutes = response.routes().stream()
                            .map(route -> enrichRoute(route, subwayMap, busMap, enrichedAt))
                            .toList();

                    return new TransitRouteResponse(response.origin(), response.destination(), enrichedRoutes);
                })
                .onErrorResume(e -> {
                    log.warn("Enrichment failed, returning original response: {}", e.getMessage());
                    return Mono.just(response);
                });
    }

    private Mono<Map<String, SubwayArrivalResponse>> fetchAllSubwayArrivals(Set<String> stations) {
        if (stations.isEmpty()) {
            return Mono.just(Map.of());
        }

        return Flux.fromIterable(new ArrayList<>(stations))
                .flatMap(station -> subwayArrivalService.getArrivals(station, false)
                        .map(resp -> Map.entry(station, resp))
                        .onErrorResume(e -> {
                            log.warn("Subway arrival fetch failed for {}: {}", station, e.getMessage());
                            return Mono.empty();
                        }))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .defaultIfEmpty(Map.of());
    }

    private Mono<Map<String, BusArrivalResponse>> fetchAllBusArrivals(Set<String> coordKeys, Map<String, double[]> coordMap) {
        if (coordKeys.isEmpty()) {
            return Mono.just(Map.of());
        }

        return Flux.fromIterable(new ArrayList<>(coordKeys))
                .flatMap(key -> {
                    double[] coords = coordMap.get(key);
                    return busArrivalService.getArrivals(coords[0], coords[1], false)
                            .map(resp -> Map.entry(key, resp))
                            .onErrorResume(e -> {
                                log.warn("Bus arrival fetch failed for {}: {}", key, e.getMessage());
                                return Mono.empty();
                            });
                })
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .defaultIfEmpty(Map.of());
    }

    private TransitRoute enrichRoute(
            TransitRoute route,
            Map<String, SubwayArrivalResponse> subwayMap,
            Map<String, BusArrivalResponse> busMap,
            String enrichedAt
    ) {
        List<TransitLeg> enrichedLegs = route.legs().stream()
                .map(leg -> enrichLeg(leg, subwayMap, busMap))
                .toList();

        int adjustedTotal = enrichedLegs.stream()
                .mapToInt(TransitLeg::adjustedTime)
                .sum();

        return route.withEnrichment(adjustedTotal, enrichedLegs, enrichedAt);
    }

    private TransitLeg enrichLeg(
            TransitLeg leg,
            Map<String, SubwayArrivalResponse> subwayMap,
            Map<String, BusArrivalResponse> busMap
    ) {
        if ("subway".equals(leg.type())) {
            return enrichSubwayLeg(leg, subwayMap);
        } else if ("bus".equals(leg.type())) {
            return enrichBusLeg(leg, busMap);
        }
        return leg; // walk 등은 그대로
    }

    private TransitLeg enrichSubwayLeg(TransitLeg leg, Map<String, SubwayArrivalResponse> subwayMap) {
        SubwayArrivalResponse response = subwayMap.get(leg.startName());
        if (response == null || response.arrivals().isEmpty()) {
            log.info("No subway arrivals for station '{}' (lineName={})", leg.startName(), leg.lineName());
            return leg;
        }

        List<SubwayArrival> arrivals = response.arrivals();
        SubwayArrival matched = matchSubwayArrival(leg, arrivals);
        if (matched == null) {
            log.info("No subway arrival match: station='{}', lineName='{}', endName='{}', candidates={}",
                    leg.startName(), leg.lineName(), leg.endName(), arrivals.size());
            return leg;
        }

        // 방면 텍스트 포함 메시지 조합: "삼성방면 · 3분후 도착"
        String dirText = extractDirectionText(matched.trainLineNm());
        String arrivalMessage = dirText != null
                ? dirText + " · " + matched.arvlMsg2()
                : matched.arvlMsg2();

        // 두 번째 열차: 같은 호선 + 같은 방향 + matched 제외
        String legLineNumber = extractNumbers(leg.lineName());
        SubwayArrival second = arrivals.stream()
                .filter(a -> !a.equals(matched))
                .filter(a -> {
                    String trainLineNum = extractNumbers(a.trainLineNm());
                    return !legLineNumber.isEmpty() && trainLineNum.contains(legLineNumber);
                })
                .filter(a -> matched.updnLine() == null || matched.updnLine().equals(a.updnLine()))
                .min(Comparator.comparingInt(a -> ArrivalMessageParser.parseMinutes(a.arvlMsg2()).orElse(Integer.MAX_VALUE)))
                .orElse(null);

        String arrivalMessage2 = null;
        if (second != null) {
            String dir2 = extractDirectionText(second.trainLineNm());
            arrivalMessage2 = dir2 != null
                    ? dir2 + " · " + second.arvlMsg2()
                    : second.arvlMsg2();
        }

        OptionalInt waitMinutes = ArrivalMessageParser.parseMinutes(matched.arvlMsg2());
        if (waitMinutes.isEmpty()) {
            return leg.withArrivalInfo(null, arrivalMessage, arrivalMessage2, leg.sectionTime());
        }

        int wait = waitMinutes.getAsInt();
        int rideTime = estimateRideTime(leg);
        int adjusted = wait + rideTime;

        return leg.withArrivalInfo(wait, arrivalMessage, arrivalMessage2, adjusted);
    }

    private TransitLeg enrichBusLeg(TransitLeg leg, Map<String, BusArrivalResponse> busMap) {
        if (leg.busStopLat() == null || leg.busStopLon() == null) {
            return leg;
        }

        String coordKey = leg.busStopLat() + "," + leg.busStopLon();
        BusArrivalResponse arrivals = busMap.get(coordKey);
        if (arrivals == null || arrivals.arrivals().isEmpty()) {
            log.info("No bus arrivals for stop ({}) lineName={}", coordKey, leg.lineName());
            return leg;
        }

        // routeName 숫자 매칭 (프론트엔드 로직을 백엔드로 이동)
        String legRouteNumber = extractNumbers(leg.lineName());
        BusArrival matched = arrivals.arrivals().stream()
                .filter(a -> legRouteNumber.equals(extractNumbers(a.routeName())))
                .findFirst()
                .orElse(null);

        if (matched == null) {
            log.info("No bus arrival match: lineName='{}' (number='{}'), routeNames={}",
                    leg.lineName(), legRouteNumber,
                    arrivals.arrivals().stream().map(BusArrival::routeName).toList());
            return leg;
        }

        OptionalInt waitMinutes = ArrivalMessageParser.parseMinutes(matched.arvlMsg1());
        String msg1 = matched.arvlMsg1();
        String msg2 = matched.arvlMsg2();

        if (waitMinutes.isEmpty()) {
            return leg.withArrivalInfo(null, msg1, msg2, leg.sectionTime());
        }

        int wait = waitMinutes.getAsInt();
        int rideTime = estimateRideTime(leg);
        int adjusted = wait + rideTime;

        return leg.withArrivalInfo(wait, msg1, msg2, adjusted);
    }

    /**
     * 지하철 도착정보에서 해당 leg에 맞는 열차를 매칭한다.
     * 1) 호선 매칭 (lineName의 숫자와 trainLineNm의 숫자 비교)
     * 2) 방면 추론 (endName이 trainLineNm이나 bstatnNm에 포함되는지)
     * 3) fallback: 호선만 맞으면 가장 빠른 도착 열차
     */
    SubwayArrival matchSubwayArrival(TransitLeg leg, List<SubwayArrival> arrivals) {
        String legLineNumber = extractNumbers(leg.lineName());

        // 호선 매칭 후보: subwayId 우선, fallback으로 trainLineNm 숫자 추출
        List<SubwayArrival> lineCandidates = arrivals.stream()
                .filter(a -> subwayLineMatches(legLineNumber, a))
                .toList();

        if (lineCandidates.isEmpty()) {
            log.info("Line matching failed: legLine='{}' (number='{}'), trainLineNms={}",
                    leg.lineName(), legLineNumber,
                    arrivals.stream().map(SubwayArrival::trainLineNm).toList());
            return null;
        }

        // 방면 매칭: endName이 trainLineNm 또는 bstatnNm에 포함
        String endName = leg.endName() != null ? leg.endName().replace("역", "") : "";
        if (!endName.isEmpty()) {
            Optional<SubwayArrival> directionMatch = lineCandidates.stream()
                    .filter(a -> {
                        String trainLine = a.trainLineNm() != null ? a.trainLineNm() : "";
                        String bstatn = a.bstatnNm() != null ? a.bstatnNm() : "";
                        return trainLine.contains(endName) || bstatn.contains(endName);
                    })
                    .min(Comparator.comparingInt(a -> ArrivalMessageParser.parseMinutes(a.arvlMsg2()).orElse(Integer.MAX_VALUE)));

            if (directionMatch.isPresent()) {
                return directionMatch.get();
            }
        }

        // fallback: 호선만 맞는 후보 중 가장 빠른 열차
        return lineCandidates.stream()
                .min(Comparator.comparingInt(a -> ArrivalMessageParser.parseMinutes(a.arvlMsg2()).orElse(Integer.MAX_VALUE)))
                .orElse(null);
    }

    /**
     * TMAP sectionTime에서 평균 대기시간(5분 추정)을 빼고,
     * stationCount 기반 최소 탑승시간과 비교하여 탑승시간을 추정한다.
     */
    private int estimateRideTime(TransitLeg leg) {
        int fromSection = Math.max(leg.sectionTime() - 5, 1);
        int fromStations = leg.stationCount() * 2;
        return Math.max(fromSection, fromStations);
    }

    static String extractNumbers(String text) {
        if (text == null) return "";
        return text.replaceAll("[^0-9]", "");
    }

    /**
     * 지하철 도착 항목의 subwayId 또는 trainLineNm에서 호선을 추출해 legLineNumber와 비교한다.
     * 서울 API의 trainLineNm은 "개화행 - 석촌고분방면"처럼 노선 번호가 없으므로 subwayId를 우선 사용.
     */
    private static boolean subwayLineMatches(String legLineNumber, SubwayArrival arrival) {
        if (legLineNumber.isEmpty()) return false;

        // 1차: subwayId 기반 (1001→"1", 1009→"9")
        String idLineNum = subwayLineNumberFromId(arrival.subwayId());
        if (!idLineNum.isEmpty()) {
            return idLineNum.equals(legLineNumber);
        }

        // 2차: trainLineNm 숫자 추출 (fallback)
        String trainLineNum = extractNumbers(arrival.trainLineNm());
        return !trainLineNum.isEmpty() && trainLineNum.contains(legLineNumber);
    }

    /**
     * 서울 지하철 API subwayId → ODsay extractNumbers(lineName) 형식의 호선 번호 문자열로 변환.
     * 1001~1009 → "1"~"9", 특수 노선은 ODsay subwayCode 기반 매핑.
     */
    private static String subwayLineNumberFromId(String subwayId) {
        if (subwayId == null || subwayId.isBlank() || "null".equals(subwayId)) return "";
        try {
            int id = Integer.parseInt(subwayId.trim());
            if (id >= 1001 && id <= 1009) return String.valueOf(id - 1000); // 1~9호선
            return switch (id) {
                case 1065 -> "101"; // 공항철도
                case 1032 -> "104"; // 경의중앙선
                case 1067 -> "108"; // 경춘선
                case 1063 -> "109"; // 신분당선
                case 1075 -> "111"; // 수인분당선
                case 1077 -> "112"; // 우이신설선
                case 1092 -> "113"; // 서해선
                case 1093 -> "114"; // 김포골드라인
                default   -> "";
            };
        } catch (NumberFormatException e) {
            return "";
        }
    }

    /**
     * trainLineNm에서 방면/행 텍스트를 추출한다.
     * "2호선 삼성행 - 삼성방면" → "삼성방면" (마지막 매칭 선택)
     * "9호선 급행 개화행"       → "개화행"
     * "1호선 인천행"            → "인천행"
     */
    static String extractDirectionText(String trainLineNm) {
        if (trainLineNm == null || trainLineNm.isBlank()) {
            return null;
        }
        Matcher m = DIRECTION_PATTERN.matcher(trainLineNm);
        String last = null;
        while (m.find()) {
            last = m.group(1);
        }
        return last;
    }
}
