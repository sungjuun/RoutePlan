package com.routeplan.place.domain;

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
import java.time.DayOfWeek;
import java.time.LocalTime;

@Entity
@Table(
        name = "place_opening_hours",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_place_opening_hours_place_day",
                columnNames = {"place_id", "day_of_week"}
        )
)
public class PlaceOpeningHour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "open_time")
    private LocalTime openTime;

    @Column(name = "close_time")
    private LocalTime closeTime;

    @Column(nullable = false)
    private boolean closed;

    protected PlaceOpeningHour() {
    }

    private PlaceOpeningHour(
            Place place,
            DayOfWeek dayOfWeek,
            LocalTime openTime,
            LocalTime closeTime,
            boolean closed
    ) {
        if (place == null || dayOfWeek == null) {
            throw new IllegalArgumentException("장소와 요일은 필수입니다.");
        }
        this.place = place;
        this.dayOfWeek = dayOfWeek;
        update(openTime, closeTime, closed);
    }

    public static PlaceOpeningHour create(
            Place place,
            DayOfWeek dayOfWeek,
            LocalTime openTime,
            LocalTime closeTime,
            boolean closed
    ) {
        return new PlaceOpeningHour(place, dayOfWeek, openTime, closeTime, closed);
    }

    public void update(LocalTime openTime, LocalTime closeTime, boolean closed) {
        if (closed) {
            if (openTime != null || closeTime != null) {
                throw new IllegalArgumentException("휴무일에는 영업 시작·종료시간을 입력할 수 없습니다.");
            }
            this.openTime = null;
            this.closeTime = null;
            this.closed = true;
            return;
        }
        if (openTime == null || closeTime == null || !closeTime.isAfter(openTime)) {
            throw new IllegalArgumentException("영업일은 종료시간이 시작시간보다 늦어야 합니다.");
        }
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.closed = false;
    }

    public Long getId() {
        return id;
    }

    public Place getPlace() {
        return place;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getOpenTime() {
        return openTime;
    }

    public LocalTime getCloseTime() {
        return closeTime;
    }

    public boolean isClosed() {
        return closed;
    }
}
