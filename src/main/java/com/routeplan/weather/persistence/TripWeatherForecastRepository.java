package com.routeplan.weather.persistence;

import com.routeplan.weather.domain.TripWeatherForecast;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripWeatherForecastRepository extends JpaRepository<TripWeatherForecast, Long> {

    List<TripWeatherForecast> findAllByTripIdOrderByForecastDateAsc(Long tripId);

    long deleteAllByTripId(Long tripId);
}
