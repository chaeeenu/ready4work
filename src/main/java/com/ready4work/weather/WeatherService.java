package com.ready4work.weather;

import com.ready4work.weather.dto.CurrentWeather;
import com.ready4work.weather.dto.HourlyForecast;
import com.ready4work.weather.dto.WeatherResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class WeatherService {

    private final WebClient weatherWebClient;
    private final WebClient kakaoWebClient;
    private final ClothingAdvisor clothingAdvisor;
    private final String apiKey;
    private final ConcurrentHashMap<String, Mono<WeatherResponse>> cache = new ConcurrentHashMap<>();

    public WeatherService(
            WebClient weatherWebClient,
            WebClient kakaoWebClient,
            ClothingAdvisor clothingAdvisor,
            @Value("${weather.api.key}") String apiKey
    ) {
        this.weatherWebClient = weatherWebClient;
        this.kakaoWebClient = kakaoWebClient;
        this.clothingAdvisor = clothingAdvisor;
        this.apiKey = apiKey;
        log.info("WeatherService initialized, API key present: {}", apiKey != null && !apiKey.isBlank());
    }

    private static final Map<String, String> ICON_EMOJI_MAP = Map.ofEntries(
            Map.entry("01d", "☀️"), Map.entry("01n", "🌙"),
            Map.entry("02d", "🌤"), Map.entry("02n", "🌤"),
            Map.entry("03d", "⛅"), Map.entry("03n", "⛅"),
            Map.entry("04d", "☁️"), Map.entry("04n", "☁️"),
            Map.entry("09d", "🌧"), Map.entry("09n", "🌧"),
            Map.entry("10d", "🌦"), Map.entry("10n", "🌦"),
            Map.entry("11d", "⛈"), Map.entry("11n", "⛈"),
            Map.entry("13d", "🌨"), Map.entry("13n", "🌨"),
            Map.entry("50d", "🌫"), Map.entry("50n", "🌫")
    );

    private static final Map<String, String> CITY_KR_MAP = Map.of(
            "Seoul", "서울",
            "Busan", "부산",
            "Incheon", "인천",
            "Daegu", "대구",
            "Daejeon", "대전",
            "Gwangju", "광주",
            "Suwon", "수원",
            "Ulsan", "울산",
            "Jeju", "제주"
    );

    private static final Map<String, String> CONDITION_KR_MAP = Map.ofEntries(
            Map.entry("Clear", "맑음"),
            Map.entry("Clouds", "흐림"),
            Map.entry("Few clouds", "구름 조금"),
            Map.entry("Scattered clouds", "구름 약간"),
            Map.entry("Broken clouds", "구름 많음"),
            Map.entry("Overcast clouds", "흐림"),
            Map.entry("Rain", "비"),
            Map.entry("Drizzle", "이슬비"),
            Map.entry("Thunderstorm", "뇌우"),
            Map.entry("Snow", "눈"),
            Map.entry("Mist", "안개"),
            Map.entry("Fog", "안개"),
            Map.entry("Haze", "연무")
    );

    private static final Set<Integer> COMMUTE_HOURS = Set.of(7, 9, 12, 15, 18, 21);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH'시'");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public Mono<WeatherResponse> getWeatherByCoords(double lat, double lon) {
        String cacheKey = String.format("coord:%.2f,%.2f", lat, lon);
        return cache.computeIfAbsent(cacheKey, k ->
                fetchWeatherByCoords(lat, lon).cache(Duration.ofMinutes(30))
        );
    }

    public Mono<WeatherResponse> getWeather(String city) {
        return cache.computeIfAbsent(city.toLowerCase(), k ->
                fetchWeather(city).cache(Duration.ofMinutes(30))
        );
    }

    @SuppressWarnings("unchecked")
    private Mono<WeatherResponse> fetchWeather(String city) {
        Mono<Map> currentMono = weatherWebClient.get()
                .uri(uri -> uri.path("/data/2.5/weather")
                        .queryParam("q", city)
                        .queryParam("appid", apiKey)
                        .queryParam("units", "metric")
                        .queryParam("lang", "kr")
                        .build())
                .retrieve()
                .bodyToMono(Map.class);

        Mono<Map> forecastMono = weatherWebClient.get()
                .uri(uri -> uri.path("/data/2.5/forecast")
                        .queryParam("q", city)
                        .queryParam("appid", apiKey)
                        .queryParam("units", "metric")
                        .queryParam("lang", "kr")
                        .build())
                .retrieve()
                .bodyToMono(Map.class);

        return Mono.zip(currentMono, forecastMono)
                .map(tuple -> buildResponse(tuple.getT1(), tuple.getT2(), city));
    }

    @SuppressWarnings("unchecked")
    private Mono<WeatherResponse> fetchWeatherByCoords(double lat, double lon) {
        Mono<Map> currentMono = weatherWebClient.get()
                .uri(uri -> uri.path("/data/2.5/weather")
                        .queryParam("lat", lat)
                        .queryParam("lon", lon)
                        .queryParam("appid", apiKey)
                        .queryParam("units", "metric")
                        .queryParam("lang", "kr")
                        .build())
                .retrieve()
                .bodyToMono(Map.class);

        Mono<Map> forecastMono = weatherWebClient.get()
                .uri(uri -> uri.path("/data/2.5/forecast")
                        .queryParam("lat", lat)
                        .queryParam("lon", lon)
                        .queryParam("appid", apiKey)
                        .queryParam("units", "metric")
                        .queryParam("lang", "kr")
                        .build())
                .retrieve()
                .bodyToMono(Map.class);

        Mono<String> regionMono = reverseGeocode(lat, lon);

        return Mono.zip(currentMono, forecastMono, regionMono)
                .map(tuple -> buildResponse(tuple.getT1(), tuple.getT2(), tuple.getT3()));
    }

    @SuppressWarnings("unchecked")
    private Mono<String> reverseGeocode(double lat, double lon) {
        return kakaoWebClient.get()
                .uri(uri -> uri.path("/v2/local/geo/coord2regioninfo.json")
                        .queryParam("x", lon)
                        .queryParam("y", lat)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .map(body -> {
                    List<Map<String, Object>> docs = (List<Map<String, Object>>) body.get("documents");
                    if (docs != null && !docs.isEmpty()) {
                        return docs.stream()
                                .filter(d -> "H".equals(d.get("region_type")))
                                .findFirst()
                                .map(d -> (String) d.get("region_2depth_name"))
                                .orElse("알 수 없음");
                    }
                    return "알 수 없음";
                })
                .onErrorReturn("알 수 없음");
    }

    @SuppressWarnings("unchecked")
    private WeatherResponse buildResponse(Map<String, Object> current, Map<String, Object> forecast, String city) {
        // Parse current weather
        Map<String, Object> main = (Map<String, Object>) current.get("main");
        List<Map<String, Object>> weatherList = (List<Map<String, Object>>) current.get("weather");
        Map<String, Object> weather = weatherList.getFirst();
        Map<String, Object> wind = (Map<String, Object>) current.get("wind");

        int temp = (int) Math.round(((Number) main.get("temp")).doubleValue());
        String mainCondition = (String) weather.get("main");
        String description = (String) weather.get("description");
        String iconCode = (String) weather.get("icon");
        int humidity = ((Number) main.get("humidity")).intValue();
        double windSpeed = ((Number) wind.get("speed")).doubleValue();

        String emoji = ICON_EMOJI_MAP.getOrDefault(iconCode, "🌡");
        String conditionKr = CONDITION_KR_MAP.getOrDefault(mainCondition, description);
        String cityKr = CITY_KR_MAP.getOrDefault(city, city);

        CurrentWeather currentWeather = new CurrentWeather(
                temp, conditionKr, emoji, humidity,
                String.format("%.0fm/s", windSpeed)
        );

        // Parse forecast - filter today's commute hours
        List<Map<String, Object>> forecastList = (List<Map<String, Object>>) forecast.get("list");

        List<HourlyForecast> hourlyForecasts = new ArrayList<>();
        int high = Integer.MIN_VALUE;
        int low = Integer.MAX_VALUE;

        var today = Instant.now().atZone(KST).toLocalDate();

        for (Map<String, Object> item : forecastList) {
            long dt = ((Number) item.get("dt")).longValue();
            var itemTime = Instant.ofEpochSecond(dt).atZone(KST);

            if (!itemTime.toLocalDate().equals(today)) continue;

            Map<String, Object> itemMain = (Map<String, Object>) item.get("main");
            int itemTemp = (int) Math.round(((Number) itemMain.get("temp")).doubleValue());

            high = Math.max(high, itemTemp);
            low = Math.min(low, itemTemp);

            int hour = itemTime.getHour();
            if (COMMUTE_HOURS.contains(hour)) {
                List<Map<String, Object>> itemWeatherList = (List<Map<String, Object>>) item.get("weather");
                String itemIcon = (String) itemWeatherList.getFirst().get("icon");

                hourlyForecasts.add(new HourlyForecast(
                        itemTime.format(TIME_FMT),
                        itemTemp,
                        ICON_EMOJI_MAP.getOrDefault(itemIcon, "🌡")
                ));
            }
        }

        // Fallback: if no today data in forecast, use current temp for high/low
        if (high == Integer.MIN_VALUE) {
            high = temp;
            low = temp;
        }

        String clothing = clothingAdvisor.recommend(temp, high, low, mainCondition);

        return new WeatherResponse(cityKr, currentWeather, high, low, hourlyForecasts, clothing, List.of());
    }
}
