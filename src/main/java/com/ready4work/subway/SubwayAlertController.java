package com.ready4work.subway;

import com.ready4work.subway.dto.SubwayAlertResponse;
import com.ready4work.subway.dto.SubwayArrivalResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class SubwayAlertController {

    private final SubwayAlertService subwayAlertService;
    private final SubwayArrivalService subwayArrivalService;

    public SubwayAlertController(SubwayAlertService subwayAlertService,
                                  SubwayArrivalService subwayArrivalService) {
        this.subwayAlertService = subwayAlertService;
        this.subwayArrivalService = subwayArrivalService;
    }

    @GetMapping("/api/alerts/subway")
    public Mono<SubwayAlertResponse> getAlerts(
            @RequestParam(required = false, defaultValue = "false") boolean refresh) {
        return subwayAlertService.getAlerts(refresh);
    }

    @GetMapping("/api/subway/arrivals")
    public Mono<SubwayArrivalResponse> getArrivals(
            @RequestParam String stationName,
            @RequestParam(required = false, defaultValue = "false") boolean refresh) {
        return subwayArrivalService.getArrivals(stationName, refresh);
    }
}
