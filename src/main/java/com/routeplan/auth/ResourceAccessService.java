package com.routeplan.auth;

import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import com.routeplan.itinerary.persistence.ItineraryRepository;
import com.routeplan.trip.persistence.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceAccessService {

    private final TripRepository tripRepository;
    private final ItineraryRepository itineraryRepository;

    public ResourceAccessService(
            TripRepository tripRepository,
            ItineraryRepository itineraryRepository
    ) {
        this.tripRepository = tripRepository;
        this.itineraryRepository = itineraryRepository;
    }

    @Transactional(readOnly = true)
    public void requireTripOwner(Long tripId, Long userId) {
        Long ownerId = tripRepository.findOwnerIdById(tripId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_NOT_FOUND));
        requireOwner(ownerId, userId);
    }

    @Transactional(readOnly = true)
    public void requireItineraryOwner(Long itineraryId, Long userId) {
        Long ownerId = itineraryRepository.findOwnerIdById(itineraryId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.ITINERARY_NOT_FOUND));
        requireOwner(ownerId, userId);
    }

    private void requireOwner(Long ownerId, Long userId) {
        if (!ownerId.equals(userId)) {
            throw new RoutePlanException(ErrorCode.ACCESS_DENIED);
        }
    }
}
