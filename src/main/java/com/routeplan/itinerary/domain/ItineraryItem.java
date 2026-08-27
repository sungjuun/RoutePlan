package com.routeplan.itinerary.domain;

import com.routeplan.budget.domain.BudgetSettings;
import com.routeplan.place.domain.Place;
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
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(
        name = "itinerary_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_itinerary_items_itinerary_sequence",
                columnNames = {"itinerary_id", "sequence"}
        )
)
public class ItineraryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "itinerary_id", nullable = false)
    private Itinerary itinerary;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(nullable = false)
    private int sequence;

    @Column(name = "travel_distance_meters", nullable = false)
    private long travelDistanceMeters;

    @Column(name = "estimated_travel_minutes", nullable = false)
    private int estimatedTravelMinutes;

    @Column(name = "visit_date")
    private LocalDate visitDate;

    @Column(name = "arrival_time")
    private LocalTime arrivalTime;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "waiting_minutes")
    private Integer waitingMinutes;

    @Column(name = "stay_minutes")
    private Integer stayMinutes;

    @Column
    private Integer priority;

    @Column(name = "must_visit")
    private Boolean mustVisit;

    @Column(name = "weather_score_adjustment", nullable = false)
    private int weatherScoreAdjustment;

    @Column(name = "estimated_cost_minor")
    private Long estimatedCostMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItineraryItemStatus status;

    protected ItineraryItem() {
    }

    ItineraryItem(
            Itinerary itinerary,
            Place place,
            int sequence,
            long travelDistanceMeters,
            int estimatedTravelMinutes
    ) {
        if (itinerary == null || place == null) {
            throw new IllegalArgumentException("일정과 장소는 필수입니다.");
        }
        if (sequence <= 0 || travelDistanceMeters < 0 || estimatedTravelMinutes < 0) {
            throw new IllegalArgumentException("방문 순서와 이동비용이 올바르지 않습니다.");
        }
        this.itinerary = itinerary;
        this.place = place;
        this.sequence = sequence;
        this.travelDistanceMeters = travelDistanceMeters;
        this.estimatedTravelMinutes = estimatedTravelMinutes;
        this.status = ItineraryItemStatus.PLANNED;
    }

    ItineraryItem(
            Itinerary itinerary,
            Place place,
            int sequence,
            long travelDistanceMeters,
            int estimatedTravelMinutes,
            LocalDate visitDate,
            LocalTime arrivalTime,
            LocalTime startTime,
            LocalTime endTime,
            int waitingMinutes,
            int stayMinutes,
            int priority,
            boolean mustVisit
    ) {
        this(
                itinerary, place, sequence, travelDistanceMeters, estimatedTravelMinutes,
                visitDate, arrivalTime, startTime, endTime, waitingMinutes, stayMinutes,
                priority, mustVisit, 0, ItineraryItemStatus.PLANNED
        );
    }

    ItineraryItem(
            Itinerary itinerary,
            Place place,
            int sequence,
            long travelDistanceMeters,
            int estimatedTravelMinutes,
            LocalDate visitDate,
            LocalTime arrivalTime,
            LocalTime startTime,
            LocalTime endTime,
            int waitingMinutes,
            int stayMinutes,
            int priority,
            boolean mustVisit,
            int weatherScoreAdjustment
    ) {
        this(
                itinerary, place, sequence, travelDistanceMeters, estimatedTravelMinutes,
                visitDate, arrivalTime, startTime, endTime, waitingMinutes, stayMinutes,
                priority, mustVisit, weatherScoreAdjustment, ItineraryItemStatus.PLANNED
        );
    }

    ItineraryItem(
            Itinerary itinerary,
            Place place,
            int sequence,
            long travelDistanceMeters,
            int estimatedTravelMinutes,
            LocalDate visitDate,
            LocalTime arrivalTime,
            LocalTime startTime,
            LocalTime endTime,
            int waitingMinutes,
            int stayMinutes,
            int priority,
            boolean mustVisit,
            int weatherScoreAdjustment,
            ItineraryItemStatus status
    ) {
        this(itinerary, place, sequence, travelDistanceMeters, estimatedTravelMinutes);
        if (visitDate == null || arrivalTime == null || startTime == null || endTime == null) {
            throw new IllegalArgumentException("방문일과 방문 시각은 필수입니다.");
        }
        if (waitingMinutes < 0 || stayMinutes <= 0 || priority < 1 || priority > 100) {
            throw new IllegalArgumentException("일정 상세 제약값이 올바르지 않습니다.");
        }
        if (startTime.isBefore(arrivalTime) || !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("도착·시작·종료시각 순서가 올바르지 않습니다.");
        }
        if (status == null) {
            throw new IllegalArgumentException("일정 항목 상태는 필수입니다.");
        }
        if (weatherScoreAdjustment < -100 || weatherScoreAdjustment > 100) {
            throw new IllegalArgumentException("날씨 점수 조정값이 올바르지 않습니다.");
        }
        this.visitDate = visitDate;
        this.arrivalTime = arrivalTime;
        this.startTime = startTime;
        this.endTime = endTime;
        this.waitingMinutes = waitingMinutes;
        this.stayMinutes = stayMinutes;
        this.priority = priority;
        this.mustVisit = mustVisit;
        this.weatherScoreAdjustment = weatherScoreAdjustment;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    void recordEstimatedCost(Long amount) {
        BudgetSettings.requireAmount(amount);
        this.estimatedCostMinor = amount;
    }

    public Long getEstimatedCostMinor() {
        return estimatedCostMinor;
    }

    public Place getPlace() {
        return place;
    }

    public int getSequence() {
        return sequence;
    }

    public long getTravelDistanceMeters() {
        return travelDistanceMeters;
    }

    public int getEstimatedTravelMinutes() {
        return estimatedTravelMinutes;
    }

    public LocalDate getVisitDate() {
        return visitDate;
    }

    public LocalTime getArrivalTime() {
        return arrivalTime;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public Integer getWaitingMinutes() {
        return waitingMinutes;
    }

    public Integer getStayMinutes() {
        return stayMinutes;
    }

    public Integer getPriority() {
        return priority;
    }

    public Boolean getMustVisit() {
        return mustVisit;
    }

    public ItineraryItemStatus getStatus() {
        return status;
    }

    public int getWeatherScoreAdjustment() {
        return weatherScoreAdjustment;
    }
}
