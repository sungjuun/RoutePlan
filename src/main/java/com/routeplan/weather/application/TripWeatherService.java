package com.routeplan.weather.application;

import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import com.routeplan.trip.domain.Trip;
import com.routeplan.trip.persistence.TripRepository;
import com.routeplan.weather.domain.TripWeatherForecast;
import com.routeplan.weather.domain.WeatherCondition;
import com.routeplan.weather.persistence.TripWeatherForecastRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripWeatherService {

    private final TripRepository tripRepository;
    private final TripWeatherForecastRepository forecastRepository;

    public TripWeatherService(
            TripRepository tripRepository,
            TripWeatherForecastRepository forecastRepository
    ) {
        this.tripRepository = tripRepository;
        this.forecastRepository = forecastRepository;
    }

    @Transactional(readOnly = true)
    public List<ForecastResult> get(Long tripId) {
        requireTrip(tripId);
        return forecastRepository.findAllByTripIdOrderByForecastDateAsc(tripId).stream()
                .map(ForecastResult::from)
                .toList();
    }

    @Transactional
    public List<ForecastResult> replace(Long tripId, List<ForecastCommand> commands) {
        Trip trip = tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_NOT_FOUND));
        if (commands == null) {
            throw new IllegalArgumentException("날짜별 예보 목록은 필수입니다.");
        }
        HashSet<LocalDate> dates = new HashSet<>();
        for (ForecastCommand command : commands) {
            if (command == null || command.forecastDate() == null || command.condition() == null) {
                throw new IllegalArgumentException("예보 날짜와 날씨 상태는 필수입니다.");
            }
            if (!dates.add(command.forecastDate())) {
                throw new IllegalArgumentException("같은 날짜의 예보를 중복 저장할 수 없습니다.");
            }
            if (command.forecastDate().isBefore(trip.getStartDate())
                    || command.forecastDate().isAfter(trip.getEndDate())) {
                throw new IllegalArgumentException("예보 날짜는 여행 기간 안에 있어야 합니다.");
            }
            if (command.precipitationProbability() < 0
                    || command.precipitationProbability() > 100) {
                throw new IllegalArgumentException("강수확률은 0 이상 100 이하여야 합니다.");
            }
        }

        forecastRepository.deleteAllByTripId(tripId);
        forecastRepository.flush();
        List<TripWeatherForecast> forecasts = commands.stream()
                .filter(command -> command.condition() != WeatherCondition.UNKNOWN)
                .map(command -> TripWeatherForecast.create(
                        trip,
                        command.forecastDate(),
                        command.condition(),
                        command.precipitationProbability()
                ))
                .toList();
        trip.markDraft();
        return forecastRepository.saveAllAndFlush(forecasts).stream()
                .map(ForecastResult::from)
                .toList();
    }

    private Trip requireTrip(Long tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_NOT_FOUND));
    }

    public record ForecastCommand(
            LocalDate forecastDate,
            WeatherCondition condition,
            int precipitationProbability
    ) {
    }

    public record ForecastResult(
            LocalDate forecastDate,
            WeatherCondition condition,
            int precipitationProbability,
            Instant updatedAt, String source
    ) {

        static ForecastResult from(TripWeatherForecast forecast) {
            return new ForecastResult(
                    forecast.getForecastDate(),
                    forecast.getCondition(),
                    forecast.getPrecipitationProbability(),
                    forecast.getUpdatedAt(), forecast.getSource()
            );
        }
    }
}
