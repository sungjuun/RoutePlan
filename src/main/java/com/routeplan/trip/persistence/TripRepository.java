package com.routeplan.trip.persistence;

import com.routeplan.trip.domain.Trip;
import jakarta.persistence.LockModeType;
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
}
