package com.ready4work.bus;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.ready4work.bus.dto.BusArrival;
import com.ready4work.bus.dto.BusArrivalResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class BusArrivalService {

    private final WebClient busStationWebClient;
    private final WebClient busArrivalWebClient;
    private final String stationServiceKey;
    private final String arrivalServiceKey;

    // 좌표 → stId 매핑 캐시: 24시간 TTL (정류소 위치는 거의 불변)
    private final ConcurrentHashMap<String, Mono<StationInfo>> stIdCache = new ConcurrentHashMap<>();
    // stId → 도착정보 캐시: 15초 TTL (실시간)
    private final ConcurrentHashMap<String, Mono<BusArrivalResponse>> arrivalCache = new ConcurrentHashMap<>();

    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final XmlMapper XML_MAPPER = new XmlMapper();

    private final String stationBaseUrl;
    private final String arrivalBaseUrl;

    public BusArrivalService(
            @Qualifier("busStationWebClient") WebClient busStationWebClient,
            @Qualifier("busArrivalWebClient") WebClient busArrivalWebClient,
            @Value("${datagokr.api.bus.station.key}") String stationServiceKey,
            @Value("${datagokr.api.bus.arrival.key}") String arrivalServiceKey,
            @Value("${datagokr.api.bus.station.base-url}") String stationBaseUrl,
            @Value("${datagokr.api.bus.arrival.base-url}") String arrivalBaseUrl
    ) {
        this.busStationWebClient = busStationWebClient;
        this.busArrivalWebClient = busArrivalWebClient;
        this.stationServiceKey = stationServiceKey;
        this.arrivalServiceKey = arrivalServiceKey;
        this.stationBaseUrl = stationBaseUrl;
        this.arrivalBaseUrl = arrivalBaseUrl;
        log.info("BusArrivalService initialized, station key present: {}, arrival key present: {}",
                stationServiceKey != null && !stationServiceKey.isBlank(),
                arrivalServiceKey != null && !arrivalServiceKey.isBlank());
    }

    public Mono<BusArrivalResponse> getArrivals(double lat, double lon, boolean refresh) {
        String coordKey = lat + "," + lon;
        if (refresh) {
            stIdCache.remove(coordKey);
        }

        return resolveStation(lat, lon, coordKey)
                .flatMap(station -> {
                    if (refresh) {
                        arrivalCache.remove(station.stId());
                    }
                    return fetchArrivals(station);
                })
                .switchIfEmpty(Mono.just(new BusArrivalResponse("", List.of(), nowTimestamp())));
    }

    private Mono<StationInfo> resolveStation(double lat, double lon, String coordKey) {
        return stIdCache.computeIfAbsent(coordKey, k ->
                fetchStationByPos(lat, lon).cache(Duration.ofHours(24))
        );
    }

    @SuppressWarnings("unchecked")
    private Mono<StationInfo> fetchStationByPos(double lat, double lon) {
        String fullUrl = stationBaseUrl + "/getStationByPos"
                + "?serviceKey=" + stationServiceKey
                + "&tmX=" + lon
                + "&tmY=" + lat
                + "&radius=200";

        return busStationWebClient.get()
                .uri(URI.create(fullUrl))
                .retrieve()
                .bodyToMono(String.class)
                .mapNotNull(this::parseXml)
                .flatMap(body -> {
                    log.debug("Station API response keys: {}", body.keySet());
                    Map<String, Object> msgBody = extractMsgBody(body);
                    if (msgBody == null) return Mono.empty();

                    List<Map<String, Object>> itemList = extractItemList(msgBody);
                    if (itemList == null || itemList.isEmpty()) {
                        log.info("No station found near lat={}, lon={}", lat, lon);
                        return Mono.empty();
                    }

                    // 가장 가까운 정류소 (첫 번째 결과)
                    Map<String, Object> nearest = itemList.getFirst();
                    String stId = String.valueOf(nearest.get("stId"));
                    String stNm = String.valueOf(nearest.getOrDefault("stNm", ""));
                    log.info("Resolved coordinates ({}, {}) → stId={}, stNm={}", lat, lon, stId, stNm);
                    return Mono.just(new StationInfo(stId, stNm));
                })
                .onErrorResume(e -> {
                    log.warn("Failed to resolve station by pos ({}, {}): {}", lat, lon, e.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<BusArrivalResponse> fetchArrivals(StationInfo station) {
        return arrivalCache.computeIfAbsent(station.stId(), k ->
                doFetchArrivals(station).cache(Duration.ofSeconds(15))
        );
    }

    @SuppressWarnings("unchecked")
    private Mono<BusArrivalResponse> doFetchArrivals(StationInfo station) {
        String fullUrl = arrivalBaseUrl + "/getArrInfoByStId"
                + "?serviceKey=" + arrivalServiceKey
                + "&stId=" + station.stId();

        return busArrivalWebClient.get()
                .uri(URI.create(fullUrl))
                .retrieve()
                .bodyToMono(String.class)
                .mapNotNull(this::parseXml)
                .map(body -> {
                    List<BusArrival> arrivals = new ArrayList<>();

                    Map<String, Object> msgBody = extractMsgBody(body);
                    if (msgBody == null) {
                        return new BusArrivalResponse(station.stNm(), List.of(), nowTimestamp());
                    }

                    List<Map<String, Object>> itemList = extractItemList(msgBody);
                    if (itemList == null || itemList.isEmpty()) {
                        log.info("No arrival data for stId={}, stNm={}", station.stId(), station.stNm());
                        return new BusArrivalResponse(station.stNm(), List.of(), nowTimestamp());
                    }

                    for (Map<String, Object> item : itemList) {
                        arrivals.add(new BusArrival(
                                String.valueOf(item.getOrDefault("rtNm", "")),
                                String.valueOf(item.getOrDefault("arrmsg1", "")),
                                String.valueOf(item.getOrDefault("arrmsg2", "")),
                                String.valueOf(item.getOrDefault("busRouteId", "")),
                                String.valueOf(item.getOrDefault("routeType", ""))
                        ));
                    }

                    log.info("Fetched {} bus arrivals for station: {} (stId={})", arrivals.size(), station.stNm(), station.stId());
                    return new BusArrivalResponse(station.stNm(), arrivals, nowTimestamp());
                })
                .onErrorResume(e -> {
                    log.warn("Failed to fetch bus arrivals for stId={}: {}", station.stId(), e.getMessage());
                    return Mono.just(new BusArrivalResponse(station.stNm(), List.of(), nowTimestamp()));
                });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMsgBody(Map<String, Object> body) {
        Object msgBodyObj = body.get("msgBody");
        if (msgBodyObj instanceof Map) {
            return (Map<String, Object>) msgBodyObj;
        }
        // BIS API 응답 구조: ServiceResult > msgBody
        Object serviceResult = body.get("ServiceResult");
        if (serviceResult instanceof Map) {
            Object innerMsgBody = ((Map<String, Object>) serviceResult).get("msgBody");
            if (innerMsgBody instanceof Map) {
                return (Map<String, Object>) innerMsgBody;
            }
        }
        log.warn("Cannot extract msgBody from response. Keys: {}, value types: {}",
                body.keySet(),
                body.entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue() == null ? "null" : e.getValue().getClass().getSimpleName()
                        )));
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractItemList(Map<String, Object> msgBody) {
        Object itemListObj = msgBody.get("itemList");
        if (itemListObj instanceof List) {
            return (List<Map<String, Object>>) itemListObj;
        }
        if (itemListObj instanceof Map) {
            // XML 단일 <itemList>: XmlMapper가 List 대신 Map으로 역직렬화
            return List.of((Map<String, Object>) itemListObj);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseXml(String xml) {
        try {
            return XML_MAPPER.readValue(xml, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse XML response: {}", e.getMessage());
            log.debug("XML body (first 500 chars): {}", xml.substring(0, Math.min(xml.length(), 500)));
            return null;
        }
    }

    private String nowTimestamp() {
        return LocalDateTime.now().format(TIMESTAMP_FMT);
    }

    record StationInfo(String stId, String stNm) {}
}
