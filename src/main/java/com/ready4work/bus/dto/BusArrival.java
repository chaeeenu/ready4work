package com.ready4work.bus.dto;

public record BusArrival(
        String routeName,
        String arvlMsg1,
        String arvlMsg2,
        String busRouteId,
        String routeType
) {}
