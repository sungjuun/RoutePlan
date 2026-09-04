package com.routeplan.integration;

import com.routeplan.auth.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class RoadGeometryController {
    private final RoadGeometryService service;
    private final ResourceAccessService access;
    private final String browserKey;
    public RoadGeometryController(RoadGeometryService service, ResourceAccessService access,
            @Value("${routeplan.google.browser-key:}") String browserKey) {
        this.service = service; this.access = access; this.browserKey = browserKey;
    }
    @GetMapping("/integrations/maps-config")
    public Map<String, String> config() { return Map.of("browserKey", browserKey); }
    @PostMapping("/itineraries/{itineraryId}/road-geometry")
    public RoadGeometryService.Geometry geometry(@PathVariable long itineraryId, @RequestParam LocalDate date,
            @AuthenticationPrincipal RoutePlanPrincipal user) {
        access.requireItineraryViewer(itineraryId, user.userId());
        return service.fetch(itineraryId, date);
    }
}
