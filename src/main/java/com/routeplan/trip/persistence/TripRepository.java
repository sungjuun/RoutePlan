package com.routeplan.trip.persistence;

import com.routeplan.trip.domain.Trip;
import com.routeplan.trip.domain.TransportMode;
import com.routeplan.trip.domain.TripPace;
import com.routeplan.trip.domain.TripStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripRepository extends JpaRepository<Trip, Long> {

    @Override
    @EntityGraph(attributePaths = "user")
    Optional<Trip> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "user")
    @Query("select trip from Trip trip where trip.id = :tripId")
    Optional<Trip> findByIdForUpdate(@Param("tripId") Long tripId);

    @Query("select trip.user.id from Trip trip where trip.id = :tripId")
    Optional<Long> findOwnerIdById(@Param("tripId") Long tripId);

    @Query("""
            select trip.id as id,
                   trip.name as name,
                   trip.startDate as startDate,
                   trip.endDate as endDate,
                   trip.accommodationName as accommodationName,
                   trip.transportMode as transportMode,
                   trip.pace as pace,
                   trip.status as status,
                   trip.createdAt as createdAt,
                   trip.updatedAt as updatedAt,
                   count(tripPlace.id) as placeCount
            from Trip trip
            left join TripPlace tripPlace on tripPlace.trip = trip
            where trip.user.id = :userId
            group by trip
            order by trip.updatedAt desc
            """)
    List<TripListProjection> findAllSummariesByUserId(@Param("userId") Long userId);

    interface TripListProjection {

        Long getId();

        String getName();

        LocalDate getStartDate();

        LocalDate getEndDate();

        String getAccommodationName();

        TransportMode getTransportMode();

        TripPace getPace();

        TripStatus getStatus();

        Instant getCreatedAt();

        Instant getUpdatedAt();

        long getPlaceCount();
    }
}
