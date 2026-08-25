package com.routeplan.itinerary.domain;

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
    }

    public Long getId() {
        return id;
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
}
