package com.ready4work.transit.dto;

public record TransitLeg(
        String type,
        String lineName,
        String lineColor,
        String startName,
        String endName,
        int stationCount,
        int sectionTime
) {}
