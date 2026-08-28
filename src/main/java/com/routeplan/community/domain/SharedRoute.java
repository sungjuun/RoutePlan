package com.routeplan.community.domain;

import com.routeplan.itinerary.domain.Itinerary;
import com.routeplan.itinerary.domain.ItineraryItem;
import com.routeplan.optimization.domain.OptimizationAlgorithm;
import com.routeplan.trip.domain.TransportMode;
import com.routeplan.trip.domain.Trip;
import com.routeplan.trip.domain.TripPace;
import com.routeplan.user.domain.User;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "shared_routes")
public class SharedRoute {
    @Column(name = "moderated_hidden", nullable = false)
    private boolean moderatedHidden;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_trip_id")
    private Trip sourceTrip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_itinerary_id", unique = true)
    private Itinerary sourceItinerary;

    @Column(name = "source_itinerary_version", nullable = false)
    private int sourceItineraryVersion;

    @Column(name = "source_trip_name", nullable = false, length = 100)
    private String sourceTripName;

    @Column(name = "source_start_date", nullable = false)
    private LocalDate sourceStartDate;

    @Column(name = "daily_start_time", nullable = false)
    private LocalTime dailyStartTime;

    @Column(name = "daily_end_time", nullable = false)
    private LocalTime dailyEndTime;

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
    private TripPace pace;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private OptimizationAlgorithm algorithm;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 100)
    private String region;

    @Column(name = "travel_days", nullable = false)
    private int travelDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SharedRouteVisibility visibility;

    @Column(name = "place_count", nullable = false)
    private int placeCount;

    @Column(name = "place_preview", nullable = false, length = 500)
    private String placePreview;

    @Column(name = "total_distance_meters", nullable = false)
    private long totalDistanceMeters;

    @Column(name = "estimated_travel_minutes", nullable = false)
    private int estimatedTravelMinutes;

    @Column(name = "optimization_score", nullable = false)
    private int optimizationScore;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "copy_count", nullable = false)
    private long copyCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @CreationTimestamp
    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "sharedRoute", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private final List<SharedRouteItem> items = new ArrayList<>();

    protected SharedRoute() {
    }

    private SharedRoute(
            User owner,
            Itinerary sourceItinerary,
            String title,
            String description,
            String region,
            SharedRouteVisibility visibility
    ) {
        if (owner == null || sourceItinerary == null) {
            throw new IllegalArgumentException("공개 루트의 작성자와 원본 일정은 필수입니다.");
        }
        Trip trip = sourceItinerary.getTrip();
        this.owner = owner;
        this.sourceTrip = trip;
        this.sourceItinerary = sourceItinerary;
        this.sourceItineraryVersion = sourceItinerary.getVersion();
        this.sourceTripName = trip.getName();
        this.sourceStartDate = trip.getStartDate();
        this.dailyStartTime = trip.getDailyStartTime();
        this.dailyEndTime = trip.getDailyEndTime();
        this.accommodationName = trip.getAccommodationName();
        this.accommodationLatitude = trip.getAccommodationLatitude();
        this.accommodationLongitude = trip.getAccommodationLongitude();
        this.transportMode = trip.getTransportMode();
        this.pace = trip.getPace();
        this.algorithm = sourceItinerary.getAlgorithm();
        this.title = requireText(title, "공개 루트 제목", 150);
        this.description = normalizeNullable(description, "공개 루트 설명", 1000);
        this.region = requireText(region, "지역", 100);
        this.travelDays = Math.toIntExact(
                ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1
        );
        this.visibility = visibility == null ? SharedRouteVisibility.PUBLIC : visibility;
        this.totalDistanceMeters = sourceItinerary.getTotalDistanceMeters();
        this.estimatedTravelMinutes = sourceItinerary.getEstimatedTravelMinutes();
        this.optimizationScore = sourceItinerary.getOptimizationScore();
    }

    public static SharedRoute publish(
            User owner,
            Itinerary sourceItinerary,
            String title,
            String description,
            String region,
            SharedRouteVisibility visibility
    ) {
        SharedRoute route = new SharedRoute(
                owner, sourceItinerary, title, description, region, visibility
        );
        if (sourceItinerary.getItems().isEmpty()) {
            throw new IllegalArgumentException("방문 장소가 없는 일정은 공개할 수 없습니다.");
        }
        sourceItinerary.getItems().forEach(item -> route.items.add(
                SharedRouteItem.snapshot(route, item)
        ));
        route.placeCount = route.items.size();
        route.placePreview = route.items.stream()
                .limit(4)
                .map(SharedRouteItem::getPlaceName)
                .reduce((left, right) -> left + " · " + right)
                .orElseThrow();
        return route;
    }

    public void increaseViewCount() {
        viewCount = increment(viewCount);
    }

    public void increaseCopyCount() {
        copyCount = increment(copyCount);
    }

    public void increaseLikeCount() {
        likeCount = increment(likeCount);
    }

    public void decreaseLikeCount() {
        if (likeCount <= 0) {
            throw new IllegalStateException("좋아요 수는 0보다 작을 수 없습니다.");
        }
        likeCount--;
    }

    private long increment(long value) {
        if (value == Long.MAX_VALUE) {
            throw new IllegalStateException("카운터가 허용 범위를 초과했습니다.");
        }
        return value + 1;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "은 필수입니다.");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + "은 " + maxLength + "자를 초과할 수 없습니다.");
        }
        return normalized;
    }

    private static String normalizeNullable(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + "은 " + maxLength + "자를 초과할 수 없습니다.");
        }
        return normalized;
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public Trip getSourceTrip() {
        return sourceTrip;
    }

    public Itinerary getSourceItinerary() {
        return sourceItinerary;
    }

    public int getSourceItineraryVersion() {
        return sourceItineraryVersion;
    }

    public String getSourceTripName() {
        return sourceTripName;
    }

    public LocalDate getSourceStartDate() {
        return sourceStartDate;
    }

    public LocalTime getDailyStartTime() {
        return dailyStartTime;
    }

    public LocalTime getDailyEndTime() {
        return dailyEndTime;
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

    public TripPace getPace() {
        return pace;
    }

    public OptimizationAlgorithm getAlgorithm() {
        return algorithm;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getRegion() {
        return region;
    }

    public int getTravelDays() {
        return travelDays;
    }

    public SharedRouteVisibility getVisibility() {
        return visibility;
    }

    public int getPlaceCount() {
        return placeCount;
    }

    public String getPlacePreview() {
        return placePreview;
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

    public long getViewCount() {
        return viewCount;
    }

    public long getCopyCount() {
        return copyCount;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<SharedRouteItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
