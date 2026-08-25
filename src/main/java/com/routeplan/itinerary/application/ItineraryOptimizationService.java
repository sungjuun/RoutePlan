package com.routeplan.itinerary.application;

import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import com.routeplan.itinerary.domain.Itinerary;
import com.routeplan.itinerary.persistence.ItineraryRepository;
import com.routeplan.optimization.algorithm.OptimizationEngine;
import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.OptimizationRequest;
import com.routeplan.optimization.domain.OptimizationResult;
import com.routeplan.optimization.domain.VisitCandidate;
import com.routeplan.place.domain.Place;
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
    private final OptimizationEngine optimizationEngine;

    public ItineraryOptimizationService(
            TripRepository tripRepository,
            TripPlaceRepository tripPlaceRepository,
            ItineraryRepository itineraryRepository,
            OptimizationEngine optimizationEngine
    ) {
        this.tripRepository = tripRepository;
        this.tripPlaceRepository = tripPlaceRepository;
        this.itineraryRepository = itineraryRepository;
        this.optimizationEngine = optimizationEngine;
    }

    @Transactional
    public ItineraryView optimize(Long tripId) {
        Trip trip = tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_NOT_FOUND));
        List<TripPlace> tripPlaces = tripPlaceRepository.findAllByTripIdOrderByIdAsc(tripId);
        if (tripPlaces.isEmpty()) {
            throw new RoutePlanException(ErrorCode.TRIP_HAS_NO_PLACES);
        }

        OptimizationRequest request = toRequest(trip, tripPlaces);
        OptimizationResult result = optimizationEngine.optimize(request);
        Itinerary itinerary = Itinerary.create(
                trip,
                itineraryRepository.findMaxVersionByTripId(tripId) + 1,
                result.algorithm(),
                result.totalDistanceMeters(),
                result.estimatedTravelMinutes()
        );

        Map<Long, Place> placesById = tripPlaces.stream()
                .map(TripPlace::getPlace)
                .collect(Collectors.toMap(Place::getId, Function.identity()));
        result.stops().forEach(stop -> itinerary.addItem(
                placesById.get(stop.placeId()),
                stop.sequence(),
                stop.travelDistanceMeters(),
                stop.estimatedTravelMinutes()
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
}
