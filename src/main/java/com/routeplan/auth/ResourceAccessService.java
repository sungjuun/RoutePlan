package com.routeplan.auth;

import com.routeplan.collaboration.domain.TripMemberRole;
import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import com.routeplan.itinerary.persistence.ItineraryRepository;
import com.routeplan.trip.persistence.TripRepository;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceAccessService {

    private final TripRepository tripRepository;
    private final ItineraryRepository itineraryRepository;
    private final JdbcTemplate jdbc;

    public ResourceAccessService(
            TripRepository tripRepository,
            ItineraryRepository itineraryRepository,
            JdbcTemplate jdbc
    ) {
        this.tripRepository = tripRepository;
        this.itineraryRepository = itineraryRepository;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public void requireTripOwner(Long tripId, Long userId) {
        Long ownerId = tripRepository.findOwnerIdById(tripId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_NOT_FOUND));
        requireOwner(ownerId, userId);
    }

    @Transactional(readOnly = true)
    public TripMemberRole requireTripViewer(Long tripId, Long userId) {
        return roleForTrip(tripId, userId);
    }

    @Transactional(readOnly = true)
    public TripMemberRole requireTripEditor(Long tripId, Long userId) {
        TripMemberRole role = roleForTrip(tripId, userId);
        if (!role.canEdit()) {
            throw new RoutePlanException(ErrorCode.ACCESS_DENIED);
        }
        return role;
    }

    @Transactional(readOnly = true)
    public void requireItineraryOwner(Long itineraryId, Long userId) {
        Long ownerId = itineraryRepository.findOwnerIdById(itineraryId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.ITINERARY_NOT_FOUND));
        requireOwner(ownerId, userId);
    }

    @Transactional(readOnly = true)
    public TripMemberRole requireItineraryViewer(Long itineraryId, Long userId) {
        return roleForTrip(tripIdForItinerary(itineraryId), userId);
    }

    @Transactional(readOnly = true)
    public TripMemberRole requireItineraryEditor(Long itineraryId, Long userId) {
        TripMemberRole role = roleForTrip(tripIdForItinerary(itineraryId), userId);
        if (!role.canEdit()) {
            throw new RoutePlanException(ErrorCode.ACCESS_DENIED);
        }
        return role;
    }

    @Transactional(readOnly = true)
    public TripMemberRole roleForTrip(Long tripId, Long userId) {
        List<TripMemberRole> roles = jdbc.query("""
                SELECT CASE WHEN trip.user_id = ? THEN 'OWNER' ELSE member.role END AS access_role
                FROM trips trip
                LEFT JOIN trip_members member ON member.trip_id = trip.id AND member.user_id = ?
                WHERE trip.id = ? AND (trip.user_id = ? OR member.user_id IS NOT NULL)
                """, (rs, row) -> TripMemberRole.valueOf(rs.getString("access_role")),
                userId, userId, tripId, userId);
        if (!roles.isEmpty()) {
            return roles.getFirst();
        }
        if (!tripRepository.existsById(tripId)) {
            throw new RoutePlanException(ErrorCode.TRIP_NOT_FOUND);
        }
        throw new RoutePlanException(ErrorCode.ACCESS_DENIED);
    }

    private Long tripIdForItinerary(Long itineraryId) {
        List<Long> tripIds = jdbc.query(
                "SELECT trip_id FROM itineraries WHERE id=?",
                (rs, row) -> rs.getLong(1), itineraryId);
        if (tripIds.isEmpty()) {
            throw new RoutePlanException(ErrorCode.ITINERARY_NOT_FOUND);
        }
        return tripIds.getFirst();
    }

    private void requireOwner(Long ownerId, Long userId) {
        if (!ownerId.equals(userId)) {
            throw new RoutePlanException(ErrorCode.ACCESS_DENIED);
        }
    }
}
