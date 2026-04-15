package com.ready4work.bus.dto;

import java.util.List;

public record BusArrivalResponse(
        String stationName,
        List<BusArrival> arrivals,
        String updatedAt
) {}
