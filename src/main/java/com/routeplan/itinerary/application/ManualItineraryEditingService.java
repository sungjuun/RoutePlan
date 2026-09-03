package com.routeplan.itinerary.application;

import com.routeplan.common.error.ErrorCode;
import com.routeplan.common.error.RoutePlanException;
import com.routeplan.itinerary.domain.Itinerary;
import com.routeplan.itinerary.domain.ItineraryChangeReason;
import com.routeplan.itinerary.domain.ItineraryDay;
import com.routeplan.itinerary.domain.ItineraryItem;
import com.routeplan.itinerary.domain.ItineraryItemStatus;
import com.routeplan.itinerary.persistence.ItineraryRepository;
import com.routeplan.optimization.constraint.ConstraintSchedule;
import com.routeplan.optimization.constraint.ConstraintSchedulePlanner;
import com.routeplan.optimization.constraint.ScheduleCandidate;
import com.routeplan.optimization.constraint.ScheduleRequest;
import com.routeplan.optimization.constraint.ScheduledVisit;
import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.OptimizationAlgorithm;
import com.routeplan.optimization.route.RouteMatrix;
import com.routeplan.optimization.route.RouteMatrixProvider;
import com.routeplan.place.domain.Place;
import com.routeplan.place.domain.PlaceOpeningHour;
import com.routeplan.place.persistence.PlaceOpeningHourRepository;
import com.routeplan.trip.domain.Trip;
import com.routeplan.trip.domain.TripPlace;
import com.routeplan.trip.persistence.TripPlaceRepository;
import com.routeplan.trip.persistence.TripRepository;
import com.routeplan.weather.domain.WeatherSnapshot;
import com.routeplan.weather.domain.WeatherSuitabilityPolicy;
import com.routeplan.weather.persistence.TripWeatherForecastRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recalculates only the days touched by a manual move. Unchanged days are copied
 * from the immutable source version, so a drag on DAY 2 cannot unexpectedly
 * rewrite the rest of the trip.
 */
@Service
public class ManualItineraryEditingService {

    private final TripRepository tripRepository;
    private final TripPlaceRepository tripPlaceRepository;
    private final ItineraryRepository itineraryRepository;
    private final PlaceOpeningHourRepository openingHourRepository;
    private final TripWeatherForecastRepository weatherRepository;
    private final WeatherSuitabilityPolicy weatherPolicy;
    private final ConstraintSchedulePlanner schedulePlanner;
    private final RouteMatrixProvider routeMatrixProvider;

    public ManualItineraryEditingService(
            TripRepository tripRepository,
            TripPlaceRepository tripPlaceRepository,
            ItineraryRepository itineraryRepository,
            PlaceOpeningHourRepository openingHourRepository,
            TripWeatherForecastRepository weatherRepository,
            WeatherSuitabilityPolicy weatherPolicy,
            ConstraintSchedulePlanner schedulePlanner,
            RouteMatrixProvider routeMatrixProvider
    ) {
        this.tripRepository = tripRepository;
        this.tripPlaceRepository = tripPlaceRepository;
        this.itineraryRepository = itineraryRepository;
        this.openingHourRepository = openingHourRepository;
        this.weatherRepository = weatherRepository;
        this.weatherPolicy = weatherPolicy;
        this.schedulePlanner = schedulePlanner;
        this.routeMatrixProvider = routeMatrixProvider;
    }

