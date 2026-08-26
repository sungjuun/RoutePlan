package com.routeplan.itinerary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(
        name = "itinerary_days",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_itinerary_days_itinerary_date",
                        columnNames = {"itinerary_id", "visit_date"}
                ),
                @UniqueConstraint(
                        name = "uk_itinerary_days_itinerary_number",
                        columnNames = {"itinerary_id", "day_number"}
                )
        }
)
public class ItineraryDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "itinerary_id", nullable = false)
    private Itinerary itinerary;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    @Column(name = "total_distance_meters", nullable = false)
    private long totalDistanceMeters;

    @Column(name = "estimated_travel_minutes", nullable = false)
    private int estimatedTravelMinutes;

    @Column(name = "total_stay_minutes", nullable = false)
    private int totalStayMinutes;

    @Column(name = "total_waiting_minutes", nullable = false)
    private int totalWaitingMinutes;

    @Column(name = "return_travel_distance_meters", nullable = false)
    private long returnTravelDistanceMeters;

    @Column(name = "return_travel_minutes", nullable = false)
    private int returnTravelMinutes;

    @Column(name = "return_arrival_time", nullable = false)
    private LocalTime returnArrivalTime;

    @Column(name = "returned_to_accommodation", nullable = false)
    private boolean returnedToAccommodation;

    protected ItineraryDay() {
    }

    ItineraryDay(
            Itinerary itinerary,
            LocalDate visitDate,
            int dayNumber,
            long totalDistanceMeters,
            int estimatedTravelMinutes,
            int totalStayMinutes,
            int totalWaitingMinutes,
            long returnTravelDistanceMeters,
            int returnTravelMinutes,
            LocalTime returnArrivalTime,
            boolean returnedToAccommodation
    ) {
        if (itinerary == null || visitDate == null || returnArrivalTime == null) {
            throw new IllegalArgumentException("일자별 일정의 여행·날짜·복귀시각은 필수입니다.");
        }
        if (dayNumber <= 0 || totalDistanceMeters < 0 || estimatedTravelMinutes < 0
                || totalStayMinutes < 0 || totalWaitingMinutes < 0
                || returnTravelDistanceMeters < 0 || returnTravelMinutes < 0) {
            throw new IllegalArgumentException("일자별 일정 합계가 올바르지 않습니다.");
        }
        this.itinerary = itinerary;
        this.visitDate = visitDate;
        this.dayNumber = dayNumber;
        this.totalDistanceMeters = totalDistanceMeters;
        this.estimatedTravelMinutes = estimatedTravelMinutes;
        this.totalStayMinutes = totalStayMinutes;
        this.totalWaitingMinutes = totalWaitingMinutes;
        this.returnTravelDistanceMeters = returnTravelDistanceMeters;
        this.returnTravelMinutes = returnTravelMinutes;
        this.returnArrivalTime = returnArrivalTime;
        this.returnedToAccommodation = returnedToAccommodation;
    }

    public LocalDate getVisitDate() {
        return visitDate;
    }

    public int getDayNumber() {
        return dayNumber;
    }

    public long getTotalDistanceMeters() {
        return totalDistanceMeters;
    }

    public int getEstimatedTravelMinutes() {
        return estimatedTravelMinutes;
    }

    public int getTotalStayMinutes() {
        return totalStayMinutes;
    }

    public int getTotalWaitingMinutes() {
        return totalWaitingMinutes;
    }

    public long getReturnTravelDistanceMeters() {
        return returnTravelDistanceMeters;
    }

    public int getReturnTravelMinutes() {
        return returnTravelMinutes;
    }

    public LocalTime getReturnArrivalTime() {
        return returnArrivalTime;
    }

    public boolean isReturnedToAccommodation() {
        return returnedToAccommodation;
    }
}
