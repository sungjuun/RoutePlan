package com.routeplan.community.domain;

import com.routeplan.itinerary.domain.ItineraryItem;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(
        name = "shared_route_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_shared_route_items_route_sequence",
                columnNames = {"shared_route_id", "sequence"}
        )
)
public class SharedRouteItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shared_route_id", nullable = false)
    private SharedRoute sharedRoute;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    @Column(nullable = false)
    private int sequence;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(name = "place_name", nullable = false, length = 150)
    private String placeName;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal longitude;

    @Column(length = 50)
    private String category;

    @Column(name = "arrival_time", nullable = false)
    private LocalTime arrivalTime;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "travel_distance_meters", nullable = false)
    private long travelDistanceMeters;

    @Column(name = "estimated_travel_minutes", nullable = false)
    private int estimatedTravelMinutes;

    @Column(name = "waiting_minutes", nullable = false)
    private int waitingMinutes;

    @Column(name = "stay_minutes", nullable = false)
    private int stayMinutes;

    @Column(nullable = false)
    private int priority;

    @Column(name = "must_visit", nullable = false)
    private boolean mustVisit;

    protected SharedRouteItem() {
    }

    private SharedRouteItem(SharedRoute sharedRoute, ItineraryItem source) {
        if (sharedRoute == null || source == null) {
            throw new IllegalArgumentException("공개 루트 항목의 원본 일정은 필수입니다.");
        }
        if (source.getVisitDate() == null || source.getArrivalTime() == null
                || source.getStartTime() == null || source.getEndTime() == null
                || source.getWaitingMinutes() == null || source.getStayMinutes() == null
                || source.getPriority() == null || source.getMustVisit() == null) {
            throw new IllegalArgumentException("시간표가 완성되지 않은 일정은 공개할 수 없습니다.");
        }
        Place sourcePlace = source.getPlace();
        this.sharedRoute = sharedRoute;
        this.place = sourcePlace;
        this.dayNumber = Math.toIntExact(
                ChronoUnit.DAYS.between(
                        sharedRoute.getSourceStartDate(), source.getVisitDate()
                ) + 1
        );
        this.sequence = source.getSequence();
        this.visitDate = source.getVisitDate();
        this.placeName = sourcePlace.getName();
        this.latitude = sourcePlace.getLatitude();
        this.longitude = sourcePlace.getLongitude();
        this.category = sourcePlace.getCategory();
        this.arrivalTime = source.getArrivalTime();
        this.startTime = source.getStartTime();
        this.endTime = source.getEndTime();
        this.travelDistanceMeters = source.getTravelDistanceMeters();
        this.estimatedTravelMinutes = source.getEstimatedTravelMinutes();
        this.waitingMinutes = source.getWaitingMinutes();
        this.stayMinutes = source.getStayMinutes();
        this.priority = source.getPriority();
        this.mustVisit = source.getMustVisit();
    }

    static SharedRouteItem snapshot(SharedRoute sharedRoute, ItineraryItem source) {
        return new SharedRouteItem(sharedRoute, source);
    }

    public Long getId() {
        return id;
    }

    public Place getPlace() {
        return place;
    }

    public int getDayNumber() {
        return dayNumber;
    }

    public int getSequence() {
        return sequence;
    }

    public LocalDate getVisitDate() {
        return visitDate;
    }

    public String getPlaceName() {
        return placeName;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public String getCategory() {
        return category;
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

    public long getTravelDistanceMeters() {
        return travelDistanceMeters;
    }

    public int getEstimatedTravelMinutes() {
        return estimatedTravelMinutes;
    }

    public int getWaitingMinutes() {
        return waitingMinutes;
    }

    public int getStayMinutes() {
        return stayMinutes;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isMustVisit() {
        return mustVisit;
    }
}
