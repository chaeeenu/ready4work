package com.ready4work.weather.dto;

import java.util.List;

public record WeatherResponse(
        String city,
        CurrentWeather current,
        int high,
        int low,
        List<HourlyForecast> hourly,
        String clothing,
        List<String> alerts
) {}
