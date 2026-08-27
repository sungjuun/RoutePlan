package com.routeplan.trip.domain;

import com.routeplan.budget.domain.BudgetSettings;
import com.routeplan.place.domain.Place;
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
import java.time.Instant;
import java.time.LocalTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
        name = "trip_places",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_trip_places_trip_place",
                columnNames = {"trip_id", "place_id"}
        )
)
public class TripPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(nullable = false)
    private int priority;

    @Column(name = "must_visit", nullable = false)
    private boolean mustVisit;

    @Column(name = "preferred_start_time")
    private LocalTime preferredStartTime;

    @Column(name = "preferred_end_time")
    private LocalTime preferredEndTime;

    @Column(name = "minimum_stay_minutes")
    private Integer minimumStayMinutes;

    @Column(name = "maximum_stay_minutes")
    private Integer maximumStayMinutes;

    @Column(name = "estimated_cost_minor")
    private Long estimatedCostMinor;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TripPlace() {
    }

    public TripPlace(Trip trip, Place place) {
        this(trip, place, 50, false, null, null, null, null);
    }

    public TripPlace(
            Trip trip,
            Place place,
            int priority,
            boolean mustVisit,
            LocalTime preferredStartTime,
            LocalTime preferredEndTime,
            Integer minimumStayMinutes,
            Integer maximumStayMinutes
    ) {
        if (trip == null || place == null) {
            throw new IllegalArgumentException("여행과 장소는 필수입니다.");
        }
        this.trip = trip;
        this.place = place;
        updateConstraints(
                priority,
                mustVisit,
                preferredStartTime,
                preferredEndTime,
                minimumStayMinutes,
                maximumStayMinutes
        );
    }

    public void updateConstraints(
            int priority,
            boolean mustVisit,
            LocalTime preferredStartTime,
            LocalTime preferredEndTime,
            Integer minimumStayMinutes,
            Integer maximumStayMinutes
    ) {
        if (priority < 1 || priority > 100) {
            throw new IllegalArgumentException("장소 우선순위는 1 이상 100 이하여야 합니다.");
        }
        if (preferredStartTime != null && preferredEndTime != null
                && !preferredEndTime.isAfter(preferredStartTime)) {
            throw new IllegalArgumentException("선호 종료시간은 선호 시작시간보다 늦어야 합니다.");
        }
        if (minimumStayMinutes != null && (minimumStayMinutes <= 0 || minimumStayMinutes > 1_440)) {
            throw new IllegalArgumentException("최소 체류시간은 1분 이상 1,440분 이하여야 합니다.");
        }
        if (maximumStayMinutes != null && (maximumStayMinutes <= 0 || maximumStayMinutes > 1_440)) {
            throw new IllegalArgumentException("최대 체류시간은 1분 이상 1,440분 이하여야 합니다.");
        }
        if (minimumStayMinutes != null && maximumStayMinutes != null
                && maximumStayMinutes < minimumStayMinutes) {
            throw new IllegalArgumentException("최대 체류시간은 최소 체류시간 이상이어야 합니다.");
        }
        this.priority = priority;
        this.mustVisit = mustVisit;
        this.preferredStartTime = preferredStartTime;
        this.preferredEndTime = preferredEndTime;
        this.minimumStayMinutes = minimumStayMinutes;
        this.maximumStayMinutes = maximumStayMinutes;
    }

    public void updateEstimatedCost(Long amount) {
        BudgetSettings.requireAmount(amount);
        this.estimatedCostMinor = amount;
    }

    public Long getEstimatedCostMinor() {
        return estimatedCostMinor;
    }

    public Long getId() {
        return id;
    }

    public Trip getTrip() {
        return trip;
    }

    public Place getPlace() {
        return place;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isMustVisit() {
        return mustVisit;
    }

    public LocalTime getPreferredStartTime() {
        return preferredStartTime;
    }

    public LocalTime getPreferredEndTime() {
        return preferredEndTime;
    }

    public Integer getMinimumStayMinutes() {
        return minimumStayMinutes;
    }

    public Integer getMaximumStayMinutes() {
        return maximumStayMinutes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
