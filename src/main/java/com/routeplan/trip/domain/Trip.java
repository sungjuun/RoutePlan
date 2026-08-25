package com.routeplan.trip.domain;

import com.routeplan.user.domain.User;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "trips")
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "accommodation_name", nullable = false, length = 100)
    private String accommodationName;

    @Column(name = "accommodation_latitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal accommodationLatitude;

    @Column(name = "accommodation_longitude", nullable = false, precision = 10, scale = 6)
    private BigDecimal accommodationLongitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_mode", nullable = false, length = 30)
    private TransportMode transportMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Trip() {
    }

    private Trip(
            User user,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            String accommodationName,
            BigDecimal accommodationLatitude,
            BigDecimal accommodationLongitude,
            TransportMode transportMode
    ) {
        this.user = requireUser(user);
        applyPlan(name, startDate, endDate, accommodationName,
                accommodationLatitude, accommodationLongitude, transportMode);
        this.status = TripStatus.DRAFT;
    }

    public static Trip create(
            User user,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            String accommodationName,
            BigDecimal accommodationLatitude,
            BigDecimal accommodationLongitude,
            TransportMode transportMode
    ) {
        return new Trip(user, name, startDate, endDate, accommodationName,
                accommodationLatitude, accommodationLongitude, transportMode);
    }

    public void update(
            String name,
            LocalDate startDate,
            LocalDate endDate,
            String accommodationName,
            BigDecimal accommodationLatitude,
            BigDecimal accommodationLongitude,
            TransportMode transportMode
    ) {
        applyPlan(name, startDate, endDate, accommodationName,
                accommodationLatitude, accommodationLongitude, transportMode);
        markDraft();
    }

    private void applyPlan(
            String name,
            LocalDate startDate,
            LocalDate endDate,
            String accommodationName,
            BigDecimal accommodationLatitude,
            BigDecimal accommodationLongitude,
            TransportMode transportMode
    ) {
        this.name = requireText(name, "여행 이름", 100);
        validateSingleDay(startDate, endDate);
        this.startDate = startDate;
        this.endDate = endDate;
        this.accommodationName = requireText(accommodationName, "숙소 이름", 100);
        this.accommodationLatitude = requireCoordinate(
                accommodationLatitude, "숙소 위도", new BigDecimal("-90"), new BigDecimal("90")
        );
        this.accommodationLongitude = requireCoordinate(
                accommodationLongitude, "숙소 경도", new BigDecimal("-180"), new BigDecimal("180")
        );
        if (transportMode == null) {
            throw new IllegalArgumentException("이동수단은 필수입니다.");
        }
        this.transportMode = transportMode;
    }

    public void markDraft() {
        this.status = TripStatus.DRAFT;
    }

    public void markOptimized() {
        this.status = TripStatus.OPTIMIZED;
    }

    private static User requireUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("사용자는 필수입니다.");
        }
        return user;
    }

    private static void validateSingleDay(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("여행 날짜는 필수입니다.");
        }
        if (!startDate.equals(endDate)) {
            throw new IllegalArgumentException("V1에서는 하루짜리 여행만 지원합니다.");
        }
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "은 필수입니다.");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + "은 " + maxLength + "자를 초과할 수 없습니다.");
        }
        return normalized;
    }

    private static BigDecimal requireCoordinate(
            BigDecimal value,
            String field,
            BigDecimal minimum,
            BigDecimal maximum
    ) {
        if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(field + " 값이 올바르지 않습니다.");
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getName() {
        return name;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getAccommodationName() {
        return accommodationName;
    }

    public BigDecimal getAccommodationLatitude() {
        return accommodationLatitude;
    }

    public BigDecimal getAccommodationLongitude() {
        return accommodationLongitude;
    }

    public TransportMode getTransportMode() {
        return transportMode;
    }

    public TripStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
