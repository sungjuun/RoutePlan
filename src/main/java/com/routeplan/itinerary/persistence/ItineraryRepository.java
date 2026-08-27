package com.routeplan.itinerary.persistence;

import com.routeplan.itinerary.domain.Itinerary;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItineraryRepository extends JpaRepository<Itinerary, Long> {

    @Query("select coalesce(max(itinerary.version), 0) from Itinerary itinerary where itinerary.trip.id = :tripId")
    int findMaxVersionByTripId(@Param("tripId") Long tripId);

    @EntityGraph(attributePaths = {"trip", "items", "items.place", "exclusions", "exclusions.place"})
    @Query("select itinerary from Itinerary itinerary where itinerary.id = :itineraryId")
    Optional<Itinerary> findDetailedById(@Param("itineraryId") Long itineraryId);

    @EntityGraph(attributePaths = {"trip", "items", "items.place", "exclusions", "exclusions.place"})
    @Query("""
            select itinerary
            from Itinerary itinerary
            where itinerary.trip.id = :tripId
              and itinerary.version = (
                  select max(latest.version)
                  from Itinerary latest
                  where latest.trip.id = :tripId
              )
            """)
    Optional<Itinerary> findLatestDetailedByTripId(@Param("tripId") Long tripId);

    @Query("select itinerary.trip.user.id from Itinerary itinerary where itinerary.id = :itineraryId")
    Optional<Long> findOwnerIdById(@Param("itineraryId") Long itineraryId);
}
