package com.ready4work.transit.dto;

import java.util.List;

public record TransitRoute(
        int totalTime,
        int transferCount,
        int totalCost,
        int walkTime,
        String summary,
        List<TransitLeg> legs
) {}
