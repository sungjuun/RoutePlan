package com.routeplan.place.domain;

import java.util.Locale;
import java.util.Set;

public enum PlaceEnvironment {
    INDOOR,
    OUTDOOR,
    MIXED;

    private static final Set<String> INDOOR_TYPES = Set.of(
            "bakery", "cafe", "coffee_shop", "department_store", "food", "lodging",
            "movie_theater", "museum", "restaurant", "shopping_mall", "store"
    );
    private static final Set<String> OUTDOOR_TYPES = Set.of(
            "amusement_park", "beach", "botanical_garden", "campground", "garden",
            "hiking_area", "national_park", "park", "playground", "scenic_spot", "zoo"
    );

    public static PlaceEnvironment infer(String category) {
        if (category == null || category.isBlank()) {
            return MIXED;
        }
        String normalized = category.strip().toLowerCase(Locale.ROOT);
        if (INDOOR_TYPES.contains(normalized)) {
            return INDOOR;
        }
        if (OUTDOOR_TYPES.contains(normalized)) {
            return OUTDOOR;
        }
        return MIXED;
    }
}
