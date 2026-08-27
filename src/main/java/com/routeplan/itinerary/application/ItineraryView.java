package com.routeplan.itinerary.application;

import com.routeplan.budget.domain.BudgetCurrency;
import com.routeplan.budget.domain.BudgetSettings;
import com.routeplan.itinerary.domain.Itinerary;
import com.routeplan.itinerary.domain.ItineraryItem;
import com.routeplan.itinerary.domain.ItineraryDay;
import com.routeplan.itinerary.domain.ItineraryExclusion;
import com.routeplan.itinerary.domain.ItineraryChangeReason;
import com.routeplan.itinerary.domain.ItineraryGenerationType;
import com.routeplan.itinerary.domain.ItineraryItemStatus;
import com.routeplan.optimization.constraint.ExclusionReason;
import com.routeplan.optimization.domain.OptimizationAlgorithm;
import com.routeplan.optimization.route.RouteDataType;
import com.routeplan.place.domain.PlaceEnvironment;
import com.routeplan.weather.domain.WeatherCondition;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public record ItineraryView(
        Long itineraryId,
        Long tripId,
        int version,
        ItineraryGenerationType generationType,
        Long parentItineraryId,
        ItineraryChangeReason changeReason,
        String changeReasonDetail,
        LocalDate reoptimizationStartDate,
        LocalTime reoptimizationStartTime,
        BigDecimal reoptimizationStartLatitude,
        BigDecimal reoptimizationStartLongitude,
        OptimizationAlgorithm algorithm,
        long totalDistanceMeters,
        int estimatedTravelMinutes,
        int optimizationScore,
        int visitedPriorityScore,
        int totalStayMinutes,
        int totalWaitingMinutes,
        boolean closedTour,
        long returnTravelDistanceMeters,
        int returnTravelMinutes,
        LocalTime returnArrivalTime,
        RouteDataType routeDataType,
        int routeProviderCallCount,
        int routeMatrixElementCount,
        long routeMatrixBuildMillis,
        boolean routeCacheEnabled,
        int routeCacheHitCount,
        int routeCacheMissCount,
        int routeCacheFailureCount,
        double routeCacheHitRatio,
        CostSummary costSummary,
        Instant createdAt,
        List<Day> days,
        List<Item> items,
        List<Exclusion> exclusions
) {

    static ItineraryView from(Itinerary itinerary) {
        return new ItineraryView(
                itinerary.getId(),
                itinerary.getTrip().getId(),
                itinerary.getVersion(),
                itinerary.getGenerationType(),
                itinerary.getParentItinerary() == null
                        ? null : itinerary.getParentItinerary().getId(),
                itinerary.getChangeReason(),
                itinerary.getChangeReasonDetail(),
                itinerary.getReoptimizationStartDate(),
                itinerary.getReoptimizationStartTime(),
                itinerary.getReoptimizationStartLatitude(),
                itinerary.getReoptimizationStartLongitude(),
                itinerary.getAlgorithm(),
                itinerary.getTotalDistanceMeters(),
                itinerary.getEstimatedTravelMinutes(),
                itinerary.getOptimizationScore(),
                itinerary.getItems().stream()
                        .map(ItineraryItem::getPriority)
                        .filter(priority -> priority != null)
                        .mapToInt(Integer::intValue)
                        .sum(),
                itinerary.getTotalStayMinutes(),
                itinerary.getTotalWaitingMinutes(),
                itinerary.isReturnedToAccommodation(),
                itinerary.getReturnTravelDistanceMeters(),
                itinerary.getReturnTravelMinutes(),
                itinerary.getReturnArrivalTime(),
                itinerary.getRouteDataType(),
                itinerary.getRouteProviderCallCount(),
                itinerary.getRouteMatrixElementCount(),
                itinerary.getRouteMatrixBuildMillis(),
                itinerary.isRouteCacheEnabled(),
                itinerary.getRouteCacheHitCount(),
                itinerary.getRouteCacheMissCount(),
                itinerary.getRouteCacheFailureCount(),
                itinerary.getRouteCacheHitRatio(),
                CostSummary.from(itinerary),
                itinerary.getCreatedAt(),
                itinerary.getDays().stream().map(Day::from).toList(),
                itinerary.getItems().stream().map(Item::from).toList(),
                itinerary.getExclusions().stream()
                        .sorted(Comparator
                                .comparingInt(ItineraryExclusion::getPriority).reversed()
                                .thenComparing(exclusion -> exclusion.getPlace().getId()))
                        .map(Exclusion::from)
                        .toList()
        );
    }

    public record CostSummary(
            BudgetCurrency currency,
            Long limitMinor,
            long fixedCostMinor,
            long knownVisitCostMinor,
            long estimatedTotalMinor,
            int unpricedPlaceCount,
            Long remainingMinor
    ) {
        static CostSummary from(Itinerary itinerary) {
            BudgetSettings settings = itinerary.getBudgetSettings();
            long visitCost = itinerary.getItems().stream()
                    .map(ItineraryItem::getEstimatedCostMinor)
                    .filter(java.util.Objects::nonNull)
                    .reduce(0L, Math::addExact);
            int unpriced = (int) itinerary.getItems().stream()
                    .filter(item -> item.getEstimatedCostMinor() == null).count();
            long total = Math.addExact(settings.fixedCostMinor(), visitCost);
            Long remaining = settings.limitMinor() == null || unpriced > 0
                    ? null : settings.limitMinor() - total;
            return new CostSummary(settings.currency(), settings.limitMinor(),
                    settings.fixedCostMinor(), visitCost, total, unpriced, remaining);
        }
    }

    public record Day(
            int dayNumber,
            LocalDate visitDate,
            long totalDistanceMeters,
            int estimatedTravelMinutes,
            int totalStayMinutes,
            int totalWaitingMinutes,
            long returnTravelDistanceMeters,
            int returnTravelMinutes,
            LocalTime returnArrivalTime,
            boolean returnedToAccommodation,
            WeatherCondition weatherCondition,
            int precipitationProbability
    ) {

        static Day from(ItineraryDay day) {
            return new Day(
                    day.getDayNumber(),
                    day.getVisitDate(),
                    day.getTotalDistanceMeters(),
                    day.getEstimatedTravelMinutes(),
                    day.getTotalStayMinutes(),
                    day.getTotalWaitingMinutes(),
                    day.getReturnTravelDistanceMeters(),
                    day.getReturnTravelMinutes(),
                    day.getReturnArrivalTime(),
                    day.isReturnedToAccommodation(),
                    day.getWeatherCondition(),
                    day.getPrecipitationProbability()
            );
        }
    }

    public record Item(
            Long itineraryItemId,
            int sequence,
            Long placeId,
            String placeName,
            long travelDistanceMeters,
            int estimatedTravelMinutes,
            LocalDate visitDate,
            LocalTime arrivalTime,
            LocalTime startTime,
            LocalTime endTime,
            Integer waitingMinutes,
            Integer stayMinutes,
            Integer priority,
            Boolean mustVisit,
            PlaceEnvironment environment,
            int weatherScoreAdjustment,
            Long estimatedCostMinor,
            ItineraryItemStatus status
    ) {

        static Item from(ItineraryItem item) {
            return new Item(
                    item.getId(),
                    item.getSequence(),
                    item.getPlace().getId(),
                    item.getPlace().getName(),
                    item.getTravelDistanceMeters(),
                    item.getEstimatedTravelMinutes(),
                    item.getVisitDate(),
                    item.getArrivalTime(),
                    item.getStartTime(),
                    item.getEndTime(),
                    item.getWaitingMinutes(),
                    item.getStayMinutes(),
                    item.getPriority(),
                    item.getMustVisit(),
                    item.getPlace().getEnvironment(),
                    item.getWeatherScoreAdjustment(),
                    item.getEstimatedCostMinor(),
                    item.getStatus()
            );
        }
    }

    public record Exclusion(
            Long placeId,
            String placeName,
            int priority,
            ExclusionReason reason
    ) {

        static Exclusion from(ItineraryExclusion exclusion) {
            return new Exclusion(
                    exclusion.getPlace().getId(),
                    exclusion.getPlace().getName(),
                    exclusion.getPriority(),
                    exclusion.getReason()
            );
        }
    }
}
