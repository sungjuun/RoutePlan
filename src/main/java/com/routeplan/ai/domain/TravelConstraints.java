package com.routeplan.ai.domain;

import com.routeplan.trip.domain.TransportMode;
import com.routeplan.trip.domain.TripPace;
import java.time.LocalTime;
import java.util.List;

public record TravelConstraints(
        LocalTime dailyStartTime,
        LocalTime dailyEndTime,
        TripPace pace,
        TransportMode transportMode,
        WalkingPreference walkingPreference,
        List<PlaceConstraint> placeConstraints,
        List<String> notes
) {

    public TravelConstraints {
        placeConstraints = placeConstraints == null ? List.of() : List.copyOf(placeConstraints);
        notes = notes == null ? List.of() : notes.stream()
                .filter(note -> note != null && !note.isBlank())
                .map(String::trim)
                .toList();
    }

    public record PlaceConstraint(
            String placeName,
            PlacePreference preference,
            LocalTime preferredStartTime,
            LocalTime preferredEndTime,
            Integer minimumStayMinutes,
            Integer maximumStayMinutes,
            MealType mealType
    ) {
    }
}
