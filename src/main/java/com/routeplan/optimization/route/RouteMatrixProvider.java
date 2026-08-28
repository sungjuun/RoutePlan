package com.routeplan.optimization.route;

import com.routeplan.optimization.domain.Location;
import com.routeplan.trip.domain.TransportMode;
import java.util.List;

public interface RouteMatrixProvider {

    RouteMatrix build(List<Location> locations, TransportMode transportMode);

    default RouteMatrix build(List<Location> locations, TransportMode mode, java.time.Instant departure) {
        return build(locations, mode);
    }

    default java.util.Map<java.time.LocalDate, RouteMatrix> buildForDates(
            List<Location> locations, TransportMode mode, List<java.time.LocalDate> dates,
            java.time.LocalTime firstStart, java.time.LocalTime dailyStart, String timeZone
    ) {
        java.util.Map<java.time.LocalDate, RouteMatrix> result = new java.util.LinkedHashMap<>();
        for (var date : dates) {
            var departure = com.routeplan.integration.TravelTime.departure(
                    date, result.isEmpty() ? firstStart : dailyStart, timeZone);
            result.put(date, mode != TransportMode.PUBLIC_TRANSIT && !result.isEmpty()
                    ? result.values().iterator().next() : build(locations, mode, departure));
        }
        return result;
    }
}
