package com.ready4work.transit;

import com.ready4work.transit.dto.TransitLeg;
import com.ready4work.transit.dto.TransitRoute;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class TransitServiceTest {

    private MockWebServer odsayServer;
    private MockWebServer kakaoServer;
    private TransitService service;

    @BeforeEach
    void setUp() throws IOException {
        odsayServer = new MockWebServer();
        odsayServer.start();
        kakaoServer = new MockWebServer();
        kakaoServer.start();

        WebClient odsayClient = WebClient.builder()
                .baseUrl(odsayServer.url("/").toString())
                .build();
        WebClient kakaoClient = WebClient.builder()
                .baseUrl(kakaoServer.url("/").toString())
                .build();

        service = new TransitService(odsayClient, kakaoClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        odsayServer.shutdown();
        kakaoServer.shutdown();
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────

    private MockResponse kakaoResponse(String lon, String lat) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        { "documents": [{ "x": "%s", "y": "%s" }] }
                        """.formatted(lon, lat));
    }

    private void enqueueKakaoCoords(String oLon, String oLat, String dLon, String dLat) {
        kakaoServer.enqueue(kakaoResponse(oLon, oLat));
        kakaoServer.enqueue(kakaoResponse(dLon, dLat));
    }

    // ─── 지하철 leg 파싱 ──────────────────────────────────────────

    @Test
    @DisplayName("지하철 leg: startName/endName은 subPath 직속 필드에서 파싱")
    void subwayLeg_parsesCorrectly() {
        enqueueKakaoCoords("126.978", "37.566", "127.028", "37.498");

        // ODsay 실제 포맷: startName/endName이 subPath 직속 필드
        odsayServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "result": {
                            "path": [{
                              "pathType": 1,
                              "info": {
                                "totalTime": 20,
                                "payment": 1350,
                                "busTransitCount": 0,
                                "subwayTransitCount": 1
                              },
                              "subPath": [
                                { "trafficType": 3, "sectionTime": 3 },
                                {
                                  "trafficType": 1,
                                  "sectionTime": 15,
                                  "stationCount": 8,
                                  "startName": "시청",
                                  "endName": "강남",
                                  "startX": 126.978, "startY": 37.566,
                                  "endX": 127.028,   "endY": 37.498,
                                  "lane": [{ "name": "수도권2호선", "subwayCode": 2, "color": "33A23D" }]
                                }
                              ]
                            }]
                          }
                        }
                        """));

        StepVerifier.create(service.getRoutes("시청역", "강남역", false))
                .assertNext(response -> {
                    assertThat(response.routes()).hasSize(1);
                    TransitRoute route = response.routes().getFirst();
                    assertThat(route.legs()).hasSize(2);

                    TransitLeg walkLeg = route.legs().get(0);
                    assertThat(walkLeg.type()).isEqualTo("walk");
                    assertThat(walkLeg.busStopLat()).isNull();

                    TransitLeg subwayLeg = route.legs().get(1);
                    assertThat(subwayLeg.type()).isEqualTo("subway");
                    assertThat(subwayLeg.lineName()).isEqualTo("수도권2호선");
                    assertThat(subwayLeg.lineColor()).isEqualTo("#33A23D");
                    assertThat(subwayLeg.startName()).isEqualTo("시청");
                    assertThat(subwayLeg.endName()).isEqualTo("강남");
                    assertThat(subwayLeg.stationCount()).isEqualTo(8);
                    assertThat(subwayLeg.busStopLat()).isNull();
                    assertThat(subwayLeg.busStopLon()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("지하철 color 없을 때 subwayCode로 노선 색상 매핑 (9호선 → #BDB092)")
    void subwayLeg_colorFallbackBySubwayCode() {
        enqueueKakaoCoords("126.801", "37.563", "126.924", "37.521");

        odsayServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "result": {
                            "path": [{
                              "pathType": 1,
                              "info": {
                                "totalTime": 25,
                                "payment": 1450,
                                "busTransitCount": 0,
                                "subwayTransitCount": 1
                              },
                              "subPath": [{
                                "trafficType": 1,
                                "sectionTime": 25,
                                "stationCount": 10,
                                "startName": "김포공항",
                                "endName": "여의도",
                                "startX": 126.801, "startY": 37.563,
                                "endX": 126.924,   "endY": 37.521,
                                "lane": [{ "name": "수도권9호선(급행)", "subwayCode": 9, "color": "" }]
                              }]
                            }]
                          }
                        }
                        """));

        StepVerifier.create(service.getRoutes("김포공항", "여의도", false))
                .assertNext(response -> {
                    TransitLeg leg = response.routes().getFirst().legs().getFirst();
                    assertThat(leg.startName()).isEqualTo("김포공항");
                    assertThat(leg.endName()).isEqualTo("여의도");
                    assertThat(leg.lineName()).isEqualTo("수도권9호선(급행)");
                    assertThat(leg.lineColor()).isEqualTo("#BDB092");
                    assertThat(response.routes().getFirst().totalCost()).isEqualTo(1450);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("info.payment 필드로 요금 파싱 + subwayCode=2 → #00A84D")
    void route_parsesPaymentFare() {
        enqueueKakaoCoords("126.929", "37.484", "127.028", "37.498");

        odsayServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "result": {
                            "path": [{
                              "pathType": 1,
                              "info": {
                                "totalTime": 30,
                                "payment": 1500,
                                "busTransitCount": 0,
                                "subwayTransitCount": 1
                              },
                              "subPath": [{
                                "trafficType": 1,
                                "sectionTime": 30,
                                "stationCount": 12,
                                "startName": "신림",
                                "endName": "강남",
                                "startX": 126.929, "startY": 37.484,
                                "endX": 127.028,   "endY": 37.498,
                                "lane": [{ "name": "수도권2호선", "subwayCode": 2, "color": "" }]
                              }]
                            }]
                          }
                        }
                        """));

        StepVerifier.create(service.getRoutes("신림역", "강남역", false))
                .assertNext(response -> {
                    TransitRoute route = response.routes().getFirst();
                    assertThat(route.totalCost()).isEqualTo(1500);
                    TransitLeg leg = route.legs().getFirst();
                    assertThat(leg.startName()).isEqualTo("신림");
                    assertThat(leg.endName()).isEqualTo("강남");
                    assertThat(leg.lineColor()).isEqualTo("#00A84D");
                })
                .verifyComplete();
    }

    // ─── 버스 leg 파싱 ────────────────────────────────────────────

    @Test
    @DisplayName("버스 leg: startName/endName/busStopLat/Lon은 subPath 직속 필드(startX,startY)에서 파싱")
    void busLeg_extractsCoordinates() {
        enqueueKakaoCoords("126.978", "37.566", "127.001", "37.540");

        // ODsay 실제 포맷: 버스 좌표는 subPath.startX(경도)/startY(위도)
        odsayServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "result": {
                            "path": [{
                              "pathType": 1,
                              "info": {
                                "totalTime": 30,
                                "payment": 1250,
                                "busTransitCount": 1,
                                "subwayTransitCount": 0
                              },
                              "subPath": [
                                { "trafficType": 3, "sectionTime": 3 },
                                {
                                  "trafficType": 2,
                                  "sectionTime": 20,
                                  "stationCount": 10,
                                  "startName": "시청앞",
                                  "endName": "강남역",
                                  "startX": 126.9780, "startY": 37.5665,
                                  "endX": 127.0282,   "endY": 37.4979,
                                  "lane": [{ "busNo": "345", "color": "3366CC" }]
                                }
                              ]
                            }]
                          }
                        }
                        """));

        StepVerifier.create(service.getRoutes("시청", "강남역", false))
                .assertNext(response -> {
                    assertThat(response.routes()).hasSize(1);
                    TransitLeg busLeg = response.routes().getFirst().legs().get(1);
                    assertThat(busLeg.type()).isEqualTo("bus");
                    assertThat(busLeg.lineName()).isEqualTo("345");
                    assertThat(busLeg.lineColor()).isEqualTo("#3366CC");
                    assertThat(busLeg.startName()).isEqualTo("시청앞");
                    assertThat(busLeg.endName()).isEqualTo("강남역");
                    assertThat(busLeg.stationCount()).isEqualTo(10);
                    assertThat(busLeg.busStopLat()).isEqualTo(37.5665);  // startY
                    assertThat(busLeg.busStopLon()).isEqualTo(126.9780); // startX
                })
                .verifyComplete();
    }

    // ─── walk leg 파싱 ────────────────────────────────────────────

    @Test
    @DisplayName("walk leg: sectionTime=0 이면 제외")
    void walkLeg_zeroSectionTime_excluded() {
        enqueueKakaoCoords("126.977", "37.571", "126.924", "37.521");

        odsayServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "result": {
                            "path": [{
                              "pathType": 1,
                              "info": {
                                "totalTime": 15,
                                "payment": 1350,
                                "busTransitCount": 0,
                                "subwayTransitCount": 1
                              },
                              "subPath": [
                                { "trafficType": 3, "sectionTime": 0 },
                                {
                                  "trafficType": 1,
                                  "sectionTime": 15,
                                  "stationCount": 5,
                                  "startName": "광화문",
                                  "endName": "여의도",
                                  "startX": 126.977, "startY": 37.571,
                                  "endX": 126.924,   "endY": 37.521,
                                  "lane": [{ "name": "수도권5호선", "subwayCode": 5, "color": "996CAC" }]
                                }
                              ]
                            }]
                          }
                        }
                        """));

        StepVerifier.create(service.getRoutes("광화문", "여의도", false))
                .assertNext(response -> {
                    TransitRoute route = response.routes().getFirst();
                    assertThat(route.legs()).hasSize(1);
                    assertThat(route.legs().getFirst().type()).isEqualTo("subway");
                })
                .verifyComplete();
    }

    // ─── pathType 선택 ────────────────────────────────────────────

    @Test
    @DisplayName("pathType 1과 3이 모두 있으면 2개 경로 반환")
    void pathType_1and3_returnsTwoRoutes() {
        enqueueKakaoCoords("126.978", "37.566", "127.028", "37.498");

        odsayServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "result": {
                            "path": [
                              {
                                "pathType": 1,
                                "info": {
                                  "totalTime": 25,
                                  "payment": 1350,
                                  "busTransitCount": 1,
                                  "subwayTransitCount": 1
                                },
                                "subPath": [
                                  {
                                    "trafficType": 1,
                                    "sectionTime": 15,
                                    "stationCount": 5,
                                    "startName": "시청", "endName": "강남",
                                    "startX": 126.978, "startY": 37.566,
                                    "endX": 127.028, "endY": 37.498,
                                    "lane": [{ "name": "수도권2호선", "subwayCode": 2, "color": "33A23D" }]
                                  },
                                  {
                                    "trafficType": 2,
                                    "sectionTime": 10,
                                    "stationCount": 3,
                                    "startName": "강남역", "endName": "역삼역",
                                    "startX": 127.028, "startY": 37.498,
                                    "endX": 127.036, "endY": 37.500,
                                    "lane": [{ "busNo": "146", "color": "3366CC" }]
                                  }
                                ]
                              },
                              {
                                "pathType": 3,
                                "info": {
                                  "totalTime": 35,
                                  "payment": 1250,
                                  "busTransitCount": 0,
                                  "subwayTransitCount": 1
                                },
                                "subPath": [{
                                  "trafficType": 1,
                                  "sectionTime": 30,
                                  "stationCount": 12,
                                  "startName": "시청", "endName": "역삼",
                                  "startX": 126.978, "startY": 37.566,
                                  "endX": 127.036, "endY": 37.500,
                                  "lane": [{ "name": "수도권2호선", "subwayCode": 2, "color": "33A23D" }]
                                }]
                              }
                            ]
                          }
                        }
                        """));

        StepVerifier.create(service.getRoutes("시청역", "역삼역", false))
                .assertNext(response -> {
                    assertThat(response.routes()).hasSize(2);
                    assertThat(response.routes().get(0).totalTime()).isEqualTo(25);
                    assertThat(response.routes().get(1).totalTime()).isEqualTo(35);
                    assertThat(response.routes().get(1).transferCount()).isEqualTo(0);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("pathType 1만 있으면 1개 경로 반환")
    void pathType_1only_returnsOneRoute() {
        enqueueKakaoCoords("126.801", "37.563", "126.924", "37.521");

        odsayServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "result": {
                            "path": [{
                              "pathType": 1,
                              "info": {
                                "totalTime": 20,
                                "payment": 1350,
                                "busTransitCount": 0,
                                "subwayTransitCount": 1
                              },
                              "subPath": [{
                                "trafficType": 1,
                                "sectionTime": 20,
                                "stationCount": 8,
                                "startName": "김포공항", "endName": "여의도",
                                "startX": 126.801, "startY": 37.563,
                                "endX": 126.924,   "endY": 37.521,
                                "lane": [{ "name": "수도권9호선", "subwayCode": 9, "color": "BDB092" }]
                              }]
                            }]
                          }
                        }
                        """));

        StepVerifier.create(service.getRoutes("김포공항", "여의도역", false))
                .assertNext(response -> {
                    assertThat(response.routes()).hasSize(1);
                    assertThat(response.routes().getFirst().totalTime()).isEqualTo(20);
                })
                .verifyComplete();
    }

    // ─── 오류 / 캐시 ──────────────────────────────────────────────

    @Test
    @DisplayName("ODsay error 응답 시 빈 routes 반환")
    void odsayError_returnsEmptyRoutes() {
        enqueueKakaoCoords("126.978", "37.566", "127.028", "37.498");

        odsayServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "result": {
                            "error": { "code": -98, "msg": "요청 데이터 오류" }
                          }
                        }
                        """));

        StepVerifier.create(service.getRoutes("출발지", "도착지", false))
                .assertNext(response -> assertThat(response.routes()).isEmpty())
                .verifyComplete();
    }

    @Test
    @DisplayName("refresh=true 시 캐시를 무시하고 새로 조회")
    void refresh_bypassesCache() {
        enqueueKakaoCoords("126.978", "37.566", "127.028", "37.498");
        enqueueKakaoCoords("126.978", "37.566", "127.028", "37.498");

        String pathJson = """
                {
                  "result": {
                    "path": [{
                      "pathType": 1,
                      "info": { "totalTime": 20, "payment": 1350,
                                "busTransitCount": 0, "subwayTransitCount": 1 },
                      "subPath": [{
                        "trafficType": 1, "sectionTime": 20, "stationCount": 5,
                        "startName": "A역", "endName": "B역",
                        "startX": 126.978, "startY": 37.566,
                        "endX": 127.028,   "endY": 37.498,
                        "lane": [{ "name": "수도권2호선", "subwayCode": 2, "color": "33A23D" }]
                      }]
                    }]
                  }
                }
                """;
        odsayServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(pathJson));
        odsayServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(pathJson));

        service.getRoutes("A역", "B역", false).block();
        StepVerifier.create(service.getRoutes("A역", "B역", true))
                .assertNext(response -> assertThat(response.routes()).hasSize(1))
                .verifyComplete();

        assertThat(odsayServer.getRequestCount()).isEqualTo(2);
    }
}
