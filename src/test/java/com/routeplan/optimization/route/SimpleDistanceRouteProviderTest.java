package com.routeplan.optimization.route;

import static org.assertj.core.api.Assertions.assertThat;

import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.RouteResult;
import com.routeplan.trip.domain.TransportMode;
import org.junit.jupiter.api.Test;

class SimpleDistanceRouteProviderTest {

    private final SimpleDistanceRouteProvider routeProvider = new SimpleDistanceRouteProvider();

    @Test
    void sameCoordinatesHaveNoTravelCost() {
        Location seoul = new Location(37.5665, 126.9780);

        RouteResult result = routeProvider.getRoute(seoul, seoul, TransportMode.WALKING);

        assertThat(result.distanceMeters()).isZero();
        assertThat(result.estimatedTravelMinutes()).isZero();
    }

    @Test
    void calculatesSeoulToBusanHaversineDistanceWithinTolerance() {
        Location seoul = new Location(37.5665, 126.9780);
        Location busan = new Location(35.1796, 129.0756);

        RouteResult result = routeProvider.getRoute(seoul, busan, TransportMode.DRIVING);

        assertThat(result.distanceMeters()).isBetween(320_000L, 330_000L);
    }

    @Test
    void distanceIsSymmetricAndTransportModeOnlyChangesEstimatedTime() {
        Location origin = new Location(34.6654, 135.5019);
        Location destination = new Location(34.6873, 135.5262);

        RouteResult walking = routeProvider.getRoute(origin, destination, TransportMode.WALKING);
        RouteResult transit = routeProvider.getRoute(destination, origin, TransportMode.PUBLIC_TRANSIT);

        assertThat(walking.distanceMeters()).isEqualTo(transit.distanceMeters());
        assertThat(walking.estimatedTravelMinutes()).isGreaterThan(transit.estimatedTravelMinutes());
    }

    @Test
    void handlesAntipodalCoordinatesWithoutFloatingPointFailure() {
        RouteResult result = routeProvider.getRoute(
                new Location(0, 0),
                new Location(0, 180),
                TransportMode.DRIVING
        );

        assertThat(result.distanceMeters()).isBetween(20_000_000L, 20_020_000L);
    }
}
