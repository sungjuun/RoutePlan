package com.routeplan.place.persistence;

import com.routeplan.place.domain.PlaceOpeningHour;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface PlaceOpeningHourRepository extends JpaRepository<PlaceOpeningHour, Long> {

    Optional<PlaceOpeningHour> findByPlaceIdAndDayOfWeek(Long placeId, DayOfWeek dayOfWeek);

    List<PlaceOpeningHour> findAllByPlaceIdOrderByDayOfWeekAsc(Long placeId);

    @EntityGraph(attributePaths = "place")
    List<PlaceOpeningHour> findAllByPlaceIdInAndDayOfWeek(List<Long> placeIds, DayOfWeek dayOfWeek);
}
