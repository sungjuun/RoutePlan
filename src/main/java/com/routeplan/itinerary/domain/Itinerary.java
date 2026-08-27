package com.routeplan.itinerary.domain;

import com.routeplan.budget.domain.BudgetCurrency;
import com.routeplan.budget.domain.BudgetSettings;
import com.routeplan.place.domain.Place;
import com.routeplan.optimization.domain.OptimizationAlgorithm;
import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.constraint.ExclusionReason;
import com.routeplan.optimization.route.RouteDataType;
import com.routeplan.trip.domain.Trip;
import com.routeplan.weather.domain.WeatherCondition;
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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;
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
    @Column(name = "generation_type", nullable = false, length = 30)
    private ItineraryGenerationType generationType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_itinerary_id")
    private Itinerary parentItinerary;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_reason", length = 30)
    private ItineraryChangeReason changeReason;

    @Column(name = "change_reason_detail", length = 500)
    private String changeReasonDetail;

    @Column(name = "reoptimization_start_time")
    private LocalTime reoptimizationStartTime;

    @Column(name = "reoptimization_start_date")
    private LocalDate reoptimizationStartDate;

    @Column(name = "reoptimization_start_latitude", precision = 9, scale = 6)
    private BigDecimal reoptimizationStartLatitude;

    @Column(name = "reoptimization_start_longitude", precision = 10, scale = 6)
    private BigDecimal reoptimizationStartLongitude;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "route_data_type", nullable = false, length = 40)
    private RouteDataType routeDataType;

    @Column(name = "route_provider_call_count", nullable = false)
    private int routeProviderCallCount;

    @Column(name = "route_matrix_element_count", nullable = false)
    private int routeMatrixElementCount;

    @Column(name = "route_matrix_build_millis", nullable = false)
    private long routeMatrixBuildMillis;

    @Column(name = "route_cache_enabled", nullable = false)
    private boolean routeCacheEnabled;

    @Column(name = "route_cache_hit_count", nullable = false)
    private int routeCacheHitCount;

    @Column(name = "route_cache_miss_count", nullable = false)
    private int routeCacheMissCount;

    @Column(name = "route_cache_failure_count", nullable = false)
    private int routeCacheFailureCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "budget_currency", nullable = false, length = 3)
    private BudgetCurrency budgetCurrency = BudgetCurrency.KRW;

    @Column(name = "budget_limit_minor")
    private Long budgetLimitMinor;

    @Column(name = "fixed_cost_minor", nullable = false)
    private long fixedCostMinor;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "itinerary", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private final List<ItineraryItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "itinerary", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayNumber ASC")
    private final Set<ItineraryDay> days = new LinkedHashSet<>();

    @OneToMany(mappedBy = "itinerary", cascade = CascadeType.ALL, orphanRemoval = true)
    private final Set<ItineraryExclusion> exclusions = new LinkedHashSet<>();

    protected Itinerary() {
    }

    public BudgetSettings getBudgetSettings() {
        return new BudgetSettings(budgetCurrency, budgetLimitMinor, fixedCostMinor);
    }

    public void recordBudget(BudgetSettings settings, Map<Long, Long> costsByPlaceId) {
        if (id != null) throw new IllegalStateException("저장된 일정의 비용 Snapshot은 변경할 수 없습니다.");
        this.budgetCurrency = settings.currency();
        this.budgetLimitMinor = settings.limitMinor();
        this.fixedCostMinor = settings.fixedCostMinor();
        items.forEach(item -> item.recordEstimatedCost(costsByPlaceId.get(item.getPlace().getId())));
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
            boolean returnedToAccommodation,
            RouteDataType routeDataType,
            int routeProviderCallCount,
            int routeMatrixElementCount,
            long routeMatrixBuildMillis,
            boolean routeCacheEnabled,
            int routeCacheHitCount,
            int routeCacheMissCount,
            int routeCacheFailureCount
    ) {
        if (trip == null || algorithm == null) {
            throw new IllegalArgumentException("여행과 알고리즘은 필수입니다.");
        }
        if (version <= 0 || totalDistanceMeters < 0 || estimatedTravelMinutes < 0
                || optimizationScore < 0 || totalStayMinutes < 0 || totalWaitingMinutes < 0
                || returnTravelDistanceMeters < 0 || returnTravelMinutes < 0
                || routeProviderCallCount < 0 || routeMatrixElementCount < 0
                || routeMatrixBuildMillis < 0 || routeCacheHitCount < 0
                || routeCacheMissCount < 0 || routeCacheFailureCount < 0) {
            throw new IllegalArgumentException("일정 버전과 이동비용이 올바르지 않습니다.");
        }
        if (routeDataType == null) {
            throw new IllegalArgumentException("경로 데이터 유형은 필수입니다.");
        }
        if (!routeCacheEnabled
                && (routeCacheHitCount != 0 || routeCacheMissCount != 0
                || routeCacheFailureCount != 0)) {
            throw new IllegalArgumentException("비활성 Route Cache에는 측정값이 있을 수 없습니다.");
        }
        this.trip = trip;
        this.version = version;
        this.generationType = ItineraryGenerationType.INITIAL_OPTIMIZATION;
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
        this.routeDataType = routeDataType;
        this.routeProviderCallCount = routeProviderCallCount;
        this.routeMatrixElementCount = routeMatrixElementCount;
        this.routeMatrixBuildMillis = routeMatrixBuildMillis;
        this.routeCacheEnabled = routeCacheEnabled;
        this.routeCacheHitCount = routeCacheHitCount;
        this.routeCacheMissCount = routeCacheMissCount;
        this.routeCacheFailureCount = routeCacheFailureCount;
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
                0, 0, 0, 0, 0, null, false,
                RouteDataType.STRAIGHT_LINE_ESTIMATE, 0, 0, 0,
                false, 0, 0, 0
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
            boolean returnedToAccommodation,
            RouteDataType routeDataType,
            int routeProviderCallCount,
            int routeMatrixElementCount,
            long routeMatrixBuildMillis,
            boolean routeCacheEnabled,
            int routeCacheHitCount,
            int routeCacheMissCount,
            int routeCacheFailureCount
    ) {
        return new Itinerary(
                trip, version, algorithm, totalDistanceMeters, estimatedTravelMinutes,
                optimizationScore, totalStayMinutes, totalWaitingMinutes,
                returnTravelDistanceMeters, returnTravelMinutes, returnArrivalTime,
                returnedToAccommodation, routeDataType, routeProviderCallCount,
                routeMatrixElementCount, routeMatrixBuildMillis, routeCacheEnabled,
                routeCacheHitCount, routeCacheMissCount, routeCacheFailureCount
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

    public void addCompletedItem(
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
        addCompletedItem(
                place, sequence, travelDistanceMeters, estimatedTravelMinutes,
                visitDate, arrivalTime, startTime, endTime,
                waitingMinutes, stayMinutes, priority, mustVisit, 0
        );
    }

    public void addCompletedItem(
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
            boolean mustVisit,
            int weatherScoreAdjustment
    ) {
        items.add(new ItineraryItem(
                this, place, sequence, travelDistanceMeters, estimatedTravelMinutes,
                visitDate, arrivalTime, startTime, endTime,
                waitingMinutes, stayMinutes, priority, mustVisit, weatherScoreAdjustment,
                ItineraryItemStatus.COMPLETED
        ));
    }

    public void markReoptimized(
            Itinerary parentItinerary,
            ItineraryChangeReason changeReason,
            String changeReasonDetail,
            LocalDate reoptimizationStartDate,
            LocalTime reoptimizationStartTime,
            BigDecimal reoptimizationStartLatitude,
            BigDecimal reoptimizationStartLongitude
    ) {
        if (parentItinerary == null || changeReason == null || reoptimizationStartDate == null
                || reoptimizationStartTime == null
                || reoptimizationStartLatitude == null || reoptimizationStartLongitude == null) {
            throw new IllegalArgumentException("재최적화 계보와 현재 날짜·위치·시각은 필수입니다.");
        }
        if (!parentItinerary.getTrip().getId().equals(trip.getId())) {
            throw new IllegalArgumentException("부모 일정은 같은 여행에 속해야 합니다.");
        }
        if (parentItinerary.getVersion() + 1 != version) {
            throw new IllegalArgumentException("재최적화 버전은 부모 일정의 다음 버전이어야 합니다.");
        }
        if (reoptimizationStartDate.isBefore(trip.getStartDate())
                || reoptimizationStartDate.isAfter(trip.getEndDate())) {
            throw new IllegalArgumentException("재최적화 시작 날짜는 여행 기간 안에 있어야 합니다.");
        }
        Location.of(reoptimizationStartLatitude, reoptimizationStartLongitude);
        this.generationType = ItineraryGenerationType.REOPTIMIZATION;
        this.parentItinerary = parentItinerary;
        this.changeReason = changeReason;
        this.changeReasonDetail = normalizeDetail(changeReasonDetail);
        this.reoptimizationStartDate = reoptimizationStartDate;
        this.reoptimizationStartTime = reoptimizationStartTime;
        this.reoptimizationStartLatitude = reoptimizationStartLatitude;
        this.reoptimizationStartLongitude = reoptimizationStartLongitude;
    }

    private String normalizeDetail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > 500) {
            throw new IllegalArgumentException("일정 변경 상세 사유는 500자를 초과할 수 없습니다.");
        }
        return normalized;
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
        addItem(
                place, sequence, travelDistanceMeters, estimatedTravelMinutes,
                visitDate, arrivalTime, startTime, endTime,
                waitingMinutes, stayMinutes, priority, mustVisit, 0
        );
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
            boolean mustVisit,
            int weatherScoreAdjustment
    ) {
        items.add(new ItineraryItem(
                this, place, sequence, travelDistanceMeters, estimatedTravelMinutes,
                visitDate, arrivalTime, startTime, endTime,
                waitingMinutes, stayMinutes, priority, mustVisit, weatherScoreAdjustment
        ));
    }

    public void addExclusion(
            Place place,
            int priority,
            ExclusionReason reason
    ) {
        exclusions.add(new ItineraryExclusion(this, place, priority, reason));
    }

    public void addDay(
            LocalDate visitDate,
            int dayNumber,
            long totalDistanceMeters,
            int estimatedTravelMinutes,
            int totalStayMinutes,
            int totalWaitingMinutes,
            long returnTravelDistanceMeters,
            int returnTravelMinutes,
            LocalTime returnArrivalTime,
            boolean returnedToAccommodation
    ) {
        addDay(
                visitDate, dayNumber, totalDistanceMeters, estimatedTravelMinutes,
                totalStayMinutes, totalWaitingMinutes, returnTravelDistanceMeters,
                returnTravelMinutes, returnArrivalTime, returnedToAccommodation,
                WeatherCondition.UNKNOWN, 0
        );
    }

    public void addDay(
            LocalDate visitDate,
            int dayNumber,
            long totalDistanceMeters,
            int estimatedTravelMinutes,
            int totalStayMinutes,
            int totalWaitingMinutes,
            long returnTravelDistanceMeters,
            int returnTravelMinutes,
            LocalTime returnArrivalTime,
            boolean returnedToAccommodation,
            WeatherCondition weatherCondition,
            int precipitationProbability
    ) {
        days.add(new ItineraryDay(
                this, visitDate, dayNumber, totalDistanceMeters, estimatedTravelMinutes,
                totalStayMinutes, totalWaitingMinutes, returnTravelDistanceMeters,
                returnTravelMinutes, returnArrivalTime, returnedToAccommodation,
                weatherCondition, precipitationProbability
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

    public ItineraryGenerationType getGenerationType() {
        return generationType;
    }

    public Itinerary getParentItinerary() {
        return parentItinerary;
    }

    public ItineraryChangeReason getChangeReason() {
        return changeReason;
    }

    public String getChangeReasonDetail() {
        return changeReasonDetail;
    }

    public LocalTime getReoptimizationStartTime() {
        return reoptimizationStartTime;
    }

    public LocalDate getReoptimizationStartDate() {
        return reoptimizationStartDate;
    }

    public BigDecimal getReoptimizationStartLatitude() {
        return reoptimizationStartLatitude;
    }

    public BigDecimal getReoptimizationStartLongitude() {
        return reoptimizationStartLongitude;
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

    public RouteDataType getRouteDataType() {
        return routeDataType;
    }

    public int getRouteProviderCallCount() {
        return routeProviderCallCount;
    }

    public int getRouteMatrixElementCount() {
        return routeMatrixElementCount;
    }

    public long getRouteMatrixBuildMillis() {
        return routeMatrixBuildMillis;
    }

    public boolean isRouteCacheEnabled() {
        return routeCacheEnabled;
    }

    public int getRouteCacheHitCount() {
        return routeCacheHitCount;
    }

    public int getRouteCacheMissCount() {
        return routeCacheMissCount;
    }

    public int getRouteCacheFailureCount() {
        return routeCacheFailureCount;
    }

    public double getRouteCacheHitRatio() {
        int lookups = routeCacheHitCount + routeCacheMissCount;
        return lookups == 0 ? 0.0 : (double) routeCacheHitCount / lookups;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<ItineraryItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public List<ItineraryDay> getDays() {
        return days.stream()
                .sorted(java.util.Comparator.comparingInt(ItineraryDay::getDayNumber))
                .toList();
    }

    public Set<ItineraryExclusion> getExclusions() {
        return Collections.unmodifiableSet(exclusions);
    }
}
