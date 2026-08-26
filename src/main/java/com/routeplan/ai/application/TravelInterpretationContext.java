package com.routeplan.ai.application;

import com.routeplan.trip.domain.TransportMode;
import com.routeplan.trip.domain.TripPace;
import java.time.LocalTime;
import java.util.List;

public record TravelInterpretationContext(
        Long userId,
        String userRequest,
        LocalTime currentDailyStartTime,
        LocalTime currentDailyEndTime,
        TripPace currentPace,
        TransportMode currentTransportMode,
        List<KnownPlace> knownPlaces
) {

    public TravelInterpretationContext {
        knownPlaces = knownPlaces == null ? List.of() : List.copyOf(knownPlaces);
    }

    public record KnownPlace(Long placeId, String name) {
    }
}
