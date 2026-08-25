package com.routeplan.place.persistence;

import com.routeplan.place.domain.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    Optional<Place> findByExternalPlaceId(String externalPlaceId);
}
