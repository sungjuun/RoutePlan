package com.routeplan.itinerary.domain;

import com.routeplan.place.domain.Place;
import com.routeplan.optimization.domain.OptimizationAlgorithm;
import com.routeplan.optimization.constraint.ExclusionReason;
import com.routeplan.trip.domain.Trip;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
        name = "itineraries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_itineraries_trip_version",
                columnNames = {"trip_id", "version"}
        )
)
public class Itinerary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private OptimizationAlgorithm algorithm;

    @Column(name = "total_distance_meters", nullable = false)
    private long totalDistanceMeters;

    @Column(name = "estimated_travel_minutes", nullable = false)
    private int estimatedTravelMinutes;

    @Column(name = "optimization_score", nullable = false)
    private int optimizationScore;

    @Column(name = "total_stay_minutes", nullable = false)
    private int totalStayMinutes;

    @Column(name = "total_waiting_minutes", nullable = false)
    private int totalWaitingMinutes;

    @Column(name = "return_travel_distance_meters", nullable = false)
    private long returnTravelDistanceMeters;

    @Column(name = "return_travel_minutes", nullable = false)
    private int returnTravelMinutes;

    @Column(name = "return_arrival_time")
    private LocalTime returnArrivalTime;

    @Column(name = "returned_to_accommodation", nullable = false)
    private boolean returnedToAccommodation;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "itinerary", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private final List<ItineraryItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "itinerary", cascade = CascadeType.ALL, orphanRemoval = true)
    private final Set<ItineraryExclusion> exclusions = new LinkedHashSet<>();

    protected Itinerary() {
    }

    private Itinerary(
            Trip trip,
            int version,
            OptimizationAlgorithm algorithm,
            long totalDistanceMeters,
            int estimatedTravelMinutes,
            int optimizationScore,
            int totalStayMinutes,
            int totalWaitingMinutes,
            long returnTravelDistanceMeters,
            int returnTravelMinutes,
            LocalTime returnArrivalTime,
            boolean returnedToAccommodation
    ) {
        if (trip == null || algorithm == null) {
            throw new IllegalArgumentException("여행과 알고리즘은 필수입니다.");
        }
        if (version <= 0 || totalDistanceMeters < 0 || estimatedTravelMinutes < 0
                || optimizationScore < 0 || totalStayMinutes < 0 || totalWaitingMinutes < 0
                || returnTravelDistanceMeters < 0 || returnTravelMinutes < 0) {
            throw new IllegalArgumentException("일정 버전과 이동비용이 올바르지 않습니다.");
        }
        this.trip = trip;
        this.version = version;
        this.algorithm = algorithm;
        this.totalDistanceMeters = totalDistanceMeters;
        this.estimatedTravelMinutes = estimatedTravelMinutes;
        this.optimizationScore = optimizationScore;
        this.totalStayMinutes = totalStayMinutes;
        this.totalWaitingMinutes = totalWaitingMinutes;
        this.returnTravelDistanceMeters = returnTravelDistanceMeters;
        this.returnTravelMinutes = returnTravelMinutes;
        this.returnArrivalTime = returnArrivalTime;
        this.returnedToAccommodation = returnedToAccommodation;
    }

    public static Itinerary create(
            Trip trip,
            int version,
            OptimizationAlgorithm algorithm,
            long totalDistanceMeters,
            int estimatedTravelMinutes
    ) {
        return new Itinerary(
                trip, version, algorithm, totalDistanceMeters, estimatedTravelMinutes,
                0, 0, 0, 0, 0, null, false
        );
    }

    public static Itinerary create(
            Trip trip,
            int version,
            OptimizationAlgorithm algorithm,
            long totalDistanceMeters,
            int estimatedTravelMinutes,
            int optimizationScore,
            int totalStayMinutes,
            int totalWaitingMinutes,
            long returnTravelDistanceMeters,
            int returnTravelMinutes,
            LocalTime returnArrivalTime,
            boolean returnedToAccommodation
    ) {
        return new Itinerary(
                trip, version, algorithm, totalDistanceMeters, estimatedTravelMinutes,
                optimizationScore, totalStayMinutes, totalWaitingMinutes,
                returnTravelDistanceMeters, returnTravelMinutes, returnArrivalTime,
                returnedToAccommodation
        );
    }

    public void addItem(
            Place place,
            int sequence,
            long travelDistanceMeters,
            int estimatedTravelMinutes
    ) {
        items.add(new ItineraryItem(
                this, place, sequence, travelDistanceMeters, estimatedTravelMinutes
        ));
    }

    public void addItem(
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
        items.add(new ItineraryItem(
                this, place, sequence, travelDistanceMeters, estimatedTravelMinutes,
                visitDate, arrivalTime, startTime, endTime,
                waitingMinutes, stayMinutes, priority, mustVisit
        ));
    }

    public void addExclusion(
            Place place,
            int priority,
            ExclusionReason reason
    ) {
        exclusions.add(new ItineraryExclusion(this, place, priority, reason));
    }

    public Long getId() {
        return id;
    }

    public Trip getTrip() {
        return trip;
    }

    public int getVersion() {
        return version;
    }

    public OptimizationAlgorithm getAlgorithm() {
        return algorithm;
    }

    public long getTotalDistanceMeters() {
        return totalDistanceMeters;
    }

    public int getEstimatedTravelMinutes() {
        return estimatedTravelMinutes;
    }

    public int getOptimizationScore() {
        return optimizationScore;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<ItineraryItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public Set<ItineraryExclusion> getExclusions() {
        return Collections.unmodifiableSet(exclusions);
    }
}
