package com.routeplan.optimization.route;

import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.RouteResult;
import com.routeplan.trip.domain.TransportMode;
import java.util.Map;
import java.util.Objects;

public final class RouteMatrix implements RouteProvider {

    private final TransportMode transportMode;
    private final RouteDataType dataType;
    private final Map<Leg, RouteResult> routes;
    private final int providerCallCount;
    private final long buildMillis;
    private final boolean cacheEnabled;
    private final int cacheHitCount;
    private final int cacheMissCount;
    private final int cacheFailureCount;
    private int accountedElements;

    public RouteMatrix(
            TransportMode transportMode,
            RouteDataType dataType,
            Map<Leg, RouteResult> routes,
            int providerCallCount,
            long buildMillis
    ) {
        this(
                transportMode, dataType, routes, providerCallCount, buildMillis,
                false, 0, 0, 0
        );
    }

    public RouteMatrix(
            TransportMode transportMode,
            RouteDataType dataType,
            Map<Leg, RouteResult> routes,
            int providerCallCount,
            long buildMillis,
            boolean cacheEnabled,
            int cacheHitCount,
            int cacheMissCount,
            int cacheFailureCount
    ) {
        this.transportMode = Objects.requireNonNull(transportMode, "이동수단은 필수입니다.");
        this.dataType = Objects.requireNonNull(dataType, "경로 데이터 유형은 필수입니다.");
        Objects.requireNonNull(routes, "경로 Matrix는 필수입니다.");
        if (providerCallCount < 0 || buildMillis < 0
                || cacheHitCount < 0 || cacheMissCount < 0 || cacheFailureCount < 0) {
            throw new IllegalArgumentException("경로 Provider 측정값은 0 이상이어야 합니다.");
        }
        if (!cacheEnabled && (cacheHitCount != 0 || cacheMissCount != 0 || cacheFailureCount != 0)) {
            throw new IllegalArgumentException("비활성 Route Cache에는 측정값이 있을 수 없습니다.");
        }
        this.routes = Map.copyOf(routes);
        this.accountedElements = routes.size();
        this.providerCallCount = providerCallCount;
        this.buildMillis = buildMillis;
        this.cacheEnabled = cacheEnabled;
        this.cacheHitCount = cacheHitCount;
        this.cacheMissCount = cacheMissCount;
        this.cacheFailureCount = cacheFailureCount;
    }

    @Override
    public RouteResult getRoute(Location origin, Location destination, TransportMode mode) {
        if (mode != transportMode) {
            throw new IllegalArgumentException("Route Matrix의 이동수단과 요청 이동수단이 다릅니다.");
        }
        RouteResult route = routes.get(new Leg(origin, destination));
        if (route == null) {
            throw new IllegalArgumentException("Route Matrix에 요청 구간이 없습니다.");
        }
        return route;
    }

    public RouteDataType dataType() {
        return dataType;
    }

    public TransportMode transportMode() {
        return transportMode;
    }

    public int providerCallCount() {
        return providerCallCount;
    }

    public int elementCount() {
        return accountedElements;
    }

    public static RouteMatrix summarize(java.util.Collection<RouteMatrix> values) {
        var matrices = values.stream().distinct().toList();
        var first = matrices.getFirst();
        RouteMatrix summary = new RouteMatrix(first.transportMode, first.dataType, first.routes,
                matrices.stream().mapToInt(RouteMatrix::providerCallCount).sum(),
                matrices.stream().mapToLong(RouteMatrix::buildMillis).sum(),
                matrices.stream().anyMatch(RouteMatrix::cacheEnabled),
                matrices.stream().mapToInt(RouteMatrix::cacheHitCount).sum(),
                matrices.stream().mapToInt(RouteMatrix::cacheMissCount).sum(),
                matrices.stream().mapToInt(RouteMatrix::cacheFailureCount).sum());
        summary.accountedElements = matrices.stream().mapToInt(RouteMatrix::elementCount).sum();
        return summary;
    }

    public long buildMillis() {
        return buildMillis;
    }

    public RouteMatrix withAdditionalElements(int calls, long millis) {
        var combined = new RouteMatrix(transportMode, dataType, routes, providerCallCount + calls,
                buildMillis + millis, cacheEnabled, cacheHitCount, cacheMissCount, cacheFailureCount);
        combined.accountedElements = accountedElements + calls;
        return combined;
    }

    public boolean cacheEnabled() {
        return cacheEnabled;
    }

    public int cacheHitCount() {
        return cacheHitCount;
    }

    public int cacheMissCount() {
        return cacheMissCount;
    }

    public int cacheFailureCount() {
        return cacheFailureCount;
    }

    public double cacheHitRatio() {
        int lookups = cacheHitCount + cacheMissCount;
        return lookups == 0 ? 0.0 : (double) cacheHitCount / lookups;
    }

    public record Leg(Location origin, Location destination) {

        public Leg {
            Objects.requireNonNull(origin, "출발 좌표는 필수입니다.");
            Objects.requireNonNull(destination, "도착 좌표는 필수입니다.");
        }
    }
}
