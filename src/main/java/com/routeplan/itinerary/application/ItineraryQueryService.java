package com.routeplan.itinerary.application;

import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import com.routeplan.itinerary.domain.Itinerary;
import com.routeplan.itinerary.persistence.ItineraryRepository;
import com.routeplan.trip.persistence.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItineraryQueryService {

    private final TripRepository tripRepository;
    private final ItineraryRepository itineraryRepository;

    public ItineraryQueryService(
            TripRepository tripRepository,
            ItineraryRepository itineraryRepository
    ) {
        this.tripRepository = tripRepository;
        this.itineraryRepository = itineraryRepository;
    }

    @Transactional(readOnly = true)
    public ItineraryView get(Long itineraryId) {
        Itinerary itinerary = itineraryRepository.findDetailedById(itineraryId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.ITINERARY_NOT_FOUND));
        return ItineraryView.from(itinerary);
    }

    @Transactional(readOnly = true)
    public ItineraryView getLatest(Long tripId) {
        if (!tripRepository.existsById(tripId)) {
            throw new RoutePlanException(ErrorCode.TRIP_NOT_FOUND);
        }
        Itinerary itinerary = itineraryRepository.findLatestDetailedByTripId(tripId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.ITINERARY_NOT_FOUND));
        return ItineraryView.from(itinerary);
    }
}
