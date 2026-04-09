package com.ready4work.subway.dto;

import java.util.List;

public record SubwayAlertResponse(List<SubwayAlert> alerts, String updatedAt) {}
