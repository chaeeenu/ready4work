package com.ready4work.weather.dto;

public record HourlyForecast(
        String time,
        int temp,
        String icon
) {}
