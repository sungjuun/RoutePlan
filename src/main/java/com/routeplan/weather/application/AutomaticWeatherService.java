package com.routeplan.weather.application;

import com.routeplan.common.error.*;
import com.routeplan.trip.domain.Trip;
import com.routeplan.trip.persistence.TripRepository;
import com.routeplan.weather.domain.TripWeatherForecast;
import com.routeplan.weather.persistence.TripWeatherForecastRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class AutomaticWeatherService {
    private final TripRepository trips;
    private final TripWeatherForecastRepository forecasts;
    private final OpenMeteoClient client;
    private final TransactionTemplate tx;

    public AutomaticWeatherService(TripRepository trips, TripWeatherForecastRepository forecasts,
            OpenMeteoClient client, PlatformTransactionManager manager) {
        this.trips = trips; this.forecasts = forecasts; this.client = client; this.tx = new TransactionTemplate(manager);
    }

    public RefreshResult refresh(long tripId) {
        Input input = tx.execute(s -> Input.from(require(tripId)));
        OpenMeteoClient.Forecast result = client.fetch(input.latitude(), input.longitude());
        return tx.execute(s -> {
            Trip trip = trips.findByIdForUpdate(tripId).orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_NOT_FOUND));
            if (!input.equals(Input.from(trip))) throw new RoutePlanException(ErrorCode.OPTIMIZATION_INPUT_CHANGED);
            Map<LocalDate, TripWeatherForecast> byDate = new HashMap<>();
            forecasts.findAllByTripIdOrderByForecastDateAsc(tripId).forEach(f -> byDate.put(f.getForecastDate(), f));
            int updated = 0, manual = 0;
            for (var day : result.days()) {
                if (day.date().isBefore(trip.getStartDate()) || day.date().isAfter(trip.getEndDate())) continue;
                var existing = byDate.get(day.date());
                if (existing != null && existing.getSource().equals("MANUAL")) { manual++; continue; }
                if (existing == null) existing = TripWeatherForecast.create(trip, day.date(), day.condition(), day.probability());
                existing.applyAutomatic(day.condition(), day.probability());
                forecasts.save(existing); updated++;
            }
            trip.updateTimeZone(result.timeZoneId());
            return new RefreshResult(updated, manual, result.timeZoneId(), result.fetchedAt(),
                    "Open-Meteo (CC BY 4.0)", "예보 가능 날짜만 갱신했습니다. 직접 입력한 날씨는 보존합니다.");
        });
    }

    public String zone(long tripId) { return require(tripId).getTimeZoneId(); }
    public void setZone(long tripId, String zone) {
        tx.executeWithoutResult(s -> trips.findByIdForUpdate(tripId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_NOT_FOUND)).updateTimeZone(zone));
    }
    private Trip require(long id) { return trips.findById(id).orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_NOT_FOUND)); }
    private record Input(LocalDate start, LocalDate end, BigDecimal latitude, BigDecimal longitude, String zone) {
        static Input from(Trip t) { return new Input(t.getStartDate(), t.getEndDate(), t.getAccommodationLatitude(), t.getAccommodationLongitude(), t.getTimeZoneId()); }
    }
    public record RefreshResult(int updatedDates, int preservedManualDates, String timeZoneId,
            Instant fetchedAt, String source, String message) {}
}
