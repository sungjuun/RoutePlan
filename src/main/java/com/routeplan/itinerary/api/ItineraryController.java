package com.routeplan.itinerary.api;

import com.routeplan.itinerary.application.ItineraryOptimizationService;
import com.routeplan.itinerary.application.ItineraryQueryService;
import com.routeplan.itinerary.application.ItineraryView;
import com.routeplan.optimization.domain.OptimizationAlgorithm;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ItineraryController {

    private final ItineraryOptimizationService optimizationService;
    private final ItineraryQueryService queryService;

    public ItineraryController(
            ItineraryOptimizationService optimizationService,
            ItineraryQueryService queryService
    ) {
        this.optimizationService = optimizationService;
        this.queryService = queryService;
    }

    @PostMapping("/trips/{tripId}/optimize")
    public ResponseEntity<ItineraryView> optimize(
            @PathVariable Long tripId,
            @RequestParam(defaultValue = "NEAREST_NEIGHBOR") OptimizationAlgorithm algorithm
    ) {
        ItineraryView result = optimizationService.optimize(tripId, algorithm);
        return ResponseEntity.created(
                URI.create("/api/v1/itineraries/" + result.itineraryId())
        ).body(result);
    }

    @GetMapping("/trips/{tripId}/itineraries/latest")
    public ItineraryView getLatest(@PathVariable Long tripId) {
        return queryService.getLatest(tripId);
    }

    @GetMapping("/itineraries/{itineraryId}")
    public ItineraryView get(@PathVariable Long itineraryId) {
        return queryService.get(itineraryId);
    }
}
