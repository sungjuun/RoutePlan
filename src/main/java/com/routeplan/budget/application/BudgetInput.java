package com.routeplan.budget.application;

import com.routeplan.budget.domain.BudgetSettings;
import com.routeplan.optimization.constraint.BudgetConstraintException;
import com.routeplan.optimization.constraint.ScheduleBudget;
import com.routeplan.trip.domain.Trip;
import com.routeplan.trip.domain.TripPlace;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record BudgetInput(BudgetSettings settings, Map<Long, Long> costsByPlaceId) {

    public BudgetInput {
        costsByPlaceId = Map.copyOf(costsByPlaceId);
    }

    public static BudgetInput from(Trip trip, List<TripPlace> places) {
        return new BudgetInput(trip.getBudgetSettings(), places.stream()
                .filter(place -> place.getEstimatedCostMinor() != null)
                .collect(Collectors.toMap(
                        place -> place.getPlace().getId(), TripPlace::getEstimatedCostMinor
                )));
    }

    public ScheduleBudget remaining(long completedCostMinor, boolean unknownCompletedCost) {
        if (settings.limitMinor() == null) {
            return new ScheduleBudget(null, costsByPlaceId);
        }
        if (unknownCompletedCost) {
            throw new BudgetConstraintException(BudgetConstraintException.Reason.MISSING_COST);
        }
        long committedCost = Math.addExact(settings.fixedCostMinor(), completedCostMinor);
        if (committedCost > settings.limitMinor()) {
            throw new BudgetConstraintException(BudgetConstraintException.Reason.EXCEEDED);
        }
        return new ScheduleBudget(settings.limitMinor() - committedCost, costsByPlaceId);
    }
}
