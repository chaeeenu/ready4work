package com.ready4work.bus;

import com.ready4work.bus.dto.BusArrivalResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class BusArrivalController {

    private final BusArrivalService busArrivalService;

    public BusArrivalController(BusArrivalService busArrivalService) {
        this.busArrivalService = busArrivalService;
    }

    @GetMapping("/api/bus/arrivals")
    public Mono<BusArrivalResponse> getArrivals(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(required = false, defaultValue = "false") boolean refresh) {
        return busArrivalService.getArrivals(lat, lon, refresh);
    }
}
