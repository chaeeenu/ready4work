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
    private final TransitEnrichmentService enrichmentService;

    @GetMapping("/api/transit/routes")
    public Mono<TransitRouteResponse> getRoutes(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam(required = false, defaultValue = "false") boolean refresh,
            @RequestParam(required = false, defaultValue = "false") boolean enrich
    ) {
        Mono<TransitRouteResponse> routes = transitService.getRoutes(origin, destination, refresh);
        if (enrich) {
            return routes.flatMap(enrichmentService::enrich);
        }
        return routes;
    }
}
