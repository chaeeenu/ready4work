package com.ready4work.weather.dto;

public record CurrentWeather(
        int temp,
        String condition,
        String icon,
        int humidity,
        String wind
) {}
