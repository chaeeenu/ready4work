package com.ready4work.transit.dto;

public record TransitLeg(
        String type,
        String lineName,
        String lineColor,
        String startName,
        String endName,
        int stationCount,
        int sectionTime,
        Double busStopLat,
        Double busStopLon,
        // 실시간 enrichment 필드
        Integer waitTime,         // 실시간 대기시간(분), null=알수없음
        String arrivalMessage,    // "3분후 도착" 등 원본 메시지
        String arrivalMessage2,   // 두 번째 차량 메시지
        int adjustedTime          // waitTime + 탑승시간 (보정된 구간시간)
) {
    /** 기본값으로 생성 (enrichment 전) */
    public static TransitLeg ofBasic(
            String type, String lineName, String lineColor,
            String startName, String endName,
            int stationCount, int sectionTime,
            Double busStopLat, Double busStopLon
    ) {
        return new TransitLeg(
                type, lineName, lineColor, startName, endName,
                stationCount, sectionTime, busStopLat, busStopLon,
                null, null, null, sectionTime
        );
    }

    /** enrichment 결과를 적용한 새 인스턴스 생성 */
    public TransitLeg withArrivalInfo(Integer waitTime, String arrivalMessage, String arrivalMessage2, int adjustedTime) {
        return new TransitLeg(
                type, lineName, lineColor, startName, endName,
                stationCount, sectionTime, busStopLat, busStopLon,
                waitTime, arrivalMessage, arrivalMessage2, adjustedTime
        );
    }
}