    @Transactional(readOnly = true)
    public ManualEditPreview preview(Long tripId, ManualEditCommand command) {
        EditContext context = context(tripId, command);
        Map<LocalDate, RouteMatrix> matrices = buildMatrices(context);
        Calculation requested = calculate(context, command.assignments(), OptimizationAlgorithm.EXACT_SEARCH, matrices);
        Calculation recommended = calculate(context, command.assignments(), OptimizationAlgorithm.NEAREST_NEIGHBOR_2_OPT, matrices);
        List<DayAssignment> recommendation = recommended.assignments();
        int savingMinutes = requested.totalTravelMinutes() - recommended.totalTravelMinutes();
        long savingDistance = requested.totalDistanceMeters() - recommended.totalDistanceMeters();
        boolean hasRecommendation = !recommendation.equals(command.assignments())
                && (savingMinutes > 0 || savingDistance > 0);
        return new ManualEditPreview(
                context.source().getId(),
                context.source().getVersion(),
                context.affectedDates().stream().sorted().toList(),
                requested.totalTravelMinutes() - context.source().getEstimatedTravelMinutes(),
                requested.totalDistanceMeters() - context.source().getTotalDistanceMeters(),
                requested.totalTravelMinutes(),
                requested.totalDistanceMeters(),
                hasRecommendation ? new RouteRecommendation(
                        recommendation,
                        Math.max(0, savingMinutes),
                        Math.max(0, savingDistance),
                        recommendationMessage(context, command.assignments(), recommendation, savingMinutes)
                ) : null
        );
    }

