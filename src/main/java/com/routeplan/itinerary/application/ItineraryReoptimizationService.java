package com.routeplan.itinerary.application;

import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import com.routeplan.itinerary.domain.Itinerary;
import com.routeplan.itinerary.domain.ItineraryChangeReason;
import com.routeplan.itinerary.domain.ItineraryItem;
import com.routeplan.itinerary.domain.ItineraryItemStatus;
import com.routeplan.itinerary.persistence.ItineraryRepository;
import com.routeplan.optimization.algorithm.ExactSearchOptimizationEngine;
import com.routeplan.optimization.algorithm.OptimizationEngineRegistry;
import com.routeplan.optimization.constraint.ConstraintSchedule;
import com.routeplan.optimization.constraint.ConstraintSchedulePlanner;
import com.routeplan.optimization.constraint.ScheduleCandidate;
import com.routeplan.optimization.constraint.ScheduleRequest;
import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.OptimizationAlgorithm;
import com.routeplan.optimization.domain.OptimizationRequest;
import com.routeplan.optimization.domain.OptimizationResult;
import com.routeplan.optimization.domain.VisitCandidate;
import com.routeplan.optimization.route.RouteMatrix;
import com.routeplan.optimization.route.RouteMatrixProvider;
import com.routeplan.place.domain.Place;
import com.routeplan.place.domain.PlaceOpeningHour;
import com.routeplan.place.persistence.PlaceOpeningHourRepository;
import com.routeplan.trip.domain.Trip;
import com.routeplan.trip.domain.TripPlace;
import com.routeplan.trip.persistence.TripPlaceRepository;
import com.routeplan.trip.persistence.TripRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ItineraryReoptimizationService {

    private final TripRepository tripRepository;
    private final TripPlaceRepository tripPlaceRepository;
    private final ItineraryRepository itineraryRepository;
    private final OptimizationEngineRegistry optimizationEngineRegistry;
    private final PlaceOpeningHourRepository openingHourRepository;
    private final ConstraintSchedulePlanner schedulePlanner;
    private final RouteMatrixProvider routeMatrixProvider;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;

    public ItineraryReoptimizationService(
            TripRepository tripRepository,
            TripPlaceRepository tripPlaceRepository,
            ItineraryRepository itineraryRepository,
            OptimizationEngineRegistry optimizationEngineRegistry,
            PlaceOpeningHourRepository openingHourRepository,
            ConstraintSchedulePlanner schedulePlanner,
            RouteMatrixProvider routeMatrixProvider,
            PlatformTransactionManager transactionManager
    ) {
        this.tripRepository = tripRepository;
        this.tripPlaceRepository = tripPlaceRepository;
        this.itineraryRepository = itineraryRepository;
        this.optimizationEngineRegistry = optimizationEngineRegistry;
        this.openingHourRepository = openingHourRepository;
        this.schedulePlanner = schedulePlanner;
        this.routeMatrixProvider = routeMatrixProvider;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    public ItineraryView reoptimize(
            Long tripId,
            OptimizationAlgorithm algorithm,
            ReoptimizeCommand command
    ) {
        ReoptimizationSnapshot snapshot = Objects.requireNonNull(
                readTransaction.execute(status -> loadSnapshot(tripId, algorithm, command))
        );
        RouteMatrix routeMatrix = routeMatrixProvider.build(
                locations(snapshot.input()),
                snapshot.input().optimizationRequest().transportMode()
        );
        OptimizationResult result = optimizationEngineRegistry.get(algorithm)
                .optimize(snapshot.input().optimizationRequest(), routeMatrix);
        ConstraintSchedule schedule = schedulePlanner.plan(
                snapshot.input().toScheduleRequest(result),
                routeMatrix
        );
        return Objects.requireNonNull(writeTransaction.execute(status ->
                saveIfUnchanged(snapshot, result, schedule, routeMatrix)
        ));
    }

    private ReoptimizationSnapshot loadSnapshot(
            Long tripId,
            OptimizationAlgorithm algorithm,
            ReoptimizeCommand command
    ) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_NOT_FOUND));
        if (!trip.getStartDate().equals(trip.getEndDate())) {
            throw invalidState("다일 여행은 일자별 전체 재계산을 사용해 주세요.");
        }
        Itinerary source = itineraryRepository.findDetailedById(command.sourceItineraryId())
                .orElseThrow(() -> new RoutePlanException(ErrorCode.ITINERARY_NOT_FOUND));
        validateSource(tripId, source);
        validateCurrentTime(trip, command.currentTime());
        List<ItineraryItem> completedItems = completedPrefix(source, command.completedItemIds());
        validateCompletedTimes(completedItems, command.currentTime());
        Set<Long> completedPlaceIds = completedItems.stream()
                .map(item -> item.getPlace().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<TripPlace> tripPlaces = tripPlaceRepository.findAllByTripIdOrderByIdAsc(tripId);
        ReoptimizationInput input = buildInput(trip, tripPlaces, completedPlaceIds, command);
        validateExactLimit(algorithm, input.optimizationRequest().candidates().size());
        return new ReoptimizationSnapshot(
                source.getId(),
                source.getVersion(),
                command,
                completedPlaceIds,
                completedItems.stream().map(CompletedVisit::from).toList(),
                input
        );
    }

    private void validateSource(Long tripId, Itinerary source) {
        if (!source.getTrip().getId().equals(tripId)) {
            throw new RoutePlanException(ErrorCode.REOPTIMIZATION_SOURCE_MISMATCH);
        }
        if (itineraryRepository.findMaxVersionByTripId(tripId) != source.getVersion()) {
            throw new RoutePlanException(ErrorCode.REOPTIMIZATION_SOURCE_NOT_LATEST);
        }
    }

    private void validateCurrentTime(Trip trip, LocalTime currentTime) {
        if (currentTime == null
                || currentTime.isBefore(trip.getDailyStartTime())
                || !currentTime.isBefore(trip.getDailyEndTime())) {
            throw invalidState("현재 시각은 여행 시작 이상, 종료 미만이어야 합니다.");
        }
    }

    private List<ItineraryItem> completedPrefix(
            Itinerary source,
            List<Long> completedItemIds
    ) {
        if (completedItemIds == null || new HashSet<>(completedItemIds).size() != completedItemIds.size()) {
            throw invalidState("완료 일정 항목은 중복될 수 없습니다.");
        }
        List<ItineraryItem> sourceItems = source.getItems();
        if (completedItemIds.size() > sourceItems.size()) {
            throw invalidState("완료 일정 항목 수가 기준 일정보다 많습니다.");
        }
        for (int index = 0; index < completedItemIds.size(); index++) {
            if (!sourceItems.get(index).getId().equals(completedItemIds.get(index))) {
                throw invalidState("완료 일정 항목은 기준 일정의 연속된 앞부분이어야 합니다.");
            }
        }
        if (sourceItems.stream()
                .skip(completedItemIds.size())
                .anyMatch(item -> item.getStatus() == ItineraryItemStatus.COMPLETED)) {
            throw invalidState("이전 버전에서 완료된 일정은 다시 미완료로 변경할 수 없습니다.");
        }
        return List.copyOf(sourceItems.subList(0, completedItemIds.size()));
    }

    private void validateCompletedTimes(List<ItineraryItem> completedItems, LocalTime currentTime) {
        for (ItineraryItem item : completedItems) {
            if (item.getVisitDate() == null || item.getArrivalTime() == null
                    || item.getStartTime() == null || item.getEndTime() == null
                    || item.getWaitingMinutes() == null || item.getStayMinutes() == null
                    || item.getPriority() == null || item.getMustVisit() == null) {
                throw invalidState("시간 정보가 없는 이전 일정은 재최적화할 수 없습니다.");
            }
        }
        if (!completedItems.isEmpty() && currentTime.isBefore(completedItems.getLast().getEndTime())) {
            throw invalidState("현재 시각은 마지막 완료 장소의 종료시각보다 빠를 수 없습니다.");
        }
    }

    private ReoptimizationInput buildInput(
            Trip trip,
            List<TripPlace> tripPlaces,
            Set<Long> completedPlaceIds,
            ReoptimizeCommand command
    ) {
        Location startLocation = Location.of(command.currentLatitude(), command.currentLongitude());
        Location accommodation = Location.of(
                trip.getAccommodationLatitude(),
                trip.getAccommodationLongitude()
        );
        List<TripPlace> remaining = tripPlaces.stream()
                .filter(tripPlace -> !completedPlaceIds.contains(tripPlace.getPlace().getId()))
                .toList();
        List<VisitCandidate> visitCandidates = remaining.stream()
                .map(tripPlace -> new VisitCandidate(
                        tripPlace.getId(),
                        tripPlace.getPlace().getId(),
                        Location.of(
                                tripPlace.getPlace().getLatitude(),
                                tripPlace.getPlace().getLongitude()
                        )
                ))
                .toList();
        OptimizationRequest optimizationRequest = new OptimizationRequest(
                startLocation,
                visitCandidates,
                trip.getTransportMode()
        );
        Map<Long, PlaceOpeningHour> openingHours = openingHours(remaining, trip.getStartDate());
        List<ScheduleCandidate> scheduleCandidates = remaining.stream()
                .map(tripPlace -> toScheduleCandidate(trip, tripPlace, openingHours))
                .toList();
        return new ReoptimizationInput(
                trip.getId(),
                trip.getStartDate(),
                trip.getDailyStartTime(),
                command.currentTime(),
                trip.getDailyEndTime(),
                accommodation,
                optimizationRequest,
                scheduleCandidates
        );
    }

    private Map<Long, PlaceOpeningHour> openingHours(
            List<TripPlace> tripPlaces,
            LocalDate visitDate
    ) {
        if (tripPlaces.isEmpty()) {
            return Map.of();
        }
        List<Long> placeIds = tripPlaces.stream()
                .map(tripPlace -> tripPlace.getPlace().getId())
                .toList();
        return openingHourRepository
                .findAllByPlaceIdInAndDayOfWeek(placeIds, visitDate.getDayOfWeek())
                .stream()
                .collect(Collectors.toMap(
                        openingHour -> openingHour.getPlace().getId(),
                        Function.identity()
                ));
    }

    private ScheduleCandidate toScheduleCandidate(
            Trip trip,
            TripPlace tripPlace,
            Map<Long, PlaceOpeningHour> openingHours
    ) {
        Place place = tripPlace.getPlace();
        PlaceOpeningHour openingHour = openingHours.get(place.getId());
        return new ScheduleCandidate(
                tripPlace.getId(),
                place.getId(),
                place.getName(),
                Location.of(place.getLatitude(), place.getLongitude()),
                tripPlace.getPriority(),
                tripPlace.isMustVisit(),
                openingHour == null ? null : openingHour.getOpenTime(),
                openingHour == null ? null : openingHour.getCloseTime(),
                openingHour != null && openingHour.isClosed(),
                tripPlace.getPreferredStartTime(),
                tripPlace.getPreferredEndTime(),
                trip.getPace().stayMinutes(
                        place.getAverageStayMinutes(),
                        tripPlace.getMinimumStayMinutes(),
                        tripPlace.getMaximumStayMinutes()
                )
        );
    }

    private void validateExactLimit(OptimizationAlgorithm algorithm, int candidates) {
        if (algorithm == OptimizationAlgorithm.EXACT_SEARCH
                && candidates > ExactSearchOptimizationEngine.MAX_CANDIDATES) {
            throw new RoutePlanException(ErrorCode.EXACT_SEARCH_LIMIT_EXCEEDED);
        }
    }

    private List<Location> locations(ReoptimizationInput input) {
        return Stream.concat(
                        Stream.of(input.optimizationRequest().startLocation(), input.accommodation()),
                        input.optimizationRequest().candidates().stream().map(VisitCandidate::location)
                )
                .distinct()
                .toList();
    }

    private ItineraryView saveIfUnchanged(
            ReoptimizationSnapshot snapshot,
            OptimizationResult result,
            ConstraintSchedule schedule,
            RouteMatrix routeMatrix
    ) {
        Trip trip = tripRepository.findByIdForUpdate(snapshot.input().tripId())
                .orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_NOT_FOUND));
        Itinerary source = itineraryRepository.findDetailedById(snapshot.sourceItineraryId())
                .orElseThrow(() -> new RoutePlanException(ErrorCode.ITINERARY_NOT_FOUND));
        if (itineraryRepository.findMaxVersionByTripId(trip.getId()) != snapshot.sourceVersion()) {
            throw new RoutePlanException(ErrorCode.REOPTIMIZATION_SOURCE_NOT_LATEST);
        }
        List<TripPlace> currentTripPlaces = tripPlaceRepository
                .findAllByTripIdOrderByIdAsc(trip.getId());
        ReoptimizationInput currentInput = buildInput(
                trip,
                currentTripPlaces,
                snapshot.completedPlaceIds(),
                snapshot.command()
        );
        if (!snapshot.input().equals(currentInput)) {
            throw new RoutePlanException(ErrorCode.OPTIMIZATION_INPUT_CHANGED);
        }

        CompletedTotals completed = CompletedTotals.from(snapshot.completedVisits());
        int totalTravel = Math.addExact(completed.travelMinutes(), schedule.totalTravelMinutes());
        int totalWaiting = Math.addExact(completed.waitingMinutes(), schedule.totalWaitingMinutes());
        int totalPriority = Math.addExact(completed.priorityScore(), schedule.visitedPriorityScore());
        int optimizationScore = Math.max(
                0,
                totalPriority * 10_000 - totalTravel * 5 - totalWaiting * 2
        );
        Itinerary itinerary = Itinerary.create(
                trip,
                snapshot.sourceVersion() + 1,
                result.algorithm(),
                Math.addExact(completed.distanceMeters(), schedule.totalDistanceMeters()),
                totalTravel,
                optimizationScore,
                Math.addExact(completed.stayMinutes(), schedule.totalStayMinutes()),
                totalWaiting,
                schedule.returnTravelDistanceMeters(),
                schedule.returnTravelMinutes(),
                schedule.returnArrivalTime(),
                true,
                routeMatrix.dataType(),
                routeMatrix.providerCallCount(),
                routeMatrix.elementCount(),
                routeMatrix.buildMillis(),
                routeMatrix.cacheEnabled(),
                routeMatrix.cacheHitCount(),
                routeMatrix.cacheMissCount(),
                routeMatrix.cacheFailureCount()
        );
        itinerary.addDay(
                trip.getStartDate(),
                1,
                Math.addExact(completed.distanceMeters(), schedule.totalDistanceMeters()),
                totalTravel,
                Math.addExact(completed.stayMinutes(), schedule.totalStayMinutes()),
                totalWaiting,
                schedule.returnTravelDistanceMeters(),
                schedule.returnTravelMinutes(),
                schedule.returnArrivalTime(),
                true
        );
        itinerary.markReoptimized(
                source,
                snapshot.command().reason(),
                snapshot.command().reasonDetail(),
                snapshot.command().currentTime(),
                snapshot.command().currentLatitude(),
                snapshot.command().currentLongitude()
        );

        Map<Long, ItineraryItem> sourceItemsById = source.getItems().stream()
                .collect(Collectors.toMap(ItineraryItem::getId, Function.identity()));
        for (int index = 0; index < snapshot.completedVisits().size(); index++) {
            CompletedVisit visit = snapshot.completedVisits().get(index);
            ItineraryItem sourceItem = sourceItemsById.get(visit.sourceItemId());
            itinerary.addCompletedItem(
                    sourceItem.getPlace(),
                    index + 1,
                    visit.travelDistanceMeters(),
                    visit.travelMinutes(),
                    visit.visitDate(),
                    visit.arrivalTime(),
                    visit.startTime(),
                    visit.endTime(),
                    visit.waitingMinutes(),
                    visit.stayMinutes(),
                    visit.priority(),
                    visit.mustVisit()
            );
        }

        Map<Long, Place> currentPlacesById = currentTripPlaces.stream()
                .map(TripPlace::getPlace)
                .collect(Collectors.toMap(Place::getId, Function.identity()));
        int completedCount = snapshot.completedVisits().size();
        schedule.visits().forEach(visit -> itinerary.addItem(
                currentPlacesById.get(visit.placeId()),
                completedCount + visit.sequence(),
                visit.travelDistanceMeters(),
                visit.travelMinutes(),
                visit.visitDate(),
                visit.arrivalTime(),
                visit.startTime(),
                visit.endTime(),
                visit.waitingMinutes(),
                visit.stayMinutes(),
                visit.priority(),
                visit.mustVisit()
        ));
        schedule.exclusions().forEach(exclusion -> itinerary.addExclusion(
                currentPlacesById.get(exclusion.placeId()),
                exclusion.priority(),
                exclusion.reason()
        ));
        trip.markOptimized();
        return ItineraryView.from(itineraryRepository.saveAndFlush(itinerary));
    }

    private RoutePlanException invalidState(String message) {
        return new RoutePlanException(ErrorCode.INVALID_REOPTIMIZATION_STATE, message);
    }

    public record ReoptimizeCommand(
            Long sourceItineraryId,
            LocalTime currentTime,
            BigDecimal currentLatitude,
            BigDecimal currentLongitude,
            List<Long> completedItemIds,
            ItineraryChangeReason reason,
            String reasonDetail
    ) {

        public ReoptimizeCommand {
            completedItemIds = completedItemIds == null
                    ? null : List.copyOf(completedItemIds);
        }
    }

    private record ReoptimizationInput(
            Long tripId,
            LocalDate visitDate,
            LocalTime tripDailyStartTime,
            LocalTime optimizationStartTime,
            LocalTime dailyEndTime,
            Location accommodation,
            OptimizationRequest optimizationRequest,
            List<ScheduleCandidate> scheduleCandidates
    ) {

        private ReoptimizationInput {
            scheduleCandidates = List.copyOf(scheduleCandidates);
        }

        private ScheduleRequest toScheduleRequest(OptimizationResult result) {
            return new ScheduleRequest(
                    visitDate,
                    optimizationStartTime,
                    dailyEndTime,
                    optimizationRequest.startLocation(),
                    accommodation,
                    optimizationRequest.transportMode(),
                    result.algorithm(),
                    scheduleCandidates,
                    result.stops().stream().map(stop -> stop.tripPlaceId()).toList()
            );
        }
    }

    private record ReoptimizationSnapshot(
            Long sourceItineraryId,
            int sourceVersion,
            ReoptimizeCommand command,
            Set<Long> completedPlaceIds,
            List<CompletedVisit> completedVisits,
            ReoptimizationInput input
    ) {

        private ReoptimizationSnapshot {
            completedPlaceIds = Set.copyOf(completedPlaceIds);
            completedVisits = List.copyOf(completedVisits);
        }
    }

    private record CompletedVisit(
            Long sourceItemId,
            Long placeId,
            LocalDate visitDate,
            LocalTime arrivalTime,
            LocalTime startTime,
            LocalTime endTime,
            long travelDistanceMeters,
            int travelMinutes,
            int waitingMinutes,
            int stayMinutes,
            int priority,
            boolean mustVisit
    ) {

        private static CompletedVisit from(ItineraryItem item) {
            return new CompletedVisit(
                    item.getId(),
                    item.getPlace().getId(),
                    item.getVisitDate(),
                    item.getArrivalTime(),
                    item.getStartTime(),
                    item.getEndTime(),
                    item.getTravelDistanceMeters(),
                    item.getEstimatedTravelMinutes(),
                    item.getWaitingMinutes(),
                    item.getStayMinutes(),
                    item.getPriority(),
                    item.getMustVisit()
            );
        }
    }

    private record CompletedTotals(
            long distanceMeters,
            int travelMinutes,
            int waitingMinutes,
            int stayMinutes,
            int priorityScore
    ) {

        private static CompletedTotals from(List<CompletedVisit> visits) {
            long distance = 0;
            int travel = 0;
            int waiting = 0;
            int stay = 0;
            int priority = 0;
            for (CompletedVisit visit : visits) {
                distance = Math.addExact(distance, visit.travelDistanceMeters());
                travel = Math.addExact(travel, visit.travelMinutes());
                waiting = Math.addExact(waiting, visit.waitingMinutes());
                stay = Math.addExact(stay, visit.stayMinutes());
                priority = Math.addExact(priority, visit.priority());
            }
            return new CompletedTotals(distance, travel, waiting, stay, priority);
        }
    }
}
