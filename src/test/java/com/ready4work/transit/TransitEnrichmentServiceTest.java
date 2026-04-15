package com.ready4work.transit;

import com.ready4work.bus.BusArrivalService;
import com.ready4work.bus.dto.BusArrival;
import com.ready4work.bus.dto.BusArrivalResponse;
import com.ready4work.subway.SubwayArrivalService;
import com.ready4work.subway.dto.SubwayArrival;
import com.ready4work.subway.dto.SubwayArrivalResponse;
import com.ready4work.transit.dto.TransitLeg;
import com.ready4work.transit.dto.TransitRoute;
import com.ready4work.transit.dto.TransitRouteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransitEnrichmentServiceTest {

    @Mock
    private SubwayArrivalService subwayArrivalService;

    @Mock
    private BusArrivalService busArrivalService;

    private TransitEnrichmentService enrichmentService;

    @BeforeEach
    void setUp() {
        enrichmentService = new TransitEnrichmentService(subwayArrivalService, busArrivalService);
    }

    @Test
    @DisplayName("지하철 leg에 실시간 도착정보가 enrichment 됨")
    void enrich_subwayLeg() {
        TransitLeg subwayLeg = TransitLeg.ofBasic("subway", "2호선", "#33A23D", "신림", "강남", 8, 20, null, null);
        TransitRoute route = TransitRoute.ofBasic(25, 0, 1250, 5, "2호선", List.of(
                TransitLeg.ofBasic("walk", null, null, null, null, 0, 5, null, null),
                subwayLeg
        ));
        TransitRouteResponse response = new TransitRouteResponse("출발지", "도착지", List.of(route));

        when(subwayArrivalService.getArrivals("신림", false)).thenReturn(Mono.just(
                new SubwayArrivalResponse("신림", List.of(
                        new SubwayArrival("2호선 - 강남방면", "3분 후 (신림)", "상행", "삼성", "99", "1002")
                ), "2026-04-14 08:30:00")
        ));

        StepVerifier.create(enrichmentService.enrich(response))
                .assertNext(enriched -> {
                    TransitRoute enrichedRoute = enriched.routes().getFirst();
                    assertThat(enrichedRoute.enrichedAt()).isNotNull();

                    // walk leg은 변경 없음
                    TransitLeg walkLeg = enrichedRoute.legs().get(0);
                    assertThat(walkLeg.arrivalMessage()).isNull();

                    // subway leg에 도착정보 적용
                    TransitLeg enrichedLeg = enrichedRoute.legs().get(1);
                    assertThat(enrichedLeg.waitTime()).isEqualTo(3);
                    // trainLineNm "2호선 - 강남방면" → 방면 텍스트 포함
                    assertThat(enrichedLeg.arrivalMessage()).isEqualTo("강남방면 · 3분 후 (신림)");
                    // adjustedTime = waitTime + rideTime
                    // rideTime = max(sectionTime-5, stationCount*2) = max(15, 16) = 16
                    // adjustedTime = 3 + 16 = 19
                    assertThat(enrichedLeg.adjustedTime()).isEqualTo(19);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("버스 leg에 실시간 도착정보가 enrichment 됨")
    void enrich_busLeg() {
        TransitLeg busLeg = TransitLeg.ofBasic("bus", "간선버스 146", "#3366CC", "서울역", "종로", 5, 15, 37.55, 126.97);
        TransitRoute route = TransitRoute.ofBasic(15, 0, 1200, 0, "간선버스 146", List.of(busLeg));
        TransitRouteResponse response = new TransitRouteResponse("출발지", "도착지", List.of(route));

        when(busArrivalService.getArrivals(eq(37.55), eq(126.97), eq(false))).thenReturn(Mono.just(
                new BusArrivalResponse("서울역정류소", List.of(
                        new BusArrival("146", "3분후 도착", "10분후 도착", "100100118", "3")
                ), "2026-04-14 08:30:00")
        ));

        StepVerifier.create(enrichmentService.enrich(response))
                .assertNext(enriched -> {
                    TransitLeg enrichedLeg = enriched.routes().getFirst().legs().getFirst();
                    assertThat(enrichedLeg.waitTime()).isEqualTo(3);
                    assertThat(enrichedLeg.arrivalMessage()).isEqualTo("3분후 도착");
                    assertThat(enrichedLeg.arrivalMessage2()).isEqualTo("10분후 도착");
                    // rideTime = max(15-5, 5*2) = max(10, 10) = 10
                    // adjustedTime = 3 + 10 = 13
                    assertThat(enrichedLeg.adjustedTime()).isEqualTo(13);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("실시간 조회 실패 시 원본 시간 유지 (fallback)")
    void enrich_fallbackOnError() {
        TransitLeg subwayLeg = TransitLeg.ofBasic("subway", "2호선", "#33A23D", "신림", "강남", 8, 20, null, null);
        TransitRoute route = TransitRoute.ofBasic(20, 0, 1250, 0, "2호선", List.of(subwayLeg));
        TransitRouteResponse response = new TransitRouteResponse("출발지", "도착지", List.of(route));

        when(subwayArrivalService.getArrivals("신림", false))
                .thenReturn(Mono.error(new RuntimeException("API 장애")));

        StepVerifier.create(enrichmentService.enrich(response))
                .assertNext(result -> {
                    // enrichment는 완료되지만, 실시간 데이터가 없어 원본 시간 유지
                    TransitRoute enrichedRoute = result.routes().getFirst();
                    assertThat(enrichedRoute.totalTime()).isEqualTo(20);
                    // 실시간 데이터 없이 sectionTime이 그대로 adjustedTime
                    assertThat(enrichedRoute.legs().getFirst().adjustedTime()).isEqualTo(20);
                    assertThat(enrichedRoute.legs().getFirst().waitTime()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("빈 경로 응답은 그대로 반환")
    void enrich_emptyRoutes() {
        TransitRouteResponse response = new TransitRouteResponse("출발지", "도착지", List.of());

        StepVerifier.create(enrichmentService.enrich(response))
                .assertNext(result -> assertThat(result.routes()).isEmpty())
                .verifyComplete();
    }

    @Test
    @DisplayName("버스 routeName 숫자 매칭: TMAP '간선버스 146' ↔ BIS '146'")
    void enrich_busRouteNameMatching() {
        TransitLeg busLeg = TransitLeg.ofBasic("bus", "간선버스 146", "#3366CC", "정류소", "도착지", 3, 10, 37.5, 127.0);
        TransitRoute route = TransitRoute.ofBasic(10, 0, 1200, 0, "간선버스 146", List.of(busLeg));
        TransitRouteResponse response = new TransitRouteResponse("출발지", "도착지", List.of(route));

        when(busArrivalService.getArrivals(eq(37.5), eq(127.0), eq(false))).thenReturn(Mono.just(
                new BusArrivalResponse("테스트정류소", List.of(
                        new BusArrival("341", "5분후 도착", "12분후 도착", "100100228", "3"),
                        new BusArrival("146", "2분후 도착", "8분후 도착", "100100118", "3")
                ), "2026-04-14 08:30:00")
        ));

        StepVerifier.create(enrichmentService.enrich(response))
                .assertNext(enriched -> {
                    TransitLeg enrichedLeg = enriched.routes().getFirst().legs().getFirst();
                    // 146에 매칭되어야 함 (341이 아닌)
                    assertThat(enrichedLeg.arrivalMessage()).isEqualTo("2분후 도착");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("지하철 방면 매칭: endName이 trainLineNm에 포함")
    void matchSubwayArrival_directionMatch() {
        TransitLeg leg = TransitLeg.ofBasic("subway", "2호선", "#33A23D", "신림", "강남", 8, 20, null, null);

        List<SubwayArrival> arrivals = List.of(
                new SubwayArrival("2호선 - 신도림방면", "5분 후 (신림)", "하행", "까치산", "99", "1002"),
                new SubwayArrival("2호선 - 강남방면", "3분 후 (신림)", "상행", "삼성", "99", "1002")
        );

        SubwayArrival matched = enrichmentService.matchSubwayArrival(leg, arrivals);
        assertThat(matched).isNotNull();
        assertThat(matched.arvlMsg2()).isEqualTo("3분 후 (신림)");
    }

    @Test
    @DisplayName("지하철 방면 매칭 실패 시 가장 빠른 열차로 fallback")
    void matchSubwayArrival_fallbackToFastest() {
        TransitLeg leg = TransitLeg.ofBasic("subway", "2호선", "#33A23D", "신림", "알수없는역", 8, 20, null, null);

        List<SubwayArrival> arrivals = List.of(
                new SubwayArrival("2호선 - 신도림방면", "5분 후 (신림)", "하행", "까치산", "99", "1002"),
                new SubwayArrival("2호선 - 삼성방면", "2분 후 (신림)", "상행", "삼성", "99", "1002")
        );

        SubwayArrival matched = enrichmentService.matchSubwayArrival(leg, arrivals);
        assertThat(matched).isNotNull();
        // 가장 빠른 열차 (2분 후)
        assertThat(matched.arvlMsg2()).isEqualTo("2분 후 (신림)");
    }

    @Test
    @DisplayName("adjustedTotalTime은 모든 leg의 adjustedTime 합산")
    void enrich_adjustedTotalTime() {
        TransitLeg walkLeg = TransitLeg.ofBasic("walk", null, null, null, null, 0, 5, null, null);
        TransitLeg subwayLeg = TransitLeg.ofBasic("subway", "2호선", "#33A23D", "신림", "강남", 8, 20, null, null);
        TransitRoute route = TransitRoute.ofBasic(25, 0, 1250, 5, "2호선", List.of(walkLeg, subwayLeg));
        TransitRouteResponse response = new TransitRouteResponse("출발지", "도착지", List.of(route));

        when(subwayArrivalService.getArrivals("신림", false)).thenReturn(Mono.just(
                new SubwayArrivalResponse("신림", List.of(
                        new SubwayArrival("2호선 - 강남방면", "3분 후 (신림)", "상행", "삼성", "99", "1002")
                ), "2026-04-14 08:30:00")
        ));

        StepVerifier.create(enrichmentService.enrich(response))
                .assertNext(enriched -> {
                    TransitRoute enrichedRoute = enriched.routes().getFirst();
                    // walk: 5분 + subway adjusted: 19분 = 24분
                    assertThat(enrichedRoute.adjustedTotalTime()).isEqualTo(24);
                    // 원본은 유지
                    assertThat(enrichedRoute.totalTime()).isEqualTo(25);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("subwayId 기반 노선 매칭: trainLineNm에 호선 번호가 없어도 subwayId로 매칭")
    void matchSubwayArrival_subwayIdMatching() {
        // 실제 서울 API처럼 trainLineNm에 호선 번호 없는 케이스 (석촌역 9호선 상황)
        TransitLeg leg = TransitLeg.ofBasic("subway", "수도권 9호선(급행)", "#BDB092", "석촌", "종합운동장", 3, 10, null, null);

        List<SubwayArrival> arrivals = List.of(
                new SubwayArrival("별내행 - 잠실방면", "2분 후", "상행", "별내", "99", "1008"),       // 8호선
                new SubwayArrival("개화행 - 석촌고분방면", "4분 후", "하행", "개화", "99", "1009"),   // 9호선 일반
                new SubwayArrival("중앙보훈병원행 - 송파나루방면", "1분 후", "상행", "중앙보훈병원", "99", "1009") // 9호선
        );

        SubwayArrival matched = enrichmentService.matchSubwayArrival(leg, arrivals);
        // 8호선(별내행) 제외, 9호선 2개 중 방면 매칭 후 가장 빠른 열차
        assertThat(matched).isNotNull();
        // subwayId 1009 = 9호선만 후보, 그 중 fastest = "1분 후"
        assertThat(matched.arvlMsg2()).isEqualTo("1분 후");
    }

    @Test
    @DisplayName("extractNumbers 유틸리티 메서드")
    void extractNumbers() {
        assertThat(TransitEnrichmentService.extractNumbers("간선버스 146")).isEqualTo("146");
        assertThat(TransitEnrichmentService.extractNumbers("2호선")).isEqualTo("2");
        assertThat(TransitEnrichmentService.extractNumbers("수인분당선")).isEmpty();
        assertThat(TransitEnrichmentService.extractNumbers(null)).isEmpty();
    }

    @Test
    @DisplayName("extractDirectionText: 다양한 trainLineNm 포맷 파싱")
    void extractDirectionText() {
        assertThat(TransitEnrichmentService.extractDirectionText("2호선 삼성행 - 삼성방면")).isEqualTo("삼성방면");
        assertThat(TransitEnrichmentService.extractDirectionText("9호선 급행 개화행")).isEqualTo("개화행");
        assertThat(TransitEnrichmentService.extractDirectionText("1호선 인천행")).isEqualTo("인천행");
        assertThat(TransitEnrichmentService.extractDirectionText("수인분당선 수원행")).isEqualTo("수원행");
        assertThat(TransitEnrichmentService.extractDirectionText("2호선 - 신도림방면")).isEqualTo("신도림방면");
        assertThat(TransitEnrichmentService.extractDirectionText(null)).isNull();
        assertThat(TransitEnrichmentService.extractDirectionText("알수없는노선")).isNull();
    }

    @Test
    @DisplayName("enrichSubwayLeg: arrivalMessage에 방면 텍스트 포함")
    void enrichSubwayLeg_directionTextInMessage() {
        TransitLeg subwayLeg = TransitLeg.ofBasic("subway", "9호선", "#BFC430", "노들역", "김포공항", 10, 30, null, null);
        TransitRoute route = TransitRoute.ofBasic(30, 0, 1500, 10, "9호선", List.of(subwayLeg));
        TransitRouteResponse response = new TransitRouteResponse("출발지", "도착지", List.of(route));

        when(subwayArrivalService.getArrivals("노들역", false)).thenReturn(Mono.just(
                new SubwayArrivalResponse("노들역", List.of(
                        new SubwayArrival("9호선 급행 개화행", "5분 후 (노들)", "하행", "개화", "99", "1009")
                ), "2026-04-14 08:30:00")
        ));

        StepVerifier.create(enrichmentService.enrich(response))
                .assertNext(enriched -> {
                    TransitLeg enrichedLeg = enriched.routes().getFirst().legs().getFirst();
                    // "개화행 · 5분 후 (노들)" 형태여야 함
                    assertThat(enrichedLeg.arrivalMessage()).isEqualTo("개화행 · 5분 후 (노들)");
                    assertThat(enrichedLeg.waitTime()).isEqualTo(5);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("enrichSubwayLeg: arrivalMessage2에 두 번째 열차 정보 설정")
    void enrichSubwayLeg_secondArrival() {
        TransitLeg subwayLeg = TransitLeg.ofBasic("subway", "2호선", "#33A23D", "신림", "강남", 8, 20, null, null);
        TransitRoute route = TransitRoute.ofBasic(25, 0, 1250, 8, "2호선", List.of(subwayLeg));
        TransitRouteResponse response = new TransitRouteResponse("출발지", "도착지", List.of(route));

        when(subwayArrivalService.getArrivals("신림", false)).thenReturn(Mono.just(
                new SubwayArrivalResponse("신림", List.of(
                        new SubwayArrival("2호선 - 강남방면", "3분 후 (신림)", "상행", "삼성", "99", "1002"),
                        new SubwayArrival("2호선 - 삼성방면", "8분 후 (신림)", "상행", "삼성", "99", "1002")
                ), "2026-04-14 08:30:00")
        ));

        StepVerifier.create(enrichmentService.enrich(response))
                .assertNext(enriched -> {
                    TransitLeg enrichedLeg = enriched.routes().getFirst().legs().getFirst();
                    // 첫 번째: 3분 후
                    assertThat(enrichedLeg.arrivalMessage()).isEqualTo("강남방면 · 3분 후 (신림)");
                    // 두 번째: 8분 후 (같은 상행 방향)
                    assertThat(enrichedLeg.arrivalMessage2()).isEqualTo("삼성방면 · 8분 후 (신림)");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("fetchAllSubwayArrivals: 일부 역 조회 실패해도 나머지 결과 반환")
    void fetchAllSubwayArrivals_partialFailure() {
        TransitLeg leg1 = TransitLeg.ofBasic("subway", "2호선", "#33A23D", "신림", "강남", 8, 20, null, null);
        TransitLeg leg2 = TransitLeg.ofBasic("subway", "2호선", "#33A23D", "선릉", "삼성", 3, 8, null, null);
        TransitRoute route = TransitRoute.ofBasic(30, 0, 2000, 8, "2호선", List.of(leg1, leg2));
        TransitRouteResponse response = new TransitRouteResponse("출발지", "도착지", List.of(route));

        // 신림역 API 성공, 선릉역 API 실패
        when(subwayArrivalService.getArrivals("신림", false)).thenReturn(Mono.just(
                new SubwayArrivalResponse("신림", List.of(
                        new SubwayArrival("2호선 - 강남방면", "4분 후 (신림)", "상행", "삼성", "99", "1002")
                ), "2026-04-14 08:30:00")
        ));
        when(subwayArrivalService.getArrivals("선릉", false))
                .thenReturn(Mono.error(new RuntimeException("선릉역 API 장애")));

        StepVerifier.create(enrichmentService.enrich(response))
                .assertNext(enriched -> {
                    List<TransitLeg> legs = enriched.routes().getFirst().legs();
                    // 신림 leg: 실시간 데이터 적용됨
                    assertThat(legs.get(0).waitTime()).isEqualTo(4);
                    assertThat(legs.get(0).arrivalMessage()).isEqualTo("강남방면 · 4분 후 (신림)");
                    // 선릉 leg: 조회 실패 → 원본 sectionTime 유지
                    assertThat(legs.get(1).waitTime()).isNull();
                    assertThat(legs.get(1).adjustedTime()).isEqualTo(8);
                })
                .verifyComplete();
    }
}
