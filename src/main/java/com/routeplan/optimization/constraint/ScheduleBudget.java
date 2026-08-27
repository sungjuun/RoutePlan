package com.routeplan.optimization.constraint;

import java.util.List;
import java.util.Map;

/** Remaining visit budget, after fixed expenses and completed visit snapshots. */
public record ScheduleBudget(Long availableMinor, Map<Long, Long> costsByPlaceId) {

    public ScheduleBudget {
        costsByPlaceId = Map.copyOf(costsByPlaceId);
        if ((availableMinor != null && availableMinor < 0)
                || costsByPlaceId.values().stream().anyMatch(cost -> cost < 0)) {
            throw new IllegalArgumentException("예산과 장소 비용은 음수일 수 없습니다.");
        }
    }

    public static ScheduleBudget unlimited() {
        return new ScheduleBudget(null, Map.of());
    }

    public void validate(List<ScheduleCandidate> candidates) {
        if (availableMinor == null) return;
        if (candidates.stream().anyMatch(candidate -> !costsByPlaceId.containsKey(candidate.placeId()))) {
            throw new BudgetConstraintException(BudgetConstraintException.Reason.MISSING_COST);
        }
        if (mandatoryCost(candidates) > availableMinor) {
            throw new BudgetConstraintException(BudgetConstraintException.Reason.EXCEEDED);
        }
    }

    public long mandatoryCost(List<ScheduleCandidate> candidates) {
        return candidates.stream().filter(ScheduleCandidate::mustVisit)
                .mapToLong(candidate -> cost(candidate.placeId())).reduce(0L, Math::addExact);
    }

    public long cost(long placeId) {
        return costsByPlaceId.getOrDefault(placeId, 0L);
    }
}
