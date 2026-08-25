package com.routeplan.itinerary.application;

import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import com.routeplan.itinerary.domain.Itinerary;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ItineraryOptimizationService {

    private final TripRepository tripRepository;
    private final TripPlaceRepository tripPlaceRepository;
    private final ItineraryRepository itineraryRepository;
    private final OptimizationEngineRegistry optimizationEngineRegistry;
    private final PlaceOpeningHourRepository openingHourRepository;
    private final ConstraintSchedulePlanner schedulePlanner;
    private final RouteMatrixProvider routeMatrixProvider;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;

    public ItineraryOptimizationService(
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

    public ItineraryView optimize(Long tripId, OptimizationAlgorithm algorithm) {
        OptimizationSnapshot snapshot = Objects.requireNonNull(
                readTransaction.execute(status -> loadSnapshot(tripId, algorithm))
        );
        RouteMatrix routeMatrix = routeMatrixProvider.build(
                locations(snapshot.optimizationRequest()),
                snapshot.optimizationRequest().transportMode()
        );
        OptimizationResult result = optimizationEngineRegistry.get(algorithm)
                .optimize(snapshot.optimizationRequest(), routeMatrix);
        ConstraintSchedule schedule = schedulePlanner.plan(
                snapshot.toScheduleRequest(result),
                routeMatrix
        );
        return Objects.requireNonNull(writeTransaction.execute(status ->
                saveIfUnchanged(snapshot, result, schedule, routeMatrix)
        ));
    }

    private OptimizationSnapshot loadSnapshot(Long tripId, OptimizationAlgorithm algorithm) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_NOT_FOUND));
        List<TripPlace> tripPlaces = tripPlaceRepository.findAllByTripIdOrderByIdAsc(tripId);
        validateCandidates(algorithm, tripPlaces);
        return buildSnapshot(trip, tripPlaces);
    }

    private ItineraryView saveIfUnchanged(
            OptimizationSnapshot snapshot,
            OptimizationResult result,
            ConstraintSchedule schedule,
            RouteMatrix routeMatrix
    ) {
        Trip trip = tripRepository.findByIdForUpdate(snapshot.tripId())
                .orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_NOT_FOUND));
        List<TripPlace> currentTripPlaces = tripPlaceRepository
                .findAllByTripIdOrderByIdAsc(snapshot.tripId());
        if (currentTripPlaces.isEmpty()) {
            throw new RoutePlanException(ErrorCode.OPTIMIZATION_INPUT_CHANGED);
        }
        OptimizationSnapshot current = buildSnapshot(trip, currentTripPlaces);
        if (!snapshot.hasSameInput(current)) {
            throw new RoutePlanException(ErrorCode.OPTIMIZATION_INPUT_CHANGED);
        }

        Itinerary itinerary = Itinerary.create(
                trip,
                itineraryRepository.findMaxVersionByTripId(snapshot.tripId()) + 1,
                result.algorithm(),
                schedule.totalDistanceMeters(),
                schedule.totalTravelMinutes(),
                schedule.optimizationScore(),
                schedule.totalStayMinutes(),
                schedule.totalWaitingMinutes(),
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

        Map<Long, Place> placesById = currentTripPlaces.stream()
                .map(TripPlace::getPlace)
                .collect(Collectors.toMap(Place::getId, Function.identity()));
        schedule.visits().forEach(visit -> itinerary.addItem(
                placesById.get(visit.placeId()),
                visit.sequence(),
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
                placesById.get(exclusion.placeId()),
                exclusion.priority(),
                exclusion.reason()
        ));

        trip.markOptimized();
        return ItineraryView.from(itineraryRepository.saveAndFlush(itinerary));
    }

    private void validateCandidates(OptimizationAlgorithm algorithm, List<TripPlace> tripPlaces) {
        if (tripPlaces.isEmpty()) {
            throw new RoutePlanException(ErrorCode.TRIP_HAS_NO_PLACES);
        }
        if (algorithm == OptimizationAlgorithm.EXACT_SEARCH
                && tripPlaces.size() > ExactSearchOptimizationEngine.MAX_CANDIDATES) {
            throw new RoutePlanException(ErrorCode.EXACT_SEARCH_LIMIT_EXCEEDED);
        }
    }

    private OptimizationSnapshot buildSnapshot(Trip trip, List<TripPlace> tripPlaces) {
        Location accommodation = Location.of(
                trip.getAccommodationLatitude(),
                trip.getAccommodationLongitude()
        );
        List<VisitCandidate> visitCandidates = tripPlaces.stream()
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
                accommodation,
                visitCandidates,
                trip.getTransportMode()
        );

        List<Long> placeIds = tripPlaces.stream()
                .map(tripPlace -> tripPlace.getPlace().getId())
                .toList();
        Map<Long, PlaceOpeningHour> openingHours = openingHourRepository
                .findAllByPlaceIdInAndDayOfWeek(placeIds, trip.getStartDate().getDayOfWeek())
                .stream()
                .collect(Collectors.toMap(
                        openingHour -> openingHour.getPlace().getId(),
                        Function.identity()
                ));
        List<ScheduleCandidate> scheduleCandidates = tripPlaces.stream()
                .map(tripPlace -> toScheduleCandidate(trip, tripPlace, openingHours))
                .toList();
        return new OptimizationSnapshot(
                trip.getId(),
                trip.getStartDate(),
                trip.getDailyStartTime(),
                trip.getDailyEndTime(),
                optimizationRequest,
                scheduleCandidates
        );
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

    private List<Location> locations(OptimizationRequest request) {
        return Stream.concat(
                        Stream.of(request.startLocation()),
                        request.candidates().stream().map(VisitCandidate::location)
                )
                .distinct()
                .toList();
    }

    private record OptimizationSnapshot(
            Long tripId,
            LocalDate visitDate,
            LocalTime dailyStartTime,
            LocalTime dailyEndTime,
            OptimizationRequest optimizationRequest,
            List<ScheduleCandidate> scheduleCandidates
    ) {

        private OptimizationSnapshot {
            scheduleCandidates = List.copyOf(scheduleCandidates);
        }

        private ScheduleRequest toScheduleRequest(OptimizationResult result) {
            return new ScheduleRequest(
                    visitDate,
                    dailyStartTime,
                    dailyEndTime,
                    optimizationRequest.startLocation(),
                    optimizationRequest.transportMode(),
                    result.algorithm(),
                    scheduleCandidates,
                    result.stops().stream().map(stop -> stop.tripPlaceId()).toList()
            );
        }

        private boolean hasSameInput(OptimizationSnapshot other) {
            return tripId.equals(other.tripId)
                    && visitDate.equals(other.visitDate)
                    && dailyStartTime.equals(other.dailyStartTime)
                    && dailyEndTime.equals(other.dailyEndTime)
                    && optimizationRequest.equals(other.optimizationRequest)
                    && scheduleCandidates.equals(other.scheduleCandidates);
        }
    }
}
