package com.routeplan.place.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "places")
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_place_id", length = 200, unique = true)
    private String externalPlaceId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal longitude;

    @Column(length = 50)
    private String category;

    @Column(name = "average_stay_minutes", nullable = false)
    private int averageStayMinutes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Place() {
    }

    private Place(
            String name,
            BigDecimal latitude,
            BigDecimal longitude,
            String category,
            int averageStayMinutes
    ) {
        this.name = requireText(name, "장소 이름", 150);
        this.latitude = requireCoordinate(latitude, "위도", new BigDecimal("-90"), new BigDecimal("90"));
        this.longitude = requireCoordinate(longitude, "경도", new BigDecimal("-180"), new BigDecimal("180"));
        this.category = normalizeNullable(category, 50);
        if (averageStayMinutes <= 0 || averageStayMinutes > 1_440) {
            throw new IllegalArgumentException("평균 체류시간은 1분 이상 1,440분 이하여야 합니다.");
        }
        this.averageStayMinutes = averageStayMinutes;
    }

    public static Place create(String name, BigDecimal latitude, BigDecimal longitude, String category) {
        return create(name, latitude, longitude, category, 60);
    }

    public static Place create(
            String name,
            BigDecimal latitude,
            BigDecimal longitude,
            String category,
            int averageStayMinutes
    ) {
        return new Place(name, latitude, longitude, category, averageStayMinutes);
    }

    public static Place createExternal(
            String externalPlaceId,
            String name,
            BigDecimal latitude,
            BigDecimal longitude,
            String category,
            int averageStayMinutes
    ) {
        Place place = new Place(name, latitude, longitude, category, averageStayMinutes);
        place.externalPlaceId = requireText(externalPlaceId, "외부 장소 ID", 200);
        return place;
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

    private static String normalizeNullable(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("카테고리는 " + maxLength + "자를 초과할 수 없습니다.");
        }
        return normalized;
    }

    public Long getId() {
        return id;
    }

    public String getExternalPlaceId() {
        return externalPlaceId;
    }

    public String getName() {
        return name;
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

    public int getAverageStayMinutes() {
        return averageStayMinutes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
