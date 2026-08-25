package com.routeplan.optimization.algorithm;

import com.routeplan.optimization.domain.RouteResult;

record PathCost(int travelMinutes, long distanceMeters) implements Comparable<PathCost> {

    static final PathCost ZERO = new PathCost(0, 0);

    PathCost {
        if (travelMinutes < 0 || distanceMeters < 0) {
            throw new IllegalArgumentException("경로 비용은 0 이상이어야 합니다.");
        }
    }

    PathCost add(RouteResult route) {
        return new PathCost(
                Math.addExact(travelMinutes, route.estimatedTravelMinutes()),
                Math.addExact(distanceMeters, route.distanceMeters())
        );
    }

    @Override
    public int compareTo(PathCost other) {
        int minutesComparison = Integer.compare(travelMinutes, other.travelMinutes);
        if (minutesComparison != 0) {
            return minutesComparison;
        }
        return Long.compare(distanceMeters, other.distanceMeters);
    }
}
