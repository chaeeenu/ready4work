package com.ready4work.subway.dto;

import java.util.List;

public record SubwayArrivalResponse(
        String stationName,
        List<SubwayArrival> arrivals,
        String updatedAt
) {}
