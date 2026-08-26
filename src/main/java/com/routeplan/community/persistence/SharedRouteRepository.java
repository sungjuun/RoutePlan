package com.routeplan.community.persistence;

import com.routeplan.community.domain.SharedRoute;
import com.routeplan.community.domain.SharedRouteVisibility;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SharedRouteRepository extends JpaRepository<SharedRoute, Long> {

    boolean existsBySourceItineraryId(Long sourceItineraryId);

    @EntityGraph(attributePaths = {"owner", "items", "items.place"})
    @Query("select route from SharedRoute route where route.id = :routeId")
    Optional<SharedRoute> findDetailedById(@Param("routeId") Long routeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select route from SharedRoute route where route.id = :routeId")
    Optional<SharedRoute> findByIdForUpdate(@Param("routeId") Long routeId);

    @Query(
            value = """
                    select route
                    from SharedRoute route
                    join fetch route.owner
                    where route.visibility = :visibility
                      and (:region = '' or lower(route.region) like lower(concat('%', :region, '%')))
                      and (:travelDays is null or route.travelDays = :travelDays)
                    """,
            countQuery = """
                    select count(route)
                    from SharedRoute route
                    where route.visibility = :visibility
                      and (:region = '' or lower(route.region) like lower(concat('%', :region, '%')))
                      and (:travelDays is null or route.travelDays = :travelDays)
                    """
    )
    Page<SharedRoute> findDiscoverable(
            @Param("visibility") SharedRouteVisibility visibility,
            @Param("region") String region,
            @Param("travelDays") Integer travelDays,
            Pageable pageable
    );
}
