package com.routeplan.itinerary.application;

import com.routeplan.budget.application.BudgetInput;
import com.routeplan.optimization.constraint.ScheduleBudget;
import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import com.routeplan.common.observability.RoutePlanMetrics;
import com.routeplan.integration.google.ExternalProviderException;
import com.routeplan.itinerary.domain.Itinerary;
import com.routeplan.itinerary.persistence.ItineraryRepository;
import com.routeplan.optimization.algorithm.ExactSearchOptimizationEngine;
import com.routeplan.optimization.algorithm.OptimizationEngineRegistry;
import com.routeplan.optimization.constraint.MultiDaySchedule;
import com.routeplan.optimization.constraint.MultiDaySchedulePlanner;
import com.routeplan.optimization.constraint.ScheduleCandidate;
import com.routeplan.optimization.constraint.ScheduleRequest;
import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.OptimizationAlgorithm;
import com.routeplan.optimization.domain.OptimizationRequest;
import com.routeplan.optimization.domain.OptimizationResult;
import com.routeplan.optimization.domain.VisitCandidate;
import com.routeplan.optimization.route.RouteMatrix;
import com.routeplan.optimization.route.RouteMatrixProvider;
import com.routeplan.place.domain.Place;
import com.routeplan.place.domain.PlaceOpeningHour;
import com.routeplan.place.persistence.PlaceOpeningHourRepository;
import com.routeplan.trip.domain.Trip;
import com.routeplan.trip.domain.TripPlace;
import com.routeplan.trip.persistence.TripPlaceRepository;
import com.routeplan.trip.persistence.TripRepository;
import com.routeplan.weather.domain.TripWeatherForecast;
import com.routeplan.weather.domain.WeatherSnapshot;
import com.routeplan.weather.domain.WeatherSuitabilityPolicy;
import com.routeplan.weather.persistence.TripWeatherForecastRepository;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ItineraryOptimizationService {

    private final TripRepository tripRepository;
    private final TripPlaceRepository tripPlaceRepository;
    private final ItineraryRepository itineraryRepository;
    private final OptimizationEngineRegistry optimizationEngineRegistry;
    private final PlaceOpeningHourRepository openingHourRepository;
    private final MultiDaySchedulePlanner schedulePlanner;
    private final RouteMatrixProvider routeMatrixProvider;
    private final TripWeatherForecastRepository weatherRepository;
    private final WeatherSuitabilityPolicy weatherPolicy;
    private final RoutePlanMetrics metrics;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;
    @org.springframework.beans.factory.annotation.Autowired
    private com.routeplan.place.search.LiveOpeningHours liveHours;
    @org.springframework.beans.factory.annotation.Autowired
    private com.routeplan.optimization.constraint.DepartureAwareScheduleRefiner departureRefiner;
    @org.springframework.beans.factory.annotation.Autowired
    private com.routeplan.optimization.constraint.TimeDependentGlobalScheduleOptimizer timeDependentOptimizer;

    public ItineraryOptimizationService(
            TripRepository tripRepository,
            TripPlaceRepository tripPlaceRepository,
            ItineraryRepository itineraryRepository,
            OptimizationEngineRegistry optimizationEngineRegistry,
            PlaceOpeningHourRepository openingHourRepository,
            MultiDaySchedulePlanner schedulePlanner,
            RouteMatrixProvider routeMatrixProvider,
            TripWeatherForecastRepository weatherRepository,
            WeatherSuitabilityPolicy weatherPolicy,
            RoutePlanMetrics metrics,
            PlatformTransactionManager transactionManager
    ) {
        this.tripRepository = tripRepository;
        this.tripPlaceRepository = tripPlaceRepository;
        this.itineraryRepository = itineraryRepository;
        this.optimizationEngineRegistry = optimizationEngineRegistry;
        this.openingHourRepository = openingHourRepository;
        this.schedulePlanner = schedulePlanner;
        this.routeMatrixProvider = routeMatrixProvider;
        this.weatherRepository = weatherRepository;
        this.weatherPolicy = weatherPolicy;
        this.metrics = metrics;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    public ItineraryView optimize(Long tripId, OptimizationAlgorithm algorithm) {
        var sample = metrics.startGeneration();
        try {
            OptimizationSnapshot snapshot = Objects.requireNonNull(
                    readTransaction.execute(status -> loadSnapshot(tripId, algorithm))
            );
            ScheduleBudget budget = snapshot.budget().remaining(0, false);
            budget.validate(snapshot.dailyCandidates().getFirst().candidates());
            var matrices = routeMatrixProvider.buildForDates(
                    locations(snapshot.optimizationRequest()),
                    snapshot.optimizationRequest().transportMode(),
                    snapshot.dailyCandidates().stream().map(DailyCandidates::visitDate).toList(),
                    snapshot.dailyStartTime(), snapshot.dailyStartTime(), snapshot.timeZoneId()
            );
            RouteMatrix baseMatrix = RouteMatrix.summarize(matrices.values());
            OptimizationResult result = optimizationEngineRegistry.get(algorithm)
                    .optimize(snapshot.optimizationRequest(), baseMatrix);
            var live = liveHours.apply(tripId, snapshot.toScheduleRequests(result));
            MultiDaySchedule initialSchedule = schedulePlanner.planByDate(
                    live.requests(),
                    matrices::get,
                    budget
            );
            var global = timeDependentOptimizer.optimize(
                    initialSchedule, live.requests(), budget, snapshot.timeZoneId(), baseMatrix.dataType());
            MultiDaySchedule finalSchedule;
            RouteMatrix measuredRouteMatrix = global.matrixMeasurement() == null
                    ? baseMatrix
                    : baseMatrix.combineMeasurements(global.matrixMeasurement());
            List<String> finalWarnings = new ArrayList<>(global.warnings());
            if (global.applied()) {
                finalSchedule = global.schedule();
            } else {
                var refined = departureRefiner.refine(
                        initialSchedule, live.requests(), snapshot.timeZoneId(), baseMatrix.dataType());
                finalSchedule = refined.schedule();
                measuredRouteMatrix = measuredRouteMatrix.withAdditionalElements(refined.calls(), refined.millis());
                finalWarnings.addAll(refined.warnings());
            }
            RouteMatrix routeMatrix = measuredRouteMatrix;
            metrics.recordRouteMatrix(routeMatrix);
            List<String> warnings = new ArrayList<>(live.warnings());
            warnings.addAll(finalWarnings);
            ItineraryView itinerary = Objects.requireNonNull(writeTransaction.execute(status ->
                    saveIfUnchanged(snapshot, result, finalSchedule, routeMatrix, warnings)
            ));
            metrics.recordGeneration(
                    sample,
                    RoutePlanMetrics.GenerationType.OPTIMIZATION,
                    algorithm,
                    RoutePlanMetrics.Outcome.SUCCESS
            );
            return itinerary;
        } catch (ExternalProviderException exception) {
            metrics.recordRouteApiFailure(exception.failure());
            recordFailure(sample, algorithm);
            throw exception;
        } catch (RuntimeException exception) {
            recordFailure(sample, algorithm);
            throw exception;
        }
    }

    private void recordFailure(
            Timer.Sample sample,
            OptimizationAlgorithm algorithm
    ) {
        metrics.recordGeneration(
                sample,
                RoutePlanMetrics.GenerationType.OPTIMIZATION,
                algorithm,
                RoutePlanMetrics.Outcome.FAILURE
        );
    }

    private OptimizationSnapshot loadSnapshot(Long tripId, OptimizationAlgorithm algorithm) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_NOT_FOUND));
        List<TripPlace> tripPlaces = tripPlaceRepository.findAllByTripIdOrderByIdAsc(tripId);
        validateCandidates(algorithm, tripPlaces);
        return buildSnapshot(trip, tripPlaces);
    }

    private ItineraryView saveIfUnchanged(
            OptimizationSnapshot snapshot,
            OptimizationResult result,
            MultiDaySchedule schedule,
            RouteMatrix routeMatrix, List<String> warnings
    ) {
        Trip trip = tripRepository.findByIdForUpdate(snapshot.tripId())
                .orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_NOT_FOUND));
        List<TripPlace> currentTripPlaces = tripPlaceRepository
                .findAllByTripIdOrderByIdAsc(snapshot.tripId());
        if (currentTripPlaces.isEmpty()) {
            throw new RoutePlanException(ErrorCode.OPTIMIZATION_INPUT_CHANGED);
        }
        OptimizationSnapshot current = buildSnapshot(trip, currentTripPlaces);
        if (!snapshot.hasSameInput(current)) {
            throw new RoutePlanException(ErrorCode.OPTIMIZATION_INPUT_CHANGED);
        }

        Itinerary itinerary = Itinerary.create(
                trip,
                itineraryRepository.findMaxVersionByTripId(snapshot.tripId()) + 1,
                result.algorithm(),
                schedule.totalDistanceMeters(),
                schedule.totalTravelMinutes(),
                schedule.optimizationScore(),
                schedule.totalStayMinutes(),
                schedule.totalWaitingMinutes(),
                schedule.returnTravelDistanceMeters(),
                schedule.returnTravelMinutes(),
                schedule.returnArrivalTime(),
                true,
                routeMatrix.dataType(),
                routeMatrix.providerCallCount(),
                routeMatrix.elementCount(),
                routeMatrix.buildMillis(),
                routeMatrix.cacheEnabled(),
                routeMatrix.cacheHitCount(),
                routeMatrix.cacheMissCount(),
                routeMatrix.cacheFailureCount()
        );
        schedule.days().forEach(day -> itinerary.addDay(
                day.visitDate(),
                day.dayNumber(),
                day.totalDistanceMeters(),
                day.totalTravelMinutes(),
                day.totalStayMinutes(),
                day.totalWaitingMinutes(),
                day.returnTravelDistanceMeters(),
                day.returnTravelMinutes(),
                day.returnArrivalTime(),
                true,
                snapshot.weatherFor(day.visitDate()).condition(),
                snapshot.weatherFor(day.visitDate()).precipitationProbability()
        ));

        Map<Long, Place> placesById = currentTripPlaces.stream()
                .map(TripPlace::getPlace)
                .collect(Collectors.toMap(Place::getId, Function.identity()));
        schedule.visits().forEach(visit -> itinerary.addItem(
                placesById.get(visit.placeId()),
                visit.sequence(),
                visit.travelDistanceMeters(),
                visit.travelMinutes(),
                visit.visitDate(),
                visit.arrivalTime(),
                visit.startTime(),
                visit.endTime(),
                visit.waitingMinutes(),
                visit.stayMinutes(),
                visit.priority(),
                visit.mustVisit(),
                visit.weatherScoreAdjustment()
        ));
        schedule.exclusions().forEach(exclusion -> itinerary.addExclusion(
                placesById.get(exclusion.placeId()),
                exclusion.priority(),
                exclusion.reason()
        ));

        itinerary.recordBudget(snapshot.budget().settings(), snapshot.budget().costsByPlaceId());
        itinerary.recordTimeZone(snapshot.timeZoneId());
        itinerary.recordLiveData(warnings, snapshot.optimizationRequest().transportMode());
        trip.markOptimized();
        return ItineraryView.from(itineraryRepository.saveAndFlush(itinerary));
    }

    private void validateCandidates(OptimizationAlgorithm algorithm, List<TripPlace> tripPlaces) {
        if (tripPlaces.isEmpty()) {
            throw new RoutePlanException(ErrorCode.TRIP_HAS_NO_PLACES);
        }
        if (algorithm == OptimizationAlgorithm.EXACT_SEARCH
                && tripPlaces.size() > ExactSearchOptimizationEngine.MAX_CANDIDATES) {
            throw new RoutePlanException(ErrorCode.EXACT_SEARCH_LIMIT_EXCEEDED);
        }
    }

    private OptimizationSnapshot buildSnapshot(Trip trip, List<TripPlace> tripPlaces) {
        Location accommodation = Location.of(
                trip.getAccommodationLatitude(),
                trip.getAccommodationLongitude()
        );
        List<VisitCandidate> visitCandidates = tripPlaces.stream()
                .map(tripPlace -> new VisitCandidate(
                        tripPlace.getId(),
                        tripPlace.getPlace().getId(),
                        Location.of(
                                tripPlace.getPlace().getLatitude(),
                                tripPlace.getPlace().getLongitude()
                        )
                ))
                .toList();
        OptimizationRequest optimizationRequest = new OptimizationRequest(
                accommodation,
                visitCandidates,
                trip.getTransportMode()
        );

        List<Long> placeIds = tripPlaces.stream()
                .map(tripPlace -> tripPlace.getPlace().getId())
                .toList();
        Map<OpeningHourKey, PlaceOpeningHour> openingHours = openingHourRepository
                .findAllByPlaceIdIn(placeIds)
                .stream()
                .collect(Collectors.toMap(
                        openingHour -> new OpeningHourKey(
                                openingHour.getPlace().getId(), openingHour.getDayOfWeek()
                        ),
                        Function.identity()
                ));
        Map<LocalDate, WeatherSnapshot> weatherByDate = weatherRepository
                .findAllByTripIdOrderByForecastDateAsc(trip.getId())
                .stream()
                .collect(Collectors.toMap(
                        TripWeatherForecast::getForecastDate,
                        TripWeatherForecast::toSnapshot
                ));
        List<DailyCandidates> dailyCandidates = new ArrayList<>();
        for (LocalDate date = trip.getStartDate(); !date.isAfter(trip.getEndDate()); date = date.plusDays(1)) {
            LocalDate visitDate = date;
            WeatherSnapshot weather = weatherByDate.getOrDefault(
                    visitDate,
                    WeatherSnapshot.unknown()
            );
            dailyCandidates.add(new DailyCandidates(
                    visitDate,
                    tripPlaces.stream()
                            .map(tripPlace -> toScheduleCandidate(
                                    trip,
                                    tripPlace,
                                    openingHours.get(new OpeningHourKey(
                                            tripPlace.getPlace().getId(), visitDate.getDayOfWeek()
                                    )),
                                    weather
                            ))
                            .toList(),
                    weather
            ));
        }
        return new OptimizationSnapshot(
                trip.getId(),
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getDailyStartTime(),
                trip.getDailyEndTime(),
                optimizationRequest,
                dailyCandidates,
                BudgetInput.from(trip, tripPlaces), trip.getTimeZoneId()
        );
    }

    private ScheduleCandidate toScheduleCandidate(
            Trip trip,
            TripPlace tripPlace,
            PlaceOpeningHour openingHour,
            WeatherSnapshot weather
    ) {
        Place place = tripPlace.getPlace();
        return new ScheduleCandidate(
                tripPlace.getId(),
                place.getId(),
                place.getName(),
                Location.of(place.getLatitude(), place.getLongitude()),
                tripPlace.getPriority(),
                tripPlace.isMustVisit(),
                openingHour == null ? null : openingHour.getOpenTime(),
                openingHour == null ? null : openingHour.getCloseTime(),
                openingHour != null && openingHour.isClosed(),
                tripPlace.getPreferredStartTime(),
                tripPlace.getPreferredEndTime(),
                trip.getPace().stayMinutes(
                        place.getAverageStayMinutes(),
                        tripPlace.getMinimumStayMinutes(),
                        tripPlace.getMaximumStayMinutes()
                ),
                weatherPolicy.adjustment(weather, place.getEnvironment())
        );
    }

    private List<Location> locations(OptimizationRequest request) {
        return Stream.concat(
                        Stream.of(request.startLocation()),
                        request.candidates().stream().map(VisitCandidate::location)
                )
                .distinct()
                .toList();
    }

    private record OptimizationSnapshot(
            Long tripId,
            LocalDate startDate,
            LocalDate endDate,
            LocalTime dailyStartTime,
            LocalTime dailyEndTime,
            OptimizationRequest optimizationRequest,
            List<DailyCandidates> dailyCandidates,
            BudgetInput budget, String timeZoneId
    ) {

        private OptimizationSnapshot {
            dailyCandidates = List.copyOf(dailyCandidates);
        }

        private List<ScheduleRequest> toScheduleRequests(OptimizationResult result) {
            List<Long> proposedOrder = result.stops().stream()
                    .map(stop -> stop.tripPlaceId())
                    .toList();
            return dailyCandidates.stream()
                    .map(day -> new ScheduleRequest(
                            day.visitDate(),
                            dailyStartTime,
                            dailyEndTime,
                            optimizationRequest.startLocation(),
                            optimizationRequest.startLocation(),
                            optimizationRequest.transportMode(),
                            result.algorithm(),
                            day.candidates(),
                            proposedOrder
                    ))
                    .toList();
        }

        private boolean hasSameInput(OptimizationSnapshot other) {
            return tripId.equals(other.tripId)
                    && startDate.equals(other.startDate)
                    && endDate.equals(other.endDate)
                    && dailyStartTime.equals(other.dailyStartTime)
                    && dailyEndTime.equals(other.dailyEndTime)
                    && optimizationRequest.equals(other.optimizationRequest)
                    && dailyCandidates.equals(other.dailyCandidates)
                    && budget.equals(other.budget)
                    && timeZoneId.equals(other.timeZoneId);
        }

        private WeatherSnapshot weatherFor(LocalDate visitDate) {
            return dailyCandidates.stream()
                    .filter(day -> day.visitDate().equals(visitDate))
                    .map(DailyCandidates::weather)
                    .findFirst()
                    .orElseGet(WeatherSnapshot::unknown);
        }
    }

    private record DailyCandidates(
            LocalDate visitDate,
            List<ScheduleCandidate> candidates,
            WeatherSnapshot weather
    ) {

        private DailyCandidates {
            candidates = List.copyOf(candidates);
            Objects.requireNonNull(weather, "날짜별 날씨는 필수입니다.");
        }
    }

    private record OpeningHourKey(Long placeId, DayOfWeek dayOfWeek) {
    }
}
