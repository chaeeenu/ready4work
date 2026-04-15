package com.ready4work.subway.dto;

public record SubwayArrival(
        String trainLineNm,
        String arvlMsg2,
        String updnLine,
        String bstatnNm,
        String arvlCd,
        String subwayId   // 서울 API 노선 ID: 1001=1호선, ..., 1009=9호선
) {}
