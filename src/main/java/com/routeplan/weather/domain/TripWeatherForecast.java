package com.routeplan.weather.domain;

import com.routeplan.trip.domain.Trip;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "trip_weather_forecasts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_trip_weather_forecasts_trip_date",
                columnNames = {"trip_id", "forecast_date"}
        )
)
public class TripWeatherForecast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "forecast_date", nullable = false)
    private LocalDate forecastDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WeatherCondition condition;

    @Column(name = "precipitation_probability", nullable = false)
    private int precipitationProbability;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TripWeatherForecast() {
    }

    private TripWeatherForecast(
            Trip trip,
            LocalDate forecastDate,
            WeatherCondition condition,
            int precipitationProbability
    ) {
        if (trip == null || forecastDate == null) {
            throw new IllegalArgumentException("여행과 예보 날짜는 필수입니다.");
        }
        if (forecastDate.isBefore(trip.getStartDate()) || forecastDate.isAfter(trip.getEndDate())) {
            throw new IllegalArgumentException("예보 날짜는 여행 기간 안에 있어야 합니다.");
        }
        this.trip = trip;
        this.forecastDate = forecastDate;
        update(condition, precipitationProbability);
    }

    public static TripWeatherForecast create(
            Trip trip,
            LocalDate forecastDate,
            WeatherCondition condition,
            int precipitationProbability
    ) {
        return new TripWeatherForecast(trip, forecastDate, condition, precipitationProbability);
    }

    public void update(WeatherCondition condition, int precipitationProbability) {
        if (condition == null || condition == WeatherCondition.UNKNOWN) {
            throw new IllegalArgumentException("저장할 예보에는 구체적인 날씨 상태가 필요합니다.");
        }
        new WeatherSnapshot(condition, precipitationProbability);
        this.condition = condition;
        this.precipitationProbability = precipitationProbability;
    }

    public LocalDate getForecastDate() {
        return forecastDate;
    }

    public WeatherCondition getCondition() {
        return condition;
    }

    public int getPrecipitationProbability() {
        return precipitationProbability;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public WeatherSnapshot toSnapshot() {
        return new WeatherSnapshot(condition, precipitationProbability);
    }
}
