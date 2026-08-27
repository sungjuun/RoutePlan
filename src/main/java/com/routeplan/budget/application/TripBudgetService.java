package com.routeplan.budget.application;

import com.routeplan.budget.domain.BudgetCurrency;
import com.routeplan.budget.domain.BudgetSettings;
import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import com.routeplan.trip.domain.Trip;
import com.routeplan.trip.domain.TripPlace;
import com.routeplan.trip.persistence.TripPlaceRepository;
import com.routeplan.trip.persistence.TripRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripBudgetService {

    private final TripRepository tripRepository;
    private final TripPlaceRepository placeRepository;

    public TripBudgetService(TripRepository tripRepository, TripPlaceRepository placeRepository) {
        this.tripRepository = tripRepository;
        this.placeRepository = placeRepository;
    }

    @Transactional(readOnly = true)
    public BudgetView get(Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_NOT_FOUND));
        return view(trip, placeRepository.findAllByTripIdOrderByIdAsc(tripId));
    }

    @Transactional
    public BudgetView replace(Long tripId, BudgetSettings settings, List<PlaceCostCommand> costs) {
        Trip trip = tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_NOT_FOUND));
        List<TripPlace> places = placeRepository.findAllByTripIdOrderByIdAsc(tripId);
        Map<Long, Long> byId = new HashMap<>();
        if (costs == null) throw new IllegalArgumentException("장소 비용 목록은 필수입니다.");
        for (PlaceCostCommand cost : costs) {
            if (cost == null || cost.placeId() == null || byId.containsKey(cost.placeId())) {
                throw new IllegalArgumentException("장소 비용은 장소별 한 번만 지정해야 합니다.");
            }
            BudgetSettings.requireAmount(cost.estimatedCostMinor());
            byId.put(cost.placeId(), cost.estimatedCostMinor());
        }
        if (!byId.keySet().equals(places.stream()
                .map(place -> place.getPlace().getId()).collect(Collectors.toSet()))) {
            throw new RoutePlanException(ErrorCode.CONFLICT,
                    "현재 여행의 모든 장소 비용을 함께 저장해야 합니다. 예산 설정을 다시 불러와 주세요.");
        }
        trip.updateBudget(settings);
        places.forEach(place -> place.updateEstimatedCost(byId.get(place.getPlace().getId())));
        return view(trip, places);
    }

    private BudgetView view(Trip trip, List<TripPlace> places) {
        BudgetSettings settings = trip.getBudgetSettings();
        return new BudgetView(settings.currency(), settings.limitMinor(), settings.fixedCostMinor(),
                places.stream().map(place -> new PlaceCostView(
                        place.getPlace().getId(), place.getPlace().getName(), place.isMustVisit(),
                        place.getEstimatedCostMinor()
                )).toList());
    }

    public record PlaceCostCommand(Long placeId, Long estimatedCostMinor) {}

    public record PlaceCostView(Long placeId, String placeName, boolean mustVisit, Long estimatedCostMinor) {}

    public record BudgetView(
            BudgetCurrency currency, Long limitMinor, long fixedCostMinor, List<PlaceCostView> placeCosts
    ) {}
}
