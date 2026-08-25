package com.routeplan.place.application;

import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import com.routeplan.place.domain.Place;
import com.routeplan.place.persistence.PlaceRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlaceService {

    private final PlaceRepository placeRepository;

    public PlaceService(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    @Transactional
    public PlaceResult create(String name, BigDecimal latitude, BigDecimal longitude, String category) {
        Place place = Place.create(name, latitude, longitude, category);
        return PlaceResult.from(placeRepository.save(place));
    }

    @Transactional(readOnly = true)
    public PlaceResult get(Long placeId) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.PLACE_NOT_FOUND));
        return PlaceResult.from(place);
    }

    public record PlaceResult(
            Long id,
            String externalPlaceId,
            String name,
            BigDecimal latitude,
            BigDecimal longitude,
            String category,
            Instant createdAt,
            Instant updatedAt
    ) {

        static PlaceResult from(Place place) {
            return new PlaceResult(
                    place.getId(),
                    place.getExternalPlaceId(),
                    place.getName(),
                    place.getLatitude(),
                    place.getLongitude(),
                    place.getCategory(),
                    place.getCreatedAt(),
                    place.getUpdatedAt()
            );
        }
    }
}
