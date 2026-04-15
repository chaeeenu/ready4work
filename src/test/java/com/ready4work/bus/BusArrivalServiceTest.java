package com.ready4work.bus;

import com.ready4work.bus.dto.BusArrivalResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class BusArrivalServiceTest {

    private MockWebServer stationServer;
    private MockWebServer arrivalServer;
    private BusArrivalService service;

    @BeforeEach
    void setUp() throws IOException {
        stationServer = new MockWebServer();
        stationServer.start();
        arrivalServer = new MockWebServer();
        arrivalServer.start();

        WebClient stationClient = WebClient.builder()
                .baseUrl(stationServer.url("/").toString())
                .build();
        WebClient arrivalClient = WebClient.builder()
                .baseUrl(arrivalServer.url("/").toString())
                .build();

        String stationBaseUrl = stationServer.url("/").toString().replaceAll("/$", "");
        String arrivalBaseUrl = arrivalServer.url("/").toString().replaceAll("/$", "");
        service = new BusArrivalService(stationClient, arrivalClient, "testStationKey", "testArrivalKey", stationBaseUrl, arrivalBaseUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        stationServer.shutdown();
        arrivalServer.shutdown();
    }

    @Test
    @DisplayName("좌표로 정류소 조회 후 도착정보 반환 (XML)")
    void getArrivals_success() {
        stationServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("""
                    <ServiceResult>
                      <msgBody>
                        <itemList><stId>12345</stId><stNm>테스트정류소</stNm></itemList>
                        <itemList><stId>12346</stId><stNm>두번째정류소</stNm></itemList>
                      </msgBody>
                    </ServiceResult>
                    """));

        arrivalServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("""
                    <ServiceResult>
                      <msgBody>
                        <itemList>
                          <rtNm>146</rtNm><arrmsg1>3분후 도착</arrmsg1><arrmsg2>10분후 도착</arrmsg2>
                          <busRouteId>100100118</busRouteId><routeType>3</routeType>
                        </itemList>
                        <itemList>
                          <rtNm>341</rtNm><arrmsg1>곧 도착</arrmsg1><arrmsg2>8분후 도착</arrmsg2>
                          <busRouteId>100100228</busRouteId><routeType>3</routeType>
                        </itemList>
                      </msgBody>
                    </ServiceResult>
                    """));

        Mono<BusArrivalResponse> result = service.getArrivals(37.5665, 126.9780, false);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.stationName()).isEqualTo("테스트정류소");
                    assertThat(response.arrivals()).hasSize(2);
                    assertThat(response.arrivals().getFirst().routeName()).isEqualTo("146");
                    assertThat(response.arrivals().getFirst().arvlMsg1()).isEqualTo("3분후 도착");
                    assertThat(response.updatedAt()).isNotBlank();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("단일 정류소 + 단일 도착정보일 때 정상 파싱 (XML 단일 항목)")
    void getArrivals_singleItemList() {
        stationServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("""
                    <ServiceResult>
                      <msgBody>
                        <itemList><stId>12345</stId><stNm>단일정류소</stNm></itemList>
                      </msgBody>
                    </ServiceResult>
                    """));

        arrivalServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("""
                    <ServiceResult>
                      <msgBody>
                        <itemList>
                          <rtNm>501</rtNm><arrmsg1>2분후 도착</arrmsg1><arrmsg2>15분후 도착</arrmsg2>
                          <busRouteId>100100501</busRouteId><routeType>4</routeType>
                        </itemList>
                      </msgBody>
                    </ServiceResult>
                    """));

        Mono<BusArrivalResponse> result = service.getArrivals(37.55, 126.97, false);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.stationName()).isEqualTo("단일정류소");
                    assertThat(response.arrivals()).hasSize(1);
                    assertThat(response.arrivals().getFirst().routeName()).isEqualTo("501");
                    assertThat(response.arrivals().getFirst().arvlMsg1()).isEqualTo("2분후 도착");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("정류소 조회 실패 시 빈 응답 반환 (빈 msgBody)")
    void getArrivals_stationNotFound() {
        stationServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("""
                    <ServiceResult>
                      <msgBody></msgBody>
                    </ServiceResult>
                    """));

        Mono<BusArrivalResponse> result = service.getArrivals(37.0, 127.0, false);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.arrivals()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("API 에러 시 빈 응답으로 fallback")
    void getArrivals_apiError() {
        stationServer.enqueue(new MockResponse().setResponseCode(500));

        Mono<BusArrivalResponse> result = service.getArrivals(37.5665, 126.9780, false);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.arrivals()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("캐시 동작 확인 - 두 번째 호출은 서버 요청 없음")
    void getArrivals_cacheWorks() {
        stationServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("""
                    <ServiceResult>
                      <msgBody>
                        <itemList><stId>99999</stId><stNm>캐시테스트</stNm></itemList>
                      </msgBody>
                    </ServiceResult>
                    """));
        arrivalServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("""
                    <ServiceResult>
                      <msgBody>
                        <itemList>
                          <rtNm>100</rtNm><arrmsg1>도착</arrmsg1><arrmsg2></arrmsg2>
                          <busRouteId>1</busRouteId><routeType>1</routeType>
                        </itemList>
                      </msgBody>
                    </ServiceResult>
                    """));

        // 첫 번째 호출
        StepVerifier.create(service.getArrivals(37.1, 127.1, false))
                .assertNext(r -> assertThat(r.stationName()).isEqualTo("캐시테스트"))
                .verifyComplete();

        // 두 번째 호출 - 서버에 추가 enqueue 없이도 성공해야 함 (캐시)
        StepVerifier.create(service.getArrivals(37.1, 127.1, false))
                .assertNext(r -> assertThat(r.stationName()).isEqualTo("캐시테스트"))
                .verifyComplete();

        // station 서버에는 1번만 요청
        assertThat(stationServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("refresh 시 캐시 무효화")
    void getArrivals_refreshClearsCache() {
        // 첫 번째 호출
        stationServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("""
                    <ServiceResult>
                      <msgBody>
                        <itemList><stId>11111</stId><stNm>리프레시</stNm></itemList>
                      </msgBody>
                    </ServiceResult>
                    """));
        arrivalServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("""
                    <ServiceResult>
                      <msgBody></msgBody>
                    </ServiceResult>
                    """));

        StepVerifier.create(service.getArrivals(37.2, 127.2, false))
                .assertNext(r -> assertThat(r.stationName()).isEqualTo("리프레시"))
                .verifyComplete();

        // refresh 호출 - 새로운 서버 응답 필요
        stationServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("""
                    <ServiceResult>
                      <msgBody>
                        <itemList><stId>11111</stId><stNm>리프레시갱신</stNm></itemList>
                      </msgBody>
                    </ServiceResult>
                    """));
        arrivalServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("""
                    <ServiceResult>
                      <msgBody></msgBody>
                    </ServiceResult>
                    """));

        StepVerifier.create(service.getArrivals(37.2, 127.2, true))
                .assertNext(r -> assertThat(r.stationName()).isEqualTo("리프레시갱신"))
                .verifyComplete();

        assertThat(stationServer.getRequestCount()).isEqualTo(2);
    }
}
