package com.routeplan.trip.persistence;

import com.routeplan.trip.domain.TripPlace;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripPlaceRepository extends JpaRepository<TripPlace, Long> {

    @EntityGraph(attributePaths = "place")
    List<TripPlace> findAllByTripIdOrderByIdAsc(Long tripId);

    boolean existsByTripIdAndPlaceId(Long tripId, Long placeId);

    long countByTripId(Long tripId);

    long deleteByTripIdAndPlaceId(Long tripId, Long placeId);
}
