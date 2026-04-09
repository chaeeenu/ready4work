package com.ready4work.transit.dto;

import java.util.List;

public record TransitRouteResponse(
        String origin,
        String destination,
        List<TransitRoute> routes
) {}
