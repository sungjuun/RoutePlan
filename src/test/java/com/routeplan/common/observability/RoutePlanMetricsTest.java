package com.routeplan.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.routeplan.integration.google.ExternalProviderFailure;
import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.OptimizationAlgorithm;
import com.routeplan.optimization.domain.RouteResult;
import com.routeplan.optimization.route.RouteDataType;
import com.routeplan.optimization.route.RouteMatrix;
import com.routeplan.trip.domain.TransportMode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoutePlanMetricsTest {

    @Test
    void recordsBoundedGenerationRouteAndFailureMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RoutePlanMetrics metrics = new RoutePlanMetrics(registry);
        var sample = metrics.startGeneration();

        metrics.recordGeneration(
                sample,
                RoutePlanMetrics.GenerationType.REOPTIMIZATION,
                OptimizationAlgorithm.NEAREST_NEIGHBOR,
                RoutePlanMetrics.Outcome.SUCCESS
        );
        metrics.recordRouteMatrix(routeMatrix());
        metrics.recordRouteApiFailure(ExternalProviderFailure.RATE_LIMITED);

        assertThat(registry.find("routeplan.itinerary.generation.duration")
                .tags("type", "reoptimization", "outcome", "success")
                .timer().count()).isEqualTo(1);
        assertThat(registry.find("routeplan.itinerary.reoptimization.total")
                .tag("outcome", "success").counter().count()).isEqualTo(1);
        assertThat(registry.find("routeplan.route.api.calls").counter().count()).isEqualTo(2);
        assertThat(registry.find("routeplan.route.cache.hits").counter().count()).isEqualTo(1);
        assertThat(registry.find("routeplan.route.cache.misses").counter().count()).isEqualTo(2);
        assertThat(registry.find("routeplan.route.cache.failures").counter().count()).isEqualTo(1);
        assertThat(registry.find("routeplan.route.api.failures")
                .tag("reason", "RATE_LIMITED").counter().count()).isEqualTo(1);
    }

    private RouteMatrix routeMatrix() {
        Location location = Location.of(
                new BigDecimal("34.665400"),
                new BigDecimal("135.501900")
        );
        return new RouteMatrix(
                TransportMode.WALKING,
                RouteDataType.GOOGLE_ROUTES,
                Map.of(new RouteMatrix.Leg(location, location), new RouteResult(0, 0)),
                2,
                5,
                true,
                1,
                2,
                1
        );
    }
}
