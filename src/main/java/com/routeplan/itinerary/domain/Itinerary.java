package com.routeplan.itinerary.domain;

import com.routeplan.place.domain.Place;
import com.routeplan.optimization.domain.OptimizationAlgorithm;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "itinerary", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private final List<ItineraryItem> items = new ArrayList<>();

    protected Itinerary() {
    }

    private Itinerary(
            Trip trip,
            int version,
            OptimizationAlgorithm algorithm,
            long totalDistanceMeters,
            int estimatedTravelMinutes
    ) {
        if (trip == null || algorithm == null) {
            throw new IllegalArgumentException("여행과 알고리즘은 필수입니다.");
        }
        if (version <= 0 || totalDistanceMeters < 0 || estimatedTravelMinutes < 0) {
            throw new IllegalArgumentException("일정 버전과 이동비용이 올바르지 않습니다.");
        }
        this.trip = trip;
        this.version = version;
        this.algorithm = algorithm;
        this.totalDistanceMeters = totalDistanceMeters;
        this.estimatedTravelMinutes = estimatedTravelMinutes;
    }

    public static Itinerary create(
            Trip trip,
            int version,
            OptimizationAlgorithm algorithm,
            long totalDistanceMeters,
            int estimatedTravelMinutes
    ) {
        return new Itinerary(trip, version, algorithm, totalDistanceMeters, estimatedTravelMinutes);
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<ItineraryItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
