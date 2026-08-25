package com.routeplan.optimization.route;

import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.RouteResult;
import com.routeplan.trip.domain.TransportMode;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SimpleDistanceRouteProvider implements RouteProvider {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final Map<TransportMode, Double> SPEED_KILOMETERS_PER_HOUR = speeds();

    @Override
    public RouteResult getRoute(Location origin, Location destination, TransportMode mode) {
        if (origin == null || destination == null || mode == null) {
            throw new IllegalArgumentException("출발지, 도착지, 이동수단은 필수입니다.");
        }

        long distanceMeters = haversineDistanceMeters(origin, destination);
        if (distanceMeters == 0) {
            return new RouteResult(0, 0);
        }

        double speed = SPEED_KILOMETERS_PER_HOUR.get(mode);
        int minutes = (int) Math.max(1, Math.ceil(distanceMeters * 60.0 / (speed * 1_000.0)));
        return new RouteResult(distanceMeters, minutes);
    }

    private long haversineDistanceMeters(Location origin, Location destination) {
        double latitudeDelta = Math.toRadians(destination.latitude() - origin.latitude());
        double longitudeDelta = Math.toRadians(destination.longitude() - origin.longitude());
        double originLatitude = Math.toRadians(origin.latitude());
        double destinationLatitude = Math.toRadians(destination.latitude());

        double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(originLatitude) * Math.cos(destinationLatitude)
                * Math.pow(Math.sin(longitudeDelta / 2), 2);
        double normalizedHaversine = Math.min(1.0, Math.max(0.0, haversine));
        double centralAngle = 2 * Math.atan2(
                Math.sqrt(normalizedHaversine),
                Math.sqrt(1 - normalizedHaversine)
        );
        return Math.round(EARTH_RADIUS_METERS * centralAngle);
    }

    private static Map<TransportMode, Double> speeds() {
        EnumMap<TransportMode, Double> speeds = new EnumMap<>(TransportMode.class);
        speeds.put(TransportMode.WALKING, 4.5);
        speeds.put(TransportMode.PUBLIC_TRANSIT, 25.0);
        speeds.put(TransportMode.DRIVING, 30.0);
        return Map.copyOf(speeds);
    }
}
