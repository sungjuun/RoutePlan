package com.routeplan.itinerary.domain;

import com.routeplan.optimization.constraint.ExclusionReason;
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

@Entity
@Table(
        name = "itinerary_exclusions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_itinerary_exclusions_itinerary_place",
                columnNames = {"itinerary_id", "place_id"}
        )
)
public class ItineraryExclusion {

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
    private int priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ExclusionReason reason;

    protected ItineraryExclusion() {
    }

    ItineraryExclusion(
            Itinerary itinerary,
            Place place,
            int priority,
            ExclusionReason reason
    ) {
        if (itinerary == null || place == null || reason == null) {
            throw new IllegalArgumentException("일정 제외 정보는 일정·장소·사유가 필수입니다.");
        }
        if (priority < 1 || priority > 100) {
            throw new IllegalArgumentException("장소 우선순위는 1 이상 100 이하여야 합니다.");
        }
        this.itinerary = itinerary;
        this.place = place;
        this.priority = priority;
        this.reason = reason;
    }

    public Place getPlace() {
        return place;
    }

    public int getPriority() {
        return priority;
    }

    public ExclusionReason getReason() {
        return reason;
    }
}
