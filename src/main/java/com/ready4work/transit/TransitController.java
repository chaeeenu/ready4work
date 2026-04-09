package com.ready4work.transit;

import com.ready4work.transit.dto.TransitRouteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class TransitController {

    private final TransitService transitService;

    @GetMapping("/api/transit/routes")
    public Mono<TransitRouteResponse> getRoutes(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam(required = false) String departureTime,
            @RequestParam(required = false, defaultValue = "false") boolean refresh
    ) {
        return transitService.getRoutes(origin, destination, departureTime, refresh);
    }
}
