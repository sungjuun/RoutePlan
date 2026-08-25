package com.routeplan.trip.domain;

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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TripPlace() {
    }

    public TripPlace(Trip trip, Place place) {
        if (trip == null || place == null) {
            throw new IllegalArgumentException("여행과 장소는 필수입니다.");
        }
        this.trip = trip;
        this.place = place;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
