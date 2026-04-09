package com.ready4work.subway;

import com.ready4work.subway.dto.SubwayAlert;
import com.ready4work.subway.dto.SubwayAlertResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SubwayAlertService {

    private final WebClient dataGoKrWebClient;
    private final String serviceKey;
    private final ConcurrentHashMap<String, Mono<SubwayAlertResponse>> cache = new ConcurrentHashMap<>();

    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> DANGER_KEYWORDS = Set.of("운행중지", "사고", "중단");

    public SubwayAlertService(
            @Qualifier("dataGoKrWebClient") WebClient dataGoKrWebClient,
            @Value("${datagokr.api.key}") String serviceKey
    ) {
        this.dataGoKrWebClient = dataGoKrWebClient;
        this.serviceKey = serviceKey;
        log.info("SubwayAlertService initialized, service key present: {}", serviceKey != null && !serviceKey.isBlank());
    }

    public Mono<SubwayAlertResponse> getAlerts(boolean refresh) {
        if (refresh) {
            cache.clear();
        }
        return cache.computeIfAbsent("alerts", k ->
                fetchAlerts().cache(Duration.ofMinutes(1))
        );
    }

    @SuppressWarnings("unchecked")
    private Mono<SubwayAlertResponse> fetchAlerts() {
        String baseUrl = "https://apis.data.go.kr/B553766/ntce/getNtceList";
        String fullUrl = baseUrl + "?serviceKey=" + serviceKey + "&pageNo=1&numOfRows=1&dataType=json";

        return dataGoKrWebClient.get()
                .uri(URI.create(fullUrl))
                .retrieve()
                .bodyToMono(Map.class)
                .map(body -> {
                    List<SubwayAlert> alerts = new ArrayList<>();

                    Map<String, Object> response = (Map<String, Object>) body.get("response");
                    if (response == null) {
                        log.debug("response not found in API response");
                        return new SubwayAlertResponse(List.of(), nowTimestamp());
                    }

                    Map<String, Object> responseBody = (Map<String, Object>) response.get("body");
                    if (responseBody == null) {
                        log.debug("response.body not found in API response");
                        return new SubwayAlertResponse(List.of(), nowTimestamp());
                    }

                    Object itemsObj = responseBody.get("items");
                    List<Map<String, Object>> items;
                    if (itemsObj instanceof List) {
                        items = (List<Map<String, Object>>) itemsObj;
                    } else if (itemsObj instanceof Map) {
                        Object itemList = ((Map<String, Object>) itemsObj).get("item");
                        if (itemList instanceof List) {
                            items = (List<Map<String, Object>>) itemList;
                        } else {
                            items = List.of();
                        }
                    } else {
                        items = List.of();
                    }

                    for (Map<String, Object> item : items) {
                        String noftTtl = String.valueOf(item.getOrDefault("noftTtl", item.get("ntftTtl")));
                        String noftCn = String.valueOf(item.getOrDefault("noftCn", item.get("noftCn")));
                        String noftOcrnDt = String.valueOf(item.getOrDefault("noftOcrnDt", item.get("noftOcrnDt")));
                        String lineNmLst = String.valueOf(item.getOrDefault("lineNmLst", item.get("lineNmLst")));
                        String stnSctnCdLst = String.valueOf(item.getOrDefault("stnSctnCdLst", item.get("stnSctnCdLst")));
                        String xcseSitnBgngDt = String.valueOf(item.getOrDefault("xcseSitnBgngDt", item.get("xcseSitnBgngDt")));
                        String xcseSitnEndDt = String.valueOf(item.getOrDefault("xcseSitnEndDt", item.get("xcseSitnEndDt")));
                        String nonstopYn = String.valueOf(item.getOrDefault("nonstopYn", item.get("nonstopYn")));

                        /*log.info("Fetched alert: noftTtl={}, noftCn={}, noftOcrnDt={}, lineNmLst={}, stnSctnCdLst={}, xcseSitnBgngDt={}, xcseSitnEndDt={}, nonstopYn={}",
                                noftTtl, noftCn, noftOcrnDt, lineNmLst, stnSctnCdLst, xcseSitnBgngDt, xcseSitnEndDt, nonstopYn);*/
                        alerts.add(new SubwayAlert(noftTtl, noftCn, noftOcrnDt, lineNmLst, stnSctnCdLst, xcseSitnBgngDt, xcseSitnEndDt, nonstopYn));
                    }

                    LocalDate today = LocalDate.now();
                    List<SubwayAlert> filtered = alerts.stream()
                            .filter(a -> isActiveToday(a, today))
                            .toList();

                    log.info("Fetched {} subway alerts from data.go.kr, {} active today", alerts.size(), filtered.size());
                    return new SubwayAlertResponse(filtered, nowTimestamp());
                })
                .onErrorResume(e -> {
                    log.warn("Failed to fetch subway alerts: {}", e.getMessage());
                    return Mono.just(new SubwayAlertResponse(List.of(), nowTimestamp()));
                });
    }

    private boolean isActiveToday(SubwayAlert alert, LocalDate today) {
        try {
            String begin = alert.xcseSitnBgngDt();
            String end = alert.xcseSitnEndDt();
            if (begin == null || "null".equals(begin) || begin.isBlank()) return true;
            LocalDate startDate = parseDate(begin);
            LocalDate endDate = (end != null && !"null".equals(end) && !end.isBlank()) ? parseDate(end) : startDate;
            return !today.isBefore(startDate) && !today.isAfter(endDate);
        } catch (Exception e) {
            log.debug("Failed to parse alert date, including alert: {}", e.getMessage());
            return true;
        }
    }

    private LocalDate parseDate(String dateStr) {
        String digits = dateStr.replaceAll("[^0-9]", "");
        if (digits.length() >= 8) {
            return LocalDate.parse(digits.substring(0, 8), DateTimeFormatter.ofPattern("yyyyMMdd"));
        }
        return LocalDate.parse(dateStr.substring(0, 10));
    }

    private String classifySeverity(String message) {
        if (message == null) return "warning";
        for (String keyword : DANGER_KEYWORDS) {
            if (message.contains(keyword)) return "danger";
        }
        return "warning";
    }

    private String nowTimestamp() {
        return LocalDateTime.now().format(TIMESTAMP_FMT);
    }
}
