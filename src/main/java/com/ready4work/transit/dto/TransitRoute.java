package com.ready4work.transit.dto;

import java.util.List;

public record TransitRoute(
        int totalTime,            // TMAP 원본 (참고용 유지)
        int adjustedTotalTime,    // 실시간 보정 총시간
        int transferCount,
        int totalCost,
        int walkTime,
        String summary,
        List<TransitLeg> legs,
        String enrichedAt         // 실시간 데이터 시각, null=enrichment 미적용
) {
    /** 기본값으로 생성 (enrichment 전) */
    public static TransitRoute ofBasic(
            int totalTime, int transferCount, int totalCost,
            int walkTime, String summary, List<TransitLeg> legs
    ) {
        return new TransitRoute(
                totalTime, totalTime, transferCount, totalCost,
                walkTime, summary, legs, null
        );
    }

    /** enrichment 결과를 적용한 새 인스턴스 생성 */
    public TransitRoute withEnrichment(int adjustedTotalTime, List<TransitLeg> enrichedLegs, String enrichedAt) {
        return new TransitRoute(
                totalTime, adjustedTotalTime, transferCount, totalCost,
                walkTime, summary, enrichedLegs, enrichedAt
        );
    }
}
