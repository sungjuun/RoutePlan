package com.routeplan.optimization.algorithm;

import com.routeplan.optimization.domain.VisitCandidate;
import java.util.Comparator;
import java.util.List;

final class RouteOrders {

    private RouteOrders() {
    }

    static int compareLexicographically(List<VisitCandidate> left, List<VisitCandidate> right) {
        for (int index = 0; index < left.size(); index++) {
            int comparison = Long.compare(
                    left.get(index).tripPlaceId(),
                    right.get(index).tripPlaceId()
            );
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    static Comparator<VisitCandidate> stableCandidateOrder() {
        return Comparator.comparingLong(VisitCandidate::tripPlaceId);
    }
}
