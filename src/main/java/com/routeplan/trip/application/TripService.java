package com.routeplan.trip.application;

import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import com.routeplan.place.domain.Place;
import com.routeplan.place.persistence.PlaceRepository;
import com.routeplan.trip.domain.TransportMode;
import com.routeplan.trip.domain.Trip;
import com.routeplan.trip.domain.TripPlace;
import com.routeplan.trip.domain.TripStatus;
import com.routeplan.trip.persistence.TripPlaceRepository;
import com.routeplan.trip.persistence.TripRepository;
import com.routeplan.user.domain.User;
import com.routeplan.user.persistence.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripService {

    static final int MAX_PLACES_PER_TRIP = 50;

    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final PlaceRepository placeRepository;
    private final TripPlaceRepository tripPlaceRepository;

    public TripService(
            UserRepository userRepository,
            TripRepository tripRepository,
            PlaceRepository placeRepository,
            TripPlaceRepository tripPlaceRepository
    ) {
        this.userRepository = userRepository;
        this.tripRepository = tripRepository;
        this.placeRepository = placeRepository;
        this.tripPlaceRepository = tripPlaceRepository;
    }

    @Transactional
    public TripResult create(CreateTripCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new RoutePlanException(ErrorCode.USER_NOT_FOUND));
        Trip trip = Trip.create(
                user,
                command.name(),
                command.startDate(),
                command.endDate(),
                command.accommodationName(),
                command.accommodationLatitude(),
                command.accommodationLongitude(),
                command.transportMode()
        );
        return toResult(tripRepository.save(trip), List.of());
    }

    @Transactional(readOnly = true)
    public TripResult get(Long tripId) {
        Trip trip = getTrip(tripId);
        return toResult(trip, tripPlaceRepository.findAllByTripIdOrderByIdAsc(tripId));
    }

    @Transactional
    public TripResult update(Long tripId, UpdateTripCommand command) {
        Trip trip = getTrip(tripId);
        BigDecimal latitude = command.accommodationLatitude() == null
                ? trip.getAccommodationLatitude() : command.accommodationLatitude();
        BigDecimal longitude = command.accommodationLongitude() == null
                ? trip.getAccommodationLongitude() : command.accommodationLongitude();

        trip.update(
                command.name() == null ? trip.getName() : command.name(),
                command.startDate() == null ? trip.getStartDate() : command.startDate(),
                command.endDate() == null ? trip.getEndDate() : command.endDate(),
                command.accommodationName() == null ? trip.getAccommodationName() : command.accommodationName(),
                latitude,
                longitude,
                command.transportMode() == null ? trip.getTransportMode() : command.transportMode()
        );
        return toResult(trip, tripPlaceRepository.findAllByTripIdOrderByIdAsc(tripId));
    }

    @Transactional
    public TripResult addPlace(Long tripId, Long placeId) {
        Trip trip = getTripForUpdate(tripId);
        if (tripPlaceRepository.existsByTripIdAndPlaceId(tripId, placeId)) {
            throw new RoutePlanException(ErrorCode.DUPLICATE_TRIP_PLACE);
        }
        if (tripPlaceRepository.countByTripId(tripId) >= MAX_PLACES_PER_TRIP) {
            throw new RoutePlanException(ErrorCode.TRIP_PLACE_LIMIT_EXCEEDED);
        }
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.PLACE_NOT_FOUND));
        tripPlaceRepository.save(new TripPlace(trip, place));
        trip.markDraft();
        return toResult(trip, tripPlaceRepository.findAllByTripIdOrderByIdAsc(tripId));
    }

    @Transactional
    public void removePlace(Long tripId, Long placeId) {
        Trip trip = getTripForUpdate(tripId);
        if (tripPlaceRepository.deleteByTripIdAndPlaceId(tripId, placeId) == 0) {
            throw new RoutePlanException(ErrorCode.TRIP_PLACE_NOT_FOUND);
        }
        trip.markDraft();
    }

    private Trip getTrip(Long tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_NOT_FOUND));
    }

    private Trip getTripForUpdate(Long tripId) {
        return tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_NOT_FOUND));
    }

    private TripResult toResult(Trip trip, List<TripPlace> tripPlaces) {
        List<TripPlaceResult> places = tripPlaces.stream()
                .map(tripPlace -> {
                    Place place = tripPlace.getPlace();
                    return new TripPlaceResult(
                            place.getId(),
                            place.getName(),
                            place.getLatitude(),
                            place.getLongitude(),
                            place.getCategory()
                    );
                })
                .toList();

        return new TripResult(
                trip.getId(),
                trip.getUser().getId(),
                trip.getName(),
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getAccommodationName(),
                trip.getAccommodationLatitude(),
                trip.getAccommodationLongitude(),
                trip.getTransportMode(),
                trip.getStatus(),
                trip.getCreatedAt(),
                trip.getUpdatedAt(),
                places
        );
    }

    public record CreateTripCommand(
            Long userId,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            String accommodationName,
            BigDecimal accommodationLatitude,
            BigDecimal accommodationLongitude,
            TransportMode transportMode
    ) {
    }

    public record UpdateTripCommand(
            String name,
            LocalDate startDate,
            LocalDate endDate,
            String accommodationName,
            BigDecimal accommodationLatitude,
            BigDecimal accommodationLongitude,
            TransportMode transportMode
    ) {
    }

    public record TripResult(
            Long id,
            Long userId,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            String accommodationName,
            BigDecimal accommodationLatitude,
            BigDecimal accommodationLongitude,
            TransportMode transportMode,
            TripStatus status,
            Instant createdAt,
            Instant updatedAt,
            List<TripPlaceResult> places
    ) {
    }

    public record TripPlaceResult(
            Long placeId,
            String name,
            BigDecimal latitude,
            BigDecimal longitude,
            String category
    ) {
    }
}
