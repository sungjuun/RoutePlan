package com.routeplan.itinerary.application;

import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import com.routeplan.common.observability.RoutePlanMetrics;
import com.routeplan.integration.google.ExternalProviderException;
import com.routeplan.itinerary.domain.Itinerary;
import com.routeplan.itinerary.domain.ItineraryChangeReason;
import com.routeplan.itinerary.domain.ItineraryDay;
import com.routeplan.itinerary.domain.ItineraryItem;
import com.routeplan.itinerary.domain.ItineraryItemStatus;
import com.routeplan.itinerary.persistence.ItineraryRepository;
import com.routeplan.optimization.algorithm.ExactSearchOptimizationEngine;
import com.routeplan.optimization.algorithm.OptimizationEngineRegistry;
import com.routeplan.optimization.constraint.DailySchedule;
import com.routeplan.optimization.constraint.MultiDaySchedule;
import com.routeplan.optimization.constraint.MultiDaySchedulePlanner;
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
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
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
    private final MultiDaySchedulePlanner schedulePlanner;
    private final RouteMatrixProvider routeMatrixProvider;
    private final RoutePlanMetrics metrics;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;

    public ItineraryReoptimizationService(
            TripRepository tripRepository,
            TripPlaceRepository tripPlaceRepository,
            ItineraryRepository itineraryRepository,
            OptimizationEngineRegistry optimizationEngineRegistry,
            PlaceOpeningHourRepository openingHourRepository,
            MultiDaySchedulePlanner schedulePlanner,
            RouteMatrixProvider routeMatrixProvider,
            RoutePlanMetrics metrics,
            PlatformTransactionManager transactionManager
    ) {
        this.tripRepository = tripRepository;
        this.tripPlaceRepository = tripPlaceRepository;
        this.itineraryRepository = itineraryRepository;
        this.optimizationEngineRegistry = optimizationEngineRegistry;
        this.openingHourRepository = openingHourRepository;
        this.schedulePlanner = schedulePlanner;
        this.routeMatrixProvider = routeMatrixProvider;
        this.metrics = metrics;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    public ItineraryView reoptimize(
            Long tripId,
            OptimizationAlgorithm algorithm,
            ReoptimizeCommand command
    ) {
        var sample = metrics.startGeneration();
        try {
            ReoptimizationSnapshot snapshot = Objects.requireNonNull(
                    readTransaction.execute(status -> loadSnapshot(tripId, algorithm, command))
            );
            RouteMatrix routeMatrix = routeMatrixProvider.build(
                    locations(snapshot.input()),
                    snapshot.input().optimizationRequest().transportMode()
            );
            metrics.recordRouteMatrix(routeMatrix);
            OptimizationResult result = optimizationEngineRegistry.get(algorithm)
                    .optimize(snapshot.input().optimizationRequest(), routeMatrix);
            MultiDaySchedule schedule = schedulePlanner.plan(
                    snapshot.input().toScheduleRequests(result),
                    routeMatrix
            );
            ItineraryView itinerary = Objects.requireNonNull(writeTransaction.execute(status ->
                    saveIfUnchanged(snapshot, result, schedule, routeMatrix)
            ));
            metrics.recordGeneration(
                    sample,
                    RoutePlanMetrics.GenerationType.REOPTIMIZATION,
                    algorithm,
                    RoutePlanMetrics.Outcome.SUCCESS
            );
            return itinerary;
        } catch (ExternalProviderException exception) {
            metrics.recordRouteApiFailure(exception.failure());
            recordFailure(sample, algorithm);
            throw exception;
        } catch (RuntimeException exception) {
            recordFailure(sample, algorithm);
            throw exception;
        }
    }

    private void recordFailure(
            Timer.Sample sample,
            OptimizationAlgorithm algorithm
    ) {
        metrics.recordGeneration(
                sample,
                RoutePlanMetrics.GenerationType.REOPTIMIZATION,
                algorithm,
                RoutePlanMetrics.Outcome.FAILURE
        );
    }

    private ReoptimizationSnapshot loadSnapshot(
            Long tripId,
            OptimizationAlgorithm algorithm,
            ReoptimizeCommand command
    ) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_NOT_FOUND));
        ReoptimizeCommand normalizedCommand = command.withCurrentDate(
                command.currentDate() == null ? trip.getStartDate() : command.currentDate()
        );
        Itinerary source = itineraryRepository.findDetailedById(normalizedCommand.sourceItineraryId())
                .orElseThrow(() -> new RoutePlanException(ErrorCode.ITINERARY_NOT_FOUND));
        validateSource(tripId, source);
        validateCurrentDate(trip, normalizedCommand.currentDate());
        validateCurrentTime(trip, normalizedCommand.currentTime());
        List<ItineraryItem> completedItems = completedPrefix(
                source, normalizedCommand.completedItemIds()
        );
        validateCompletedDates(source, completedItems, normalizedCommand.currentDate());
        validateCompletedTimes(
                completedItems, normalizedCommand.currentDate(), normalizedCommand.currentTime()
        );
        Set<Long> completedPlaceIds = completedItems.stream()
                .map(item -> item.getPlace().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<TripPlace> tripPlaces = tripPlaceRepository.findAllByTripIdOrderByIdAsc(tripId);
        ReoptimizationInput input = buildInput(
                trip, tripPlaces, completedPlaceIds, normalizedCommand
        );
        validateExactLimit(algorithm, input.optimizationRequest().candidates().size());
        return new ReoptimizationSnapshot(
                source.getId(),
                source.getVersion(),
                normalizedCommand,
                completedPlaceIds,
                completedItems.stream().map(CompletedVisit::from).toList(),
                source.getDays().stream()
                        .filter(day -> day.getVisitDate().isBefore(normalizedCommand.currentDate()))
                        .map(FixedDay::from)
                        .toList(),
                input
        );
    }

    private void validateCurrentDate(Trip trip, LocalDate currentDate) {
        if (currentDate == null
                || currentDate.isBefore(trip.getStartDate())
                || currentDate.isAfter(trip.getEndDate())) {
            throw invalidState("현재 날짜는 여행 기간 안에 있어야 합니다.");
        }
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

    private void validateCompletedDates(
            Itinerary source,
            List<ItineraryItem> completedItems,
            LocalDate currentDate
    ) {
        if (source.getItems().stream().anyMatch(item -> item.getVisitDate() == null)) {
            throw invalidState("방문일 정보가 없는 이전 일정은 재최적화할 수 없습니다.");
        }
        Set<Long> completedIds = completedItems.stream()
                .map(ItineraryItem::getId)
                .collect(Collectors.toSet());
        if (source.getItems().stream()
                .filter(item -> item.getVisitDate().isBefore(currentDate))
                .anyMatch(item -> !completedIds.contains(item.getId()))) {
            throw invalidState("지난 날짜의 일정 항목은 모두 완료 상태로 고정해야 합니다.");
        }
        if (completedItems.stream().anyMatch(item -> item.getVisitDate().isAfter(currentDate))) {
            throw invalidState("현재 날짜 이후의 일정은 완료 처리할 수 없습니다.");
        }
        long expectedPastDays = source.getTrip().getStartDate().datesUntil(currentDate).count();
        long storedPastDays = source.getDays().stream()
                .filter(day -> day.getVisitDate().isBefore(currentDate))
                .count();
        if (storedPastDays != expectedPastDays) {
            throw invalidState("일자별 합계가 없는 이전 일정은 다일 재최적화할 수 없습니다.");
        }
    }

    private void validateCompletedTimes(
            List<ItineraryItem> completedItems,
            LocalDate currentDate,
            LocalTime currentTime
    ) {
        for (ItineraryItem item : completedItems) {
            if (item.getVisitDate() == null || item.getArrivalTime() == null
                    || item.getStartTime() == null || item.getEndTime() == null
                    || item.getWaitingMinutes() == null || item.getStayMinutes() == null
                    || item.getPriority() == null || item.getMustVisit() == null) {
                throw invalidState("시간 정보가 없는 이전 일정은 재최적화할 수 없습니다.");
            }
        }
        ItineraryItem lastCurrentDay = completedItems.stream()
                .filter(item -> item.getVisitDate().equals(currentDate))
                .reduce((left, right) -> right)
                .orElse(null);
        if (lastCurrentDay != null && currentTime.isBefore(lastCurrentDay.getEndTime())) {
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
        Map<OpeningHourKey, PlaceOpeningHour> openingHours = openingHours(remaining);
        List<DailyCandidates> dailyCandidates = new ArrayList<>();
        for (LocalDate date = command.currentDate(); !date.isAfter(trip.getEndDate()); date = date.plusDays(1)) {
            LocalDate visitDate = date;
            dailyCandidates.add(new DailyCandidates(
                    visitDate,
                    remaining.stream()
                            .map(tripPlace -> toScheduleCandidate(
                                    trip,
                                    tripPlace,
                                    openingHours.get(new OpeningHourKey(
                                            tripPlace.getPlace().getId(), visitDate.getDayOfWeek()
                                    ))
                            ))
                            .toList()
            ));
        }
        return new ReoptimizationInput(
                trip.getId(),
                command.currentDate(),
                trip.getDailyStartTime(),
                command.currentTime(),
                trip.getDailyEndTime(),
                accommodation,
                optimizationRequest,
                dailyCandidates
        );
    }

    private Map<OpeningHourKey, PlaceOpeningHour> openingHours(List<TripPlace> tripPlaces) {
        if (tripPlaces.isEmpty()) {
            return Map.of();
        }
        List<Long> placeIds = tripPlaces.stream()
                .map(tripPlace -> tripPlace.getPlace().getId())
                .toList();
        return openingHourRepository
                .findAllByPlaceIdIn(placeIds)
                .stream()
                .collect(Collectors.toMap(
                        openingHour -> new OpeningHourKey(
                                openingHour.getPlace().getId(), openingHour.getDayOfWeek()
                        ),
                        Function.identity()
                ));
    }

    private ScheduleCandidate toScheduleCandidate(
            Trip trip,
            TripPlace tripPlace,
            PlaceOpeningHour openingHour
    ) {
        Place place = tripPlace.getPlace();
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
            MultiDaySchedule schedule,
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
        CompletedTotals completedToday = CompletedTotals.from(snapshot.completedVisits().stream()
                .filter(visit -> visit.visitDate().equals(snapshot.command().currentDate()))
                .toList());
        FixedDayTotals fixedPast = FixedDayTotals.from(snapshot.fixedDays());
        long totalDistance = Math.addExact(
                Math.addExact(fixedPast.distanceMeters(), completedToday.distanceMeters()),
                schedule.totalDistanceMeters()
        );
        int totalTravel = Math.addExact(
                Math.addExact(fixedPast.travelMinutes(), completedToday.travelMinutes()),
                schedule.totalTravelMinutes()
        );
        int totalStay = Math.addExact(
                Math.addExact(fixedPast.stayMinutes(), completedToday.stayMinutes()),
                schedule.totalStayMinutes()
        );
        int totalWaiting = Math.addExact(
                Math.addExact(fixedPast.waitingMinutes(), completedToday.waitingMinutes()),
                schedule.totalWaitingMinutes()
        );
        int totalPriority = Math.addExact(completed.priorityScore(), schedule.visitedPriorityScore());
        int optimizationScore = Math.max(
                0,
                totalPriority * 10_000 - totalTravel * 5 - totalWaiting * 2
        );
        long totalReturnDistance = Math.addExact(
                fixedPast.returnDistanceMeters(), schedule.returnTravelDistanceMeters()
        );
        int totalReturnTravel = Math.addExact(
                fixedPast.returnTravelMinutes(), schedule.returnTravelMinutes()
        );
        Itinerary itinerary = Itinerary.create(
                trip,
                snapshot.sourceVersion() + 1,
                result.algorithm(),
                totalDistance,
                totalTravel,
                optimizationScore,
                totalStay,
                totalWaiting,
                totalReturnDistance,
                totalReturnTravel,
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
        snapshot.fixedDays().forEach(day -> itinerary.addDay(
                day.visitDate(),
                day.dayNumber(),
                day.totalDistanceMeters(),
                day.travelMinutes(),
                day.stayMinutes(),
                day.waitingMinutes(),
                day.returnDistanceMeters(),
                day.returnTravelMinutes(),
                day.returnArrivalTime(),
                true
        ));
        DailySchedule currentDay = schedule.days().getFirst();
        itinerary.addDay(
                currentDay.visitDate(),
                Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(
                        trip.getStartDate(), currentDay.visitDate()
                ) + 1),
                Math.addExact(completedToday.distanceMeters(), currentDay.totalDistanceMeters()),
                Math.addExact(completedToday.travelMinutes(), currentDay.totalTravelMinutes()),
                Math.addExact(completedToday.stayMinutes(), currentDay.totalStayMinutes()),
                Math.addExact(completedToday.waitingMinutes(), currentDay.totalWaitingMinutes()),
                currentDay.returnTravelDistanceMeters(),
                currentDay.returnTravelMinutes(),
                currentDay.returnArrivalTime(),
                true
        );
        schedule.days().stream().skip(1).forEach(day -> itinerary.addDay(
                day.visitDate(),
                Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(
                        trip.getStartDate(), day.visitDate()
                ) + 1),
                day.totalDistanceMeters(),
                day.totalTravelMinutes(),
                day.totalStayMinutes(),
                day.totalWaitingMinutes(),
                day.returnTravelDistanceMeters(),
                day.returnTravelMinutes(),
                day.returnArrivalTime(),
                true
        ));
        itinerary.markReoptimized(
                source,
                snapshot.command().reason(),
                snapshot.command().reasonDetail(),
                snapshot.command().currentDate(),
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
            LocalDate currentDate,
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

        private ReoptimizeCommand withCurrentDate(LocalDate value) {
            return new ReoptimizeCommand(
                    sourceItineraryId,
                    value,
                    currentTime,
                    currentLatitude,
                    currentLongitude,
                    completedItemIds,
                    reason,
                    reasonDetail
            );
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
            List<DailyCandidates> dailyCandidates
    ) {

        private ReoptimizationInput {
            dailyCandidates = List.copyOf(dailyCandidates);
        }

        private List<ScheduleRequest> toScheduleRequests(OptimizationResult result) {
            List<Long> proposedOrder = result.stops().stream()
                    .map(stop -> stop.tripPlaceId())
                    .toList();
            return dailyCandidates.stream()
                    .map(day -> new ScheduleRequest(
                            day.visitDate(),
                            day.visitDate().equals(visitDate)
                                    ? optimizationStartTime : tripDailyStartTime,
                            dailyEndTime,
                            day.visitDate().equals(visitDate)
                                    ? optimizationRequest.startLocation() : accommodation,
                            accommodation,
                            optimizationRequest.transportMode(),
                            result.algorithm(),
                            day.candidates(),
                            proposedOrder
                    ))
                    .toList();
        }
    }

    private record ReoptimizationSnapshot(
            Long sourceItineraryId,
            int sourceVersion,
            ReoptimizeCommand command,
            Set<Long> completedPlaceIds,
            List<CompletedVisit> completedVisits,
            List<FixedDay> fixedDays,
            ReoptimizationInput input
    ) {

        private ReoptimizationSnapshot {
            completedPlaceIds = Set.copyOf(completedPlaceIds);
            completedVisits = List.copyOf(completedVisits);
            fixedDays = List.copyOf(fixedDays);
        }
    }

    private record DailyCandidates(LocalDate visitDate, List<ScheduleCandidate> candidates) {

        private DailyCandidates {
            candidates = List.copyOf(candidates);
        }
    }

    private record OpeningHourKey(Long placeId, DayOfWeek dayOfWeek) {
    }

    private record FixedDay(
            LocalDate visitDate,
            int dayNumber,
            long totalDistanceMeters,
            int travelMinutes,
            int stayMinutes,
            int waitingMinutes,
            long returnDistanceMeters,
            int returnTravelMinutes,
            LocalTime returnArrivalTime
    ) {

        private static FixedDay from(ItineraryDay day) {
            return new FixedDay(
                    day.getVisitDate(),
                    day.getDayNumber(),
                    day.getTotalDistanceMeters(),
                    day.getEstimatedTravelMinutes(),
                    day.getTotalStayMinutes(),
                    day.getTotalWaitingMinutes(),
                    day.getReturnTravelDistanceMeters(),
                    day.getReturnTravelMinutes(),
                    day.getReturnArrivalTime()
            );
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

    private record FixedDayTotals(
            long distanceMeters,
            int travelMinutes,
            int waitingMinutes,
            int stayMinutes,
            long returnDistanceMeters,
            int returnTravelMinutes
    ) {

        private static FixedDayTotals from(List<FixedDay> days) {
            long distance = 0;
            int travel = 0;
            int waiting = 0;
            int stay = 0;
            long returnDistance = 0;
            int returnTravel = 0;
            for (FixedDay day : days) {
                distance = Math.addExact(distance, day.totalDistanceMeters());
                travel = Math.addExact(travel, day.travelMinutes());
                waiting = Math.addExact(waiting, day.waitingMinutes());
                stay = Math.addExact(stay, day.stayMinutes());
                returnDistance = Math.addExact(returnDistance, day.returnDistanceMeters());
                returnTravel = Math.addExact(returnTravel, day.returnTravelMinutes());
            }
            return new FixedDayTotals(
                    distance, travel, waiting, stay, returnDistance, returnTravel
            );
        }
    }
}
