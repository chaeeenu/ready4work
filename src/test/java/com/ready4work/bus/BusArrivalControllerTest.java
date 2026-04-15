package com.ready4work.bus;

import com.ready4work.bus.dto.BusArrival;
import com.ready4work.bus.dto.BusArrivalResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BusArrivalController.class)
class BusArrivalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BusArrivalService busArrivalService;

    @Test
    @DisplayName("GET /api/bus/arrivals - 정상 응답")
    void getArrivals_success() throws Exception {
        BusArrivalResponse response = new BusArrivalResponse(
                "테스트정류소",
                List.of(new BusArrival("146", "3분후 도착", "10분후 도착", "100100118", "3")),
                "2026-04-13 08:00:00"
        );

        when(busArrivalService.getArrivals(37.5665, 126.9780, false))
                .thenReturn(Mono.just(response));

        MvcResult mvcResult = mockMvc.perform(get("/api/bus/arrivals")
                        .param("lat", "37.5665")
                        .param("lon", "126.9780"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stationName").value("테스트정류소"))
                .andExpect(jsonPath("$.arrivals[0].routeName").value("146"))
                .andExpect(jsonPath("$.arrivals[0].arvlMsg1").value("3분후 도착"));
    }

    @Test
    @DisplayName("GET /api/bus/arrivals - refresh 파라미터")
    void getArrivals_withRefresh() throws Exception {
        BusArrivalResponse response = new BusArrivalResponse("정류소", List.of(), "2026-04-13 08:00:00");

        when(busArrivalService.getArrivals(37.5, 127.0, true))
                .thenReturn(Mono.just(response));

        MvcResult mvcResult = mockMvc.perform(get("/api/bus/arrivals")
                        .param("lat", "37.5")
                        .param("lon", "127.0")
                        .param("refresh", "true"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stationName").value("정류소"));
    }

    @Test
    @DisplayName("GET /api/bus/arrivals - 빈 도착정보")
    void getArrivals_empty() throws Exception {
        BusArrivalResponse response = new BusArrivalResponse("", List.of(), "2026-04-13 08:00:00");

        when(busArrivalService.getArrivals(37.0, 127.0, false))
                .thenReturn(Mono.just(response));

        MvcResult mvcResult = mockMvc.perform(get("/api/bus/arrivals")
                        .param("lat", "37.0")
                        .param("lon", "127.0"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arrivals").isEmpty());
    }
}
