package com.routeplan.itinerary.application;

import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import com.routeplan.itinerary.domain.Itinerary;
import com.routeplan.itinerary.persistence.ItineraryRepository;
import com.routeplan.optimization.algorithm.ExactSearchOptimizationEngine;
import com.routeplan.optimization.algorithm.OptimizationEngineRegistry;
import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.OptimizationAlgorithm;
import com.routeplan.optimization.domain.OptimizationRequest;
import com.routeplan.optimization.domain.OptimizationResult;
import com.routeplan.optimization.domain.VisitCandidate;
import com.routeplan.optimization.constraint.ConstraintSchedule;
import com.routeplan.optimization.constraint.ConstraintSchedulePlanner;
import com.routeplan.optimization.constraint.ScheduleCandidate;
import com.routeplan.optimization.constraint.ScheduleRequest;
import com.routeplan.optimization.route.RouteMatrix;
import com.routeplan.optimization.route.RouteMatrixProvider;
import com.routeplan.place.domain.Place;
import com.routeplan.place.domain.PlaceOpeningHour;
import com.routeplan.place.persistence.PlaceOpeningHourRepository;
import com.routeplan.trip.domain.Trip;
import com.routeplan.trip.domain.TripPlace;
import com.routeplan.trip.persistence.TripPlaceRepository;
import com.routeplan.trip.persistence.TripRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItineraryOptimizationService {

    private final TripRepository tripRepository;
    private final TripPlaceRepository tripPlaceRepository;
    private final ItineraryRepository itineraryRepository;
    private final OptimizationEngineRegistry optimizationEngineRegistry;
    private final PlaceOpeningHourRepository openingHourRepository;
    private final ConstraintSchedulePlanner schedulePlanner;
    private final RouteMatrixProvider routeMatrixProvider;

    public ItineraryOptimizationService(
            TripRepository tripRepository,
            TripPlaceRepository tripPlaceRepository,
            ItineraryRepository itineraryRepository,
            OptimizationEngineRegistry optimizationEngineRegistry,
            PlaceOpeningHourRepository openingHourRepository,
            ConstraintSchedulePlanner schedulePlanner,
            RouteMatrixProvider routeMatrixProvider
    ) {
        this.tripRepository = tripRepository;
        this.tripPlaceRepository = tripPlaceRepository;
        this.itineraryRepository = itineraryRepository;
        this.optimizationEngineRegistry = optimizationEngineRegistry;
        this.openingHourRepository = openingHourRepository;
        this.schedulePlanner = schedulePlanner;
        this.routeMatrixProvider = routeMatrixProvider;
    }

    @Transactional
    public ItineraryView optimize(Long tripId, OptimizationAlgorithm algorithm) {
        Trip trip = tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_NOT_FOUND));
        List<TripPlace> tripPlaces = tripPlaceRepository.findAllByTripIdOrderByIdAsc(tripId);
        if (tripPlaces.isEmpty()) {
            throw new RoutePlanException(ErrorCode.TRIP_HAS_NO_PLACES);
        }
        if (algorithm == OptimizationAlgorithm.EXACT_SEARCH
                && tripPlaces.size() > ExactSearchOptimizationEngine.MAX_CANDIDATES) {
            throw new RoutePlanException(ErrorCode.EXACT_SEARCH_LIMIT_EXCEEDED);
        }

        OptimizationRequest request = toRequest(trip, tripPlaces);
        RouteMatrix routeMatrix = routeMatrixProvider.build(
                locations(request),
                request.transportMode()
        );
        OptimizationResult result = optimizationEngineRegistry.get(algorithm)
                .optimize(request, routeMatrix);
        ConstraintSchedule schedule = schedulePlanner.plan(
                toScheduleRequest(trip, tripPlaces, result),
                routeMatrix
        );
        Itinerary itinerary = Itinerary.create(
                trip,
                itineraryRepository.findMaxVersionByTripId(tripId) + 1,
                result.algorithm(),
                schedule.totalDistanceMeters(),
                schedule.totalTravelMinutes(),
                schedule.optimizationScore(),
                schedule.totalStayMinutes(),
                schedule.totalWaitingMinutes(),
                schedule.returnTravelDistanceMeters(),
                schedule.returnTravelMinutes(),
                schedule.returnArrivalTime(),
                true
        );

        Map<Long, Place> placesById = tripPlaces.stream()
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
        Itinerary saved = itineraryRepository.saveAndFlush(itinerary);
        return ItineraryView.from(saved);
    }

    private OptimizationRequest toRequest(Trip trip, List<TripPlace> tripPlaces) {
        Location start = Location.of(
                trip.getAccommodationLatitude(),
                trip.getAccommodationLongitude()
        );
        List<VisitCandidate> candidates = tripPlaces.stream()
                .map(tripPlace -> new VisitCandidate(
                        tripPlace.getId(),
                        tripPlace.getPlace().getId(),
                        Location.of(
                                tripPlace.getPlace().getLatitude(),
                                tripPlace.getPlace().getLongitude()
                        )
                ))
                .toList();
        return new OptimizationRequest(start, candidates, trip.getTransportMode());
    }

    private List<Location> locations(OptimizationRequest request) {
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(request.startLocation()),
                        request.candidates().stream().map(VisitCandidate::location)
                )
                .distinct()
                .toList();
    }

    private ScheduleRequest toScheduleRequest(
            Trip trip,
            List<TripPlace> tripPlaces,
            OptimizationResult result
    ) {
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
        List<ScheduleCandidate> candidates = tripPlaces.stream()
                .map(tripPlace -> {
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
                })
                .toList();
        return new ScheduleRequest(
                trip.getStartDate(),
                trip.getDailyStartTime(),
                trip.getDailyEndTime(),
                Location.of(
                        trip.getAccommodationLatitude(),
                        trip.getAccommodationLongitude()
                ),
                trip.getTransportMode(),
                result.algorithm(),
                candidates,
                result.stops().stream().map(stop -> stop.tripPlaceId()).toList()
        );
    }
}
