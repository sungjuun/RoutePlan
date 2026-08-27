package com.routeplan.place.application;

import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import com.routeplan.place.domain.Place;
import com.routeplan.place.domain.PlaceEnvironment;
import com.routeplan.place.domain.PlaceOpeningHour;
import com.routeplan.place.persistence.PlaceOpeningHourRepository;
import com.routeplan.place.persistence.PlaceRepository;
import com.routeplan.place.search.PlaceSearchProvider;
import com.routeplan.place.search.PlaceSearchQuery;
import com.routeplan.place.search.PlaceSearchResult;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlaceService {

    private final PlaceRepository placeRepository;
    private final PlaceOpeningHourRepository openingHourRepository;
    private final PlaceSearchProvider placeSearchProvider;

    public PlaceService(
            PlaceRepository placeRepository,
            PlaceOpeningHourRepository openingHourRepository,
            PlaceSearchProvider placeSearchProvider
    ) {
        this.placeRepository = placeRepository;
        this.openingHourRepository = openingHourRepository;
        this.placeSearchProvider = placeSearchProvider;
    }

    @Transactional
    public PlaceResult create(String name, BigDecimal latitude, BigDecimal longitude, String category) {
        return create(name, latitude, longitude, category, 60);
    }

    @Transactional
    public PlaceResult create(
            String name,
            BigDecimal latitude,
            BigDecimal longitude,
            String category,
            int averageStayMinutes
    ) {
        return create(name, latitude, longitude, category, averageStayMinutes, null);
    }

    @Transactional
    public PlaceResult create(
            String name,
            BigDecimal latitude,
            BigDecimal longitude,
            String category,
            int averageStayMinutes,
            PlaceEnvironment environment
    ) {
        Place place = Place.create(
                name, latitude, longitude, category, averageStayMinutes, environment
        );
        return PlaceResult.from(placeRepository.save(place));
    }

    @Transactional(readOnly = true)
    public PlaceResult get(Long placeId) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.PLACE_NOT_FOUND));
        return PlaceResult.from(place);
    }

    public List<PlaceSearchResult> search(PlaceSearchQuery query) {
        return placeSearchProvider.search(query);
    }

    @Transactional
    public ImportPlaceResult importExternal(
            String externalPlaceId,
            String name,
            BigDecimal latitude,
            BigDecimal longitude,
            String category,
            int averageStayMinutes
    ) {
        return importExternal(
                externalPlaceId, name, latitude, longitude, category, averageStayMinutes, null
        );
    }

    @Transactional
    public ImportPlaceResult importExternal(
            String externalPlaceId,
            String name,
            BigDecimal latitude,
            BigDecimal longitude,
            String category,
            int averageStayMinutes,
            PlaceEnvironment environment
    ) {
        return placeRepository.findByExternalPlaceId(externalPlaceId)
                .map(place -> new ImportPlaceResult(PlaceResult.from(place), false))
                .orElseGet(() -> {
                    Place place = Place.createExternal(
                            externalPlaceId,
                            name,
                            latitude,
                            longitude,
                            category,
                            averageStayMinutes,
                            environment
                    );
                    return new ImportPlaceResult(
                            PlaceResult.from(placeRepository.save(place)),
                            true
                    );
                });
    }

    @Transactional
    public OpeningHourResult setOpeningHour(
            Long placeId,
            DayOfWeek dayOfWeek,
            LocalTime openTime,
            LocalTime closeTime,
            boolean closed
    ) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.PLACE_NOT_FOUND));
        PlaceOpeningHour openingHour = openingHourRepository
                .findByPlaceIdAndDayOfWeek(placeId, dayOfWeek)
                .map(existing -> {
                    existing.update(openTime, closeTime, closed);
                    return existing;
                })
                .orElseGet(() -> PlaceOpeningHour.create(
                        place, dayOfWeek, openTime, closeTime, closed
                ));
        return OpeningHourResult.from(openingHourRepository.save(openingHour));
    }

    @Transactional(readOnly = true)
    public List<OpeningHourResult> getOpeningHours(Long placeId) {
        if (!placeRepository.existsById(placeId)) {
            throw new RoutePlanException(ErrorCode.PLACE_NOT_FOUND);
        }
        return openingHourRepository.findAllByPlaceIdOrderByDayOfWeekAsc(placeId).stream()
                .map(OpeningHourResult::from)
                .toList();
    }

    public record PlaceResult(
            Long id,
            String externalPlaceId,
            String name,
            BigDecimal latitude,
            BigDecimal longitude,
            String category,
            int averageStayMinutes,
            PlaceEnvironment environment,
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
                    place.getAverageStayMinutes(),
                    place.getEnvironment(),
                    place.getCreatedAt(),
                    place.getUpdatedAt()
            );
        }
    }

    public record OpeningHourResult(
            DayOfWeek dayOfWeek,
            LocalTime openTime,
            LocalTime closeTime,
            boolean closed
    ) {

        static OpeningHourResult from(PlaceOpeningHour openingHour) {
            return new OpeningHourResult(
                    openingHour.getDayOfWeek(),
                    openingHour.getOpenTime(),
                    openingHour.getCloseTime(),
                    openingHour.isClosed()
            );
        }
    }

    public record ImportPlaceResult(PlaceResult place, boolean created) {
    }
}
