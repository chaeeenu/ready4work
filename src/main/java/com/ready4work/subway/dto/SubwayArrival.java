package com.ready4work.subway.dto;

public record SubwayArrival(
        String trainLineNm,
        String arvlMsg2,
        String updnLine,
        String bstatnNm,
        String arvlCd
) {}
