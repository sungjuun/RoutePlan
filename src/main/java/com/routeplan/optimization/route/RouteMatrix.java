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

    public RouteMatrix(
            TransportMode transportMode,
            RouteDataType dataType,
            Map<Leg, RouteResult> routes,
            int providerCallCount,
            long buildMillis
    ) {
        this.transportMode = Objects.requireNonNull(transportMode, "이동수단은 필수입니다.");
        this.dataType = Objects.requireNonNull(dataType, "경로 데이터 유형은 필수입니다.");
        Objects.requireNonNull(routes, "경로 Matrix는 필수입니다.");
        if (providerCallCount < 0 || buildMillis < 0) {
            throw new IllegalArgumentException("경로 Provider 측정값은 0 이상이어야 합니다.");
        }
        this.routes = Map.copyOf(routes);
        this.providerCallCount = providerCallCount;
        this.buildMillis = buildMillis;
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

    public int providerCallCount() {
        return providerCallCount;
    }

    public int elementCount() {
        return routes.size();
    }

    public long buildMillis() {
        return buildMillis;
    }

    public record Leg(Location origin, Location destination) {

        public Leg {
            Objects.requireNonNull(origin, "출발 좌표는 필수입니다.");
            Objects.requireNonNull(destination, "도착 좌표는 필수입니다.");
        }
    }
}
