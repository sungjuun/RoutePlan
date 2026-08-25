package com.routeplan.place.search;

import java.math.BigDecimal;

public record PlaceSearchResult(
        String externalPlaceId,
        String name,
        String formattedAddress,
        BigDecimal latitude,
        BigDecimal longitude,
        String primaryType,
        String provider
) {
}