    @Transactional
    public ItineraryView apply(Long tripId, ManualEditCommand command) {
        EditContext context = context(tripId, command);
        Calculation result = calculate(context, command.assignments(), OptimizationAlgorithm.EXACT_SEARCH,
                buildMatrices(context));
        Trip trip = tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_NOT_FOUND));
        if (itineraryRepository.findMaxVersionByTripId(tripId) != context.source().getVersion()) {
            throw new RoutePlanException(ErrorCode.REOPTIMIZATION_SOURCE_NOT_LATEST);
        }

        RouteMatrix matrix = result.routeMatrix();
        Itinerary itinerary = Itinerary.create(
                trip,
                context.source().getVersion() + 1,
                OptimizationAlgorithm.EXACT_SEARCH,
                result.totalDistanceMeters(),
                result.totalTravelMinutes(),
                result.optimizationScore(),
                result.totalStayMinutes(),
                result.totalWaitingMinutes(),
                result.totalReturnDistanceMeters(),
                result.totalReturnTravelMinutes(),
                result.lastReturnArrivalTime(),
                true,
                matrix.dataType(),
                matrix.providerCallCount(),
                matrix.elementCount(),
                matrix.buildMillis(),
                matrix.cacheEnabled(),
                matrix.cacheHitCount(),
                matrix.cacheMissCount(),
                matrix.cacheFailureCount()
        );

        int sequence = 1;
        for (DayResult day : result.days()) {
            itinerary.addDay(
                    day.date(), day.dayNumber(), day.distanceMeters(), day.travelMinutes(),
                    day.stayMinutes(), day.waitingMinutes(), day.returnDistanceMeters(),
                    day.returnTravelMinutes(), day.returnArrivalTime(), true,
                    day.weather().condition(), day.weather().precipitationProbability()
            );
            for (ItemResult item : day.items()) {
                ItineraryItem sourceItem = item.sourceItem();
                if (item.status() == ItineraryItemStatus.COMPLETED) {
                    itinerary.addCompletedItem(
                            sourceItem.getPlace(), sequence++, item.travelDistanceMeters(), item.travelMinutes(),
                            day.date(), item.arrivalTime(), item.startTime(), item.endTime(), item.waitingMinutes(),
                            item.stayMinutes(), item.priority(), item.mustVisit(), item.weatherScoreAdjustment()
                    );
                } else {
                    itinerary.addItem(
                            sourceItem.getPlace(), sequence++, item.travelDistanceMeters(), item.travelMinutes(),
                            day.date(), item.arrivalTime(), item.startTime(), item.endTime(), item.waitingMinutes(),
                            item.stayMinutes(), item.priority(), item.mustVisit(), item.weatherScoreAdjustment()
                    );
                }
            }
        }
        context.source().getExclusions().forEach(exclusion -> itinerary.addExclusion(
                exclusion.getPlace(), exclusion.getPriority(), exclusion.getReason()
        ));

        LocalDate firstChanged = context.affectedDates().stream().min(LocalDate::compareTo).orElseThrow();
        ItemResult completed = result.days().stream()
                .filter(day -> day.date().equals(firstChanged))
                .flatMap(day -> day.items().stream())
                .filter(item -> item.status() == ItineraryItemStatus.COMPLETED)
                .reduce((left, right) -> right)
                .orElse(null);
        Place startPlace = completed == null ? null : completed.sourceItem().getPlace();
        itinerary.markReoptimized(
                context.source(), ItineraryChangeReason.USER_REQUEST,
                "직접 일정 편집 · " + context.affectedDates().stream().sorted()
                        .map(LocalDate::toString).collect(Collectors.joining(", ")),
                firstChanged,
                completed == null ? trip.getDailyStartTime() : completed.endTime(),
                completed == null ? trip.getAccommodationLatitude() : startPlace.getLatitude(),
                completed == null ? trip.getAccommodationLongitude() : startPlace.getLongitude()
        );
        Map<Long, Long> costs = context.source().getItems().stream()
                .filter(item -> item.getEstimatedCostMinor() != null)
                .collect(Collectors.toMap(item -> item.getPlace().getId(), ItineraryItem::getEstimatedCostMinor));
        itinerary.recordBudget(context.source().getBudgetSettings(), costs);
        itinerary.recordTimeZone(context.source().getTimeZoneId());
        List<String> warnings = new ArrayList<>();
        if (context.source().getDataWarnings() != null && !context.source().getDataWarnings().isBlank()) {
            warnings.add(context.source().getDataWarnings());
        }
        warnings.add("직접 이동한 날짜만 영업시간과 이동 경로를 다시 계산했습니다.");
        itinerary.recordLiveData(warnings, trip.getTransportMode());
        trip.markOptimized();
        return ItineraryView.from(itineraryRepository.saveAndFlush(itinerary));
    }

    private EditContext context(Long tripId, ManualEditCommand command) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RoutePlanException(ErrorCode.TRIP_NOT_FOUND));
        Itinerary source = itineraryRepository.findDetailedById(command.sourceItineraryId())
                .orElseThrow(() -> new RoutePlanException(ErrorCode.ITINERARY_NOT_FOUND));
        if (!source.getTrip().getId().equals(tripId)) {
            throw new RoutePlanException(ErrorCode.REOPTIMIZATION_SOURCE_MISMATCH);
        }
        if (itineraryRepository.findMaxVersionByTripId(tripId) != source.getVersion()) {
            throw new RoutePlanException(ErrorCode.REOPTIMIZATION_SOURCE_NOT_LATEST);
        }
        List<LocalDate> travelDates = trip.getStartDate().datesUntil(trip.getEndDate().plusDays(1)).toList();
        if (command.assignments() == null || command.assignments().size() != travelDates.size()) {
            throw invalid("여행의 모든 날짜를 한 번씩 전송해야 합니다.");
        }
        Map<LocalDate, List<Long>> requested = new LinkedHashMap<>();
        for (DayAssignment assignment : command.assignments()) {
            if (assignment == null || assignment.visitDate() == null || assignment.itineraryItemIds() == null
                    || requested.putIfAbsent(assignment.visitDate(), List.copyOf(assignment.itineraryItemIds())) != null) {
                throw invalid("날짜별 일정은 중복 없이 한 번씩만 지정해야 합니다.");
            }
        }
        if (!requested.keySet().equals(new LinkedHashSet<>(travelDates))) {
            throw invalid("일정 날짜는 여행 기간과 정확히 일치해야 합니다.");
        }
        List<Long> flattened = requested.values().stream().flatMap(List::stream).toList();
        Set<Long> sourceIds = source.getItems().stream().map(ItineraryItem::getId).collect(Collectors.toSet());
        if (flattened.stream().anyMatch(java.util.Objects::isNull)
                || flattened.size() != new HashSet<>(flattened).size()
                || flattened.size() != sourceIds.size()
                || !new HashSet<>(flattened).equals(sourceIds)) {
            throw invalid("기존 일정의 모든 항목을 중복 없이 한 번씩 배치해야 합니다.");
        }
        Map<LocalDate, List<Long>> current = travelDates.stream().collect(Collectors.toMap(
                Function.identity(),
                date -> source.getItems().stream().filter(item -> date.equals(item.getVisitDate()))
                        .map(ItineraryItem::getId).toList(),
                (left, right) -> left,
                LinkedHashMap::new
        ));
        Set<LocalDate> affected = travelDates.stream()
                .filter(date -> !requested.get(date).equals(current.get(date)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (affected.isEmpty()) {
            throw invalid("변경된 장소 순서가 없습니다.");
        }
        for (LocalDate date : travelDates) {
            List<Long> completed = source.getItems().stream()
                    .filter(item -> date.equals(item.getVisitDate()) && item.getStatus() == ItineraryItemStatus.COMPLETED)
                    .map(ItineraryItem::getId).toList();
            List<Long> requestedDay = requested.get(date);
            if (requestedDay.size() < completed.size()
                    || !requestedDay.subList(0, completed.size()).equals(completed)) {
                throw invalid("완료된 방문은 원래 날짜와 순서대로 고정해야 합니다.");
            }
        }
        List<TripPlace> tripPlaces = tripPlaceRepository.findAllByTripIdOrderByIdAsc(tripId);
        Map<Long, TripPlace> tripPlaceByPlace = tripPlaces.stream()
                .collect(Collectors.toMap(value -> value.getPlace().getId(), Function.identity()));
        if (source.getItems().stream().anyMatch(item -> !tripPlaceByPlace.containsKey(item.getPlace().getId()))) {
            throw new RoutePlanException(ErrorCode.OPTIMIZATION_INPUT_CHANGED);
        }
        Map<Long, ItineraryItem> itemById = source.getItems().stream()
                .collect(Collectors.toMap(ItineraryItem::getId, Function.identity()));
        Map<Long, PlaceOpeningHour> openingHours = openingHourRepository.findAllByPlaceIdIn(
                        source.getItems().stream().map(item -> item.getPlace().getId()).toList())
                .stream().collect(Collectors.toMap(
                        hour -> openingKey(hour.getPlace().getId(), hour.getDayOfWeek().getValue()),
                        Function.identity()
                ));
        Map<LocalDate, WeatherSnapshot> weather = weatherRepository.findAllByTripIdOrderByForecastDateAsc(tripId)
                .stream().collect(Collectors.toMap(value -> value.getForecastDate(), value -> value.toSnapshot()));
        Map<LocalDate, ItineraryDay> sourceDay = source.getDays().stream()
                .collect(Collectors.toMap(ItineraryDay::getVisitDate, Function.identity()));
        if (!sourceDay.keySet().containsAll(travelDates)) {
            throw invalid("일자별 합계가 없는 이전 일정은 직접 편집할 수 없습니다.");
        }
        return new EditContext(trip, source, travelDates, requested, affected, itemById,
                tripPlaceByPlace, openingHours, weather, sourceDay);
    }

    private Calculation calculate(
            EditContext context,
            List<DayAssignment> assignments,
            OptimizationAlgorithm algorithm,
            Map<LocalDate, RouteMatrix> matrices
    ) {
        RouteMatrix summary = RouteMatrix.summarize(matrices.values());
        List<DayResult> days = new ArrayList<>();
        Map<LocalDate, List<Long>> assignmentByDate = assignments.stream()
                .collect(Collectors.toMap(DayAssignment::visitDate, DayAssignment::itineraryItemIds));
        for (LocalDate date : context.travelDates()) {
            List<Long> ids = assignmentByDate.get(date);
            if (!context.affectedDates().contains(date)) {
                days.add(copyDay(context, date, ids));
            } else {
                days.add(recalculateDay(context, date, ids, algorithm, matrices.get(date)));
            }
        }
        List<DayAssignment> normalized = days.stream().map(day -> new DayAssignment(
                day.date(), day.items().stream().map(item -> item.sourceItem().getId()).toList()
        )).toList();
        long distance = days.stream().mapToLong(DayResult::distanceMeters).sum();
        int travel = days.stream().mapToInt(DayResult::travelMinutes).sum();
        int stay = days.stream().mapToInt(DayResult::stayMinutes).sum();
        int waiting = days.stream().mapToInt(DayResult::waitingMinutes).sum();
        int score = Math.max(0, days.stream().flatMap(day -> day.items().stream())
                .mapToInt(item -> Math.max(1, Math.min(150, item.priority() + item.weatherScoreAdjustment())))
                .sum() * 10_000 - travel * 5 - waiting * 2);
        return new Calculation(
                days, normalized, distance, travel, stay, waiting,
                days.stream().mapToLong(DayResult::returnDistanceMeters).sum(),
                days.stream().mapToInt(DayResult::returnTravelMinutes).sum(),
                days.getLast().returnArrivalTime(), score, summary
        );
    }

    private Map<LocalDate, RouteMatrix> buildMatrices(EditContext context) {
        List<Location> locations = new ArrayList<>();
        Location hotel = Location.of(context.trip().getAccommodationLatitude(), context.trip().getAccommodationLongitude());
        locations.add(hotel);
        context.affectedDates().forEach(date -> context.requested().get(date).stream()
                .map(context.itemById()::get).map(ItineraryItem::getPlace)
                .map(place -> Location.of(place.getLatitude(), place.getLongitude()))
                .forEach(locations::add));
        List<LocalDate> affected = context.affectedDates().stream().sorted().toList();
        return routeMatrixProvider.buildForDates(
                locations.stream().distinct().toList(), context.trip().getTransportMode(), affected,
                context.trip().getDailyStartTime(), context.trip().getDailyStartTime(), context.source().getTimeZoneId()
        );
    }

    private DayResult copyDay(EditContext context, LocalDate date, List<Long> ids) {
        ItineraryDay sourceDay = context.sourceDay().get(date);
        List<ItemResult> items = ids.stream().map(context.itemById()::get).map(ItemResult::copy).toList();
        return new DayResult(
                date, sourceDay.getDayNumber(), items, sourceDay.getTotalDistanceMeters(),
                sourceDay.getEstimatedTravelMinutes(), sourceDay.getTotalStayMinutes(),
                sourceDay.getTotalWaitingMinutes(), sourceDay.getReturnTravelDistanceMeters(),
                sourceDay.getReturnTravelMinutes(), sourceDay.getReturnArrivalTime(),
                new WeatherSnapshot(sourceDay.getWeatherCondition(), sourceDay.getPrecipitationProbability())
        );
    }

    private DayResult recalculateDay(
            EditContext context,
            LocalDate date,
            List<Long> ids,
            OptimizationAlgorithm algorithm,
            RouteMatrix matrix
    ) {
        List<ItineraryItem> sourceItems = ids.stream().map(context.itemById()::get).toList();
        List<ItineraryItem> completed = sourceItems.stream()
                .takeWhile(item -> item.getStatus() == ItineraryItemStatus.COMPLETED).toList();
        List<ItineraryItem> planned = sourceItems.subList(completed.size(), sourceItems.size());
        Location hotel = Location.of(context.trip().getAccommodationLatitude(), context.trip().getAccommodationLongitude());
        Location start = completed.isEmpty() ? hotel : location(completed.getLast().getPlace());
        LocalTime startTime = completed.isEmpty() ? context.trip().getDailyStartTime() : completed.getLast().getEndTime();
        WeatherSnapshot weather = context.weather().getOrDefault(date, new WeatherSnapshot(
                context.sourceDay().get(date).getWeatherCondition(),
                context.sourceDay().get(date).getPrecipitationProbability()
        ));
        List<ScheduleCandidate> candidates = planned.stream().map(sourceItem -> {
            TripPlace tripPlace = context.tripPlaceByPlace().get(sourceItem.getPlace().getId());
            PlaceOpeningHour hour = context.openingHours().get(openingKey(
                    sourceItem.getPlace().getId(), date.getDayOfWeek().getValue()
            ));
            return new ScheduleCandidate(
                    tripPlace.getId(), sourceItem.getPlace().getId(), sourceItem.getPlace().getName(),
                    location(sourceItem.getPlace()), tripPlace.getPriority(), tripPlace.isMustVisit(),
                    hour == null ? null : hour.getOpenTime(), hour == null ? null : hour.getCloseTime(),
                    hour != null && hour.isClosed(), tripPlace.getPreferredStartTime(), tripPlace.getPreferredEndTime(),
                    context.trip().getPace().stayMinutes(sourceItem.getPlace().getAverageStayMinutes(),
                            tripPlace.getMinimumStayMinutes(), tripPlace.getMaximumStayMinutes()),
                    weatherPolicy.adjustment(weather, sourceItem.getPlace().getEnvironment())
            );
        }).toList();
        List<Long> proposed = planned.stream().map(item -> context.tripPlaceByPlace()
                .get(item.getPlace().getId()).getId()).toList();
        ConstraintSchedule schedule = schedulePlanner.plan(new ScheduleRequest(
                date, startTime, context.trip().getDailyEndTime(), start, hotel,
                context.trip().getTransportMode(), algorithm, candidates, proposed
        ), matrix);
        List<Long> actual = schedule.visits().stream().map(ScheduledVisit::tripPlaceId).toList();
        if (schedule.exclusions().size() > 0 || actual.size() != proposed.size()) {
            throw infeasible("옮긴 날짜에 모든 장소를 배치할 시간이 부족합니다.");
        }
        if (algorithm == OptimizationAlgorithm.EXACT_SEARCH && !actual.equals(proposed)) {
            throw infeasible("선택한 순서대로는 영업시간 또는 숙소 복귀시간을 맞출 수 없습니다.");
        }
        Map<Long, ItineraryItem> sourceByTripPlace = planned.stream().collect(Collectors.toMap(
                item -> context.tripPlaceByPlace().get(item.getPlace().getId()).getId(), Function.identity()
        ));
        List<ItemResult> itemResults = new ArrayList<>(completed.stream().map(ItemResult::copy).toList());
        schedule.visits().forEach(visit -> itemResults.add(ItemResult.from(
                sourceByTripPlace.get(visit.tripPlaceId()), visit
        )));
        long completedDistance = completed.stream().mapToLong(ItineraryItem::getTravelDistanceMeters).sum();
        int completedTravel = completed.stream().mapToInt(ItineraryItem::getEstimatedTravelMinutes).sum();
        int completedStay = completed.stream().mapToInt(ItineraryItem::getStayMinutes).sum();
        int completedWaiting = completed.stream().mapToInt(ItineraryItem::getWaitingMinutes).sum();
        return new DayResult(
                date, Math.toIntExact(ChronoUnit.DAYS.between(context.trip().getStartDate(), date) + 1),
                itemResults, completedDistance + schedule.totalDistanceMeters(),
                completedTravel + schedule.totalTravelMinutes(), completedStay + schedule.totalStayMinutes(),
                completedWaiting + schedule.totalWaitingMinutes(), schedule.returnTravelDistanceMeters(),
                schedule.returnTravelMinutes(), schedule.returnArrivalTime(), weather
        );
    }

    private String recommendationMessage(
            EditContext context,
            List<DayAssignment> requested,
            List<DayAssignment> recommended,
            int savingMinutes
    ) {
        Map<Long, String> names = context.itemById().values().stream()
                .collect(Collectors.toMap(ItineraryItem::getId, item -> item.getPlace().getName()));
        for (int day = 0; day < requested.size(); day++) {
            List<Long> before = requested.get(day).itineraryItemIds();
            List<Long> after = recommended.get(day).itineraryItemIds();
            for (int index = 0; index < Math.min(before.size(), after.size()); index++) {
                if (!before.get(index).equals(after.get(index))) {
                    String anchor = index == 0 ? "하루 첫 장소" : names.get(after.get(index - 1)) + " 다음";
                    return names.get(after.get(index)) + "을(를) " + anchor + "에 두면 약 "
                            + Math.max(0, savingMinutes) + "분을 줄일 수 있습니다.";
                }
            }
        }
        return "추천 순서를 적용하면 이동 부담을 줄일 수 있습니다.";
    }

    private long openingKey(long placeId, int dayOfWeek) {
        return placeId * 10 + dayOfWeek;
    }

    private Location location(Place place) {
        return Location.of(place.getLatitude(), place.getLongitude());
    }

    private RoutePlanException invalid(String message) {
        return new RoutePlanException(ErrorCode.INVALID_MANUAL_ITINERARY, message);
    }

    private RoutePlanException infeasible(String message) {
        return new RoutePlanException(ErrorCode.MANUAL_ITINERARY_INFEASIBLE, message);
    }

    public record ManualEditCommand(Long sourceItineraryId, List<DayAssignment> assignments) {
        public ManualEditCommand {
            assignments = assignments == null ? null : List.copyOf(assignments);
        }
    }

    public record DayAssignment(LocalDate visitDate, List<Long> itineraryItemIds) {
        public DayAssignment {
            itineraryItemIds = itineraryItemIds == null ? null : List.copyOf(itineraryItemIds);
        }
    }

    public record ManualEditPreview(
            Long sourceItineraryId,
            int sourceVersion,
            List<LocalDate> affectedDates,
            int travelMinutesDelta,
            long distanceMetersDelta,
            int totalTravelMinutes,
            long totalDistanceMeters,
            RouteRecommendation recommendation
    ) {}

    public record RouteRecommendation(
            List<DayAssignment> assignments,
            int savingMinutes,
            long savingDistanceMeters,
            String message
    ) {}

    private record EditContext(
            Trip trip,
            Itinerary source,
            List<LocalDate> travelDates,
            Map<LocalDate, List<Long>> requested,
            Set<LocalDate> affectedDates,
            Map<Long, ItineraryItem> itemById,
            Map<Long, TripPlace> tripPlaceByPlace,
            Map<Long, PlaceOpeningHour> openingHours,
            Map<LocalDate, WeatherSnapshot> weather,
            Map<LocalDate, ItineraryDay> sourceDay
    ) {}

    private record Calculation(
            List<DayResult> days,
            List<DayAssignment> assignments,
            long totalDistanceMeters,
            int totalTravelMinutes,
            int totalStayMinutes,
            int totalWaitingMinutes,
            long totalReturnDistanceMeters,
            int totalReturnTravelMinutes,
            LocalTime lastReturnArrivalTime,
            int optimizationScore,
            RouteMatrix routeMatrix
    ) {}

    private record DayResult(
            LocalDate date,
            int dayNumber,
            List<ItemResult> items,
            long distanceMeters,
            int travelMinutes,
            int stayMinutes,
            int waitingMinutes,
            long returnDistanceMeters,
            int returnTravelMinutes,
            LocalTime returnArrivalTime,
            WeatherSnapshot weather
    ) {}

    private record ItemResult(
            ItineraryItem sourceItem,
            ItineraryItemStatus status,
            long travelDistanceMeters,
            int travelMinutes,
            LocalTime arrivalTime,
            LocalTime startTime,
            LocalTime endTime,
            int waitingMinutes,
            int stayMinutes,
            int priority,
            boolean mustVisit,
            int weatherScoreAdjustment
    ) {
        private static ItemResult copy(ItineraryItem item) {
            return new ItemResult(
                    item, item.getStatus(), item.getTravelDistanceMeters(), item.getEstimatedTravelMinutes(),
                    item.getArrivalTime(), item.getStartTime(), item.getEndTime(), item.getWaitingMinutes(),
                    item.getStayMinutes(), item.getPriority(), item.getMustVisit(), item.getWeatherScoreAdjustment()
            );
        }

        private static ItemResult from(ItineraryItem source, ScheduledVisit visit) {
            return new ItemResult(
                    source, ItineraryItemStatus.PLANNED, visit.travelDistanceMeters(), visit.travelMinutes(),
                    visit.arrivalTime(), visit.startTime(), visit.endTime(), visit.waitingMinutes(),
                    visit.stayMinutes(), visit.priority(), visit.mustVisit(), visit.weatherScoreAdjustment()
            );
        }
    }
}
