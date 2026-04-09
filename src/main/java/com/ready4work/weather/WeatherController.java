package com.ready4work.weather;

import com.ready4work.weather.dto.WeatherResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    @GetMapping("/api/weather")
    public Mono<WeatherResponse> getWeather(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestParam(defaultValue = "Seoul") String city) {
        if (lat != null && lon != null) {
            return weatherService.getWeatherByCoords(lat, lon);
        }
        return weatherService.getWeather(city);
    }
}
