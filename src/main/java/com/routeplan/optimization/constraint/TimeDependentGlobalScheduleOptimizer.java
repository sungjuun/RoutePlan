package com.routeplan.optimization.constraint;

import com.routeplan.integration.TravelTime;
import com.routeplan.integration.google.ExternalProviderException;
import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.RouteResult;
import com.routeplan.optimization.route.RouteDataType;
import com.routeplan.optimization.route.RouteMatrix;
import com.routeplan.optimization.route.RouteMatrixProvider;
import com.routeplan.trip.domain.TransportMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Bounded global beam search over all trip days and visit orders using preloaded departure-time matrices.
 * External calls are completed before combinatorial search starts.
 */
@Component
public class TimeDependentGlobalScheduleOptimizer {

    private final RouteMatrixProvider matrixProvider;
    private final TimeDependentOptimizationProperties properties;
    private final TimeDependentOptimizationMetrics metrics;

    public TimeDependentGlobalScheduleOptimizer(
            RouteMatrixProvider matrixProvider,
            TimeDependentOptimizationProperties properties,
            TimeDependentOptimizationMetrics metrics
    ) {
        this.matrixProvider = matrixProvider;
        this.properties = properties;
        this.metrics = metrics;
    }

    public Result optimize(
            MultiDaySchedule initial,
            List<ScheduleRequest> requests,
            ScheduleBudget budget,
            String zone,
            RouteDataType dataType
    ) {
        long started = System.nanoTime();
        Applicability applicability = applicability(requests, dataType);
        if (!applicability.applicable()) {
            return skipped(initial, applicability.reason(), 0, started, false);
        }
        List<ScheduleCandidate> candidates = canonicalCandidates(requests);
        List<Location> locations = locations(requests, candidates);
        int bucketMinutes = Math.toIntExact(properties.getBucket().toMinutes());
        List<TimeKey> timeKeys = timeKeys(requests, bucketMinutes);
        long projectedElements = (long) timeKeys.size() * locations.size() * locations.size();
        if (candidates.size() > properties.getMaxCandidates()) {
            return skipped(initial, "candidate_limit", 0, started, true);
        }
        if (requests.size() > properties.getMaxDays()) {
            return skipped(initial, "day_limit", 0, started, true);
        }
        if (timeKeys.size() > properties.getMaxMatrixBuilds()
                || projectedElements > properties.getMaxMatrixElements()) {
            return skipped(initial, "matrix_cost_limit", timeKeys.size(), started, true);
        }
        if (!supportedDepartureWindow(timeKeys, requests.getFirst().transportMode(), zone)) {
            return skipped(initial, "departure_window", timeKeys.size(), started, true);
        }

        Map<TimeKey, RouteMatrix> matrices = new LinkedHashMap<>();
        try {
            for (TimeKey key : timeKeys) {
                Instant departure = TravelTime.departure(key.date(), minute(key.minute()), zone);
                RouteMatrix matrix = matrixProvider.build(locations, requests.getFirst().transportMode(), departure);
                matrices.put(key, matrix);
            }
        } catch (ExternalProviderException | IllegalArgumentException exception) {
            RouteMatrix measurement = summarize(matrices);
            metrics.record("fallback", "provider_fallback", 0, matrices.size(), elapsedMillis(started));
            return new Result(initial, false, measurement, 0,
                    List.of("시간대별 Matrix 일부를 준비하지 못해 기존 일정 검증으로 전환됐습니다."));
        }

        RouteMatrix measurement = summarize(matrices);
        Search search = new Search(requests, candidates, budget, matrices, bucketMinutes, System.nanoTime());
        MultiDaySchedule optimized = search.run();
        long elapsed = elapsedMillis(started);
        if (optimized == null) {
            metrics.record("fallback", search.limitReached ? "search_limit" : "infeasible",
                    search.evaluatedStates, matrices.size(), elapsed);
            return new Result(initial, false, measurement, search.evaluatedStates,
                    List.of(search.limitReached
                            ? "시간대별 전역 탐색 상태 한도에 도달해 기존 일정으로 안전하게 되돌렸습니다."
                            : "시간대별 Matrix에서 모든 필수 장소를 만족하는 전역 일정을 찾지 못해 기존 일정을 유지했습니다."));
        }
        metrics.record("applied", "success", search.evaluatedStates, matrices.size(), elapsed);
        String source = requests.getFirst().transportMode() == TransportMode.DRIVING
                ? "예측 교통량" : "출발 시각별 대중교통";
        int providerCalls = measurement == null ? 0 : measurement.providerCallCount();
        return new Result(optimized, true, measurement, search.evaluatedStates,
                List.of(source + "을 반영한 " + matrices.size() + "개 시간 Matrix로 날짜와 방문 순서를 전역 재탐색했습니다"
                        + "(평가 상태 " + search.evaluatedStates + "개, 외부 호출 " + providerCalls + "회)."));
    }

    private Applicability applicability(List<ScheduleRequest> requests, RouteDataType dataType) {
        if (!properties.isEnabled()) return new Applicability(false, "disabled");
        if (dataType != RouteDataType.GOOGLE_ROUTES) return new Applicability(false, "non_google");
        if (requests == null || requests.isEmpty()) return new Applicability(false, "empty");
        TransportMode mode = requests.getFirst().transportMode();
        if (mode != TransportMode.DRIVING && mode != TransportMode.PUBLIC_TRANSIT) {
            return new Applicability(false, "timeless_mode");
        }
        return new Applicability(true, "applicable");
    }

    private Result skipped(
            MultiDaySchedule initial, String reason, int buckets,
            long started, boolean userVisible
    ) {
        metrics.record("skipped", reason, 0, buckets, elapsedMillis(started));
        List<String> warnings = userVisible
                ? List.of("시간대별 전역 최적화가 안전 상한(" + reason + ")에 따라 기존 일정 검증으로 전환됐습니다.")
                : List.of();
        return new Result(initial, false, null, 0, warnings);
    }

    private RouteMatrix summarize(Map<TimeKey, RouteMatrix> matrices) {
        return matrices.isEmpty() ? null : RouteMatrix.summarize(matrices.values());
    }

    private List<ScheduleCandidate> canonicalCandidates(List<ScheduleRequest> requests) {
        Map<Long, ScheduleCandidate> values = new LinkedHashMap<>();
        requests.getFirst().candidates().forEach(candidate -> values.put(candidate.tripPlaceId(), candidate));
        return List.copyOf(values.values());
    }

    private List<Location> locations(List<ScheduleRequest> requests, List<ScheduleCandidate> candidates) {
        return Stream.concat(
                        requests.stream().flatMap(request -> Stream.of(request.startLocation(), request.accommodation())),
                        candidates.stream().map(ScheduleCandidate::location))
                .distinct()
                .toList();
    }

    private List<TimeKey> timeKeys(List<ScheduleRequest> requests, int bucketMinutes) {
        Set<TimeKey> keys = new LinkedHashSet<>();
        for (ScheduleRequest request : requests) {
            int start = floor(minute(request.dailyStartTime()), bucketMinutes);
            int end = minute(request.dailyEndTime());
            for (int value = start; value <= end; value += bucketMinutes) {
                keys.add(new TimeKey(request.visitDate(), value));
            }
        }
        return List.copyOf(keys);
    }

    private boolean supportedDepartureWindow(List<TimeKey> keys, TransportMode mode, String zone) {
        Instant now = Instant.now();
        for (TimeKey key : keys) {
            Instant departure;
            try {
                departure = TravelTime.departure(key.date(), minute(key.minute()), zone);
            } catch (IllegalArgumentException exception) {
                return false;
            }
            if (mode == TransportMode.DRIVING && departure.isBefore(now.plus(Duration.ofMinutes(1)))) return false;
            if (mode == TransportMode.PUBLIC_TRANSIT
                    && (departure.isBefore(now.minus(Duration.ofDays(7)))
                    || departure.isAfter(now.plus(Duration.ofDays(100))))) return false;
        }
        return true;
    }

    private int floor(int minute, int bucketMinutes) {
        return Math.floorDiv(minute, bucketMinutes) * bucketMinutes;
    }

    private int minute(LocalTime time) {
        return time.toSecondOfDay() / 60;
    }

    private LocalTime minute(int value) {
        return LocalTime.ofSecondOfDay(value * 60L);
    }

    private long elapsedMillis(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    private final class Search {
        private final List<ScheduleRequest> requests;
        private final List<ScheduleCandidate> candidates;
        private final ScheduleBudget budget;
        private final Map<TimeKey, RouteMatrix> matrices;
        private final int bucketMinutes;
        private final long started;
        private final long requiredMask;
        private final Map<LocalDate, Map<Long, ScheduleCandidate>> candidatesByDate;
        private final int[] optimisticWeights;
        private int evaluatedStates;
        private boolean limitReached;

        private Search(
                List<ScheduleRequest> requests,
                List<ScheduleCandidate> candidates,
                ScheduleBudget budget,
                Map<TimeKey, RouteMatrix> matrices,
                int bucketMinutes,
                long started
        ) {
            this.requests = requests;
            this.candidates = candidates;
            this.budget = budget;
            this.matrices = matrices;
            this.bucketMinutes = bucketMinutes;
            this.started = started;
            this.requiredMask = requiredMask(candidates);
            this.candidatesByDate = candidatesByDate(requests);
            this.optimisticWeights = optimisticWeights(requests, candidates);
        }

        private MultiDaySchedule run() {
            ScheduleRequest first = requests.getFirst();
            State initial = new State(0, minute(first.dailyStartTime()), first.startLocation(), 0L, 0L,
                    List.of(), DayProgress.empty(), 0, 0, 0, 0, 0, 0);
            List<State> frontier = List.of(initial);
            State best = null;
            int maximumActions = candidates.size() + requests.size();
            for (int depth = 0; depth <= maximumActions && !frontier.isEmpty(); depth++) {
                List<State> expanded = new ArrayList<>();
                for (State state : frontier) {
                    if (state.dayIndex() == requests.size()) {
                        if (hasRequired(state.visitedMask()) && (best == null || betterComplete(state, best))) best = state;
                        continue;
                    }
                    if (limitExceeded()) {
                        limitReached = true;
                        break;
                    }
                    expandVisits(state, expanded);
                    State ended = endDay(state);
                    if (ended != null) expanded.add(ended);
                }
                if (limitReached) break;
                frontier = prune(expanded);
            }
            if (best == null) {
                for (State state : frontier) {
                    if (state.dayIndex() == requests.size() && hasRequired(state.visitedMask())
                            && (best == null || betterComplete(state, best))) best = state;
                }
            }
            return best == null ? null : schedule(best);
        }

        private void expandVisits(State state, List<State> target) {
            ScheduleRequest request = requests.get(state.dayIndex());
            Map<Long, ScheduleCandidate> today = candidatesByDate.get(request.visitDate());
            for (int index = 0; index < candidates.size(); index++) {
                long bit = 1L << index;
                if ((state.visitedMask() & bit) != 0) continue;
                ScheduleCandidate candidate = today.get(candidates.get(index).tripPlaceId());
                if (candidate == null || candidate.closed()) continue;
                long cost = budget.cost(candidate.placeId());
                if (!canAfford(state, index, cost)) continue;
                evaluatedStates++;
                RouteResult leg = route(request, state.current(), candidate.location(), state.minute());
                int arrival = Math.addExact(state.minute(), leg.estimatedTravelMinutes());
                int visitStart = candidate.earliestStart(arrival, request.dailyStartTime(), request.dailyEndTime());
                if (visitStart < 0) continue;
                int end = Math.addExact(visitStart, candidate.stayMinutes());
                RouteResult home = route(request, candidate.location(), request.accommodation(), end);
                if ((long) end + home.estimatedTravelMinutes() > minute(request.dailyEndTime())) continue;
                int waiting = visitStart - arrival;
                ScheduledVisit visit = new ScheduledVisit(
                        candidate.tripPlaceId(), candidate.placeId(), Long.bitCount(state.visitedMask()) + 1,
                        request.visitDate(), minute(arrival), minute(visitStart), minute(end),
                        leg.distanceMeters(), leg.estimatedTravelMinutes(), waiting,
                        candidate.stayMinutes(), candidate.priority(), candidate.mustVisit(),
                        candidate.weatherScoreAdjustment());
                DayProgress progress = state.progress().add(visit);
                target.add(new State(
                        state.dayIndex(), end, candidate.location(), state.visitedMask() | bit,
                        Math.addExact(state.spent(), cost), state.days(), progress,
                        Math.addExact(state.weightedPriority(), candidate.weatherAdjustedPriority()),
                        Math.addExact(state.priority(), candidate.priority()),
                        Math.addExact(state.distance(), leg.distanceMeters()),
                        Math.addExact(state.travel(), leg.estimatedTravelMinutes()),
                        Math.addExact(state.stay(), candidate.stayMinutes()),
                        Math.addExact(state.waiting(), waiting)));
            }
        }

        private State endDay(State state) {
            evaluatedStates++;
            ScheduleRequest request = requests.get(state.dayIndex());
            RouteResult home = route(request, state.current(), request.accommodation(), state.minute());
            int arrival = Math.addExact(state.minute(), home.estimatedTravelMinutes());
            if (arrival > minute(request.dailyEndTime())) return null;
            DayProgress progress = state.progress();
            DailySchedule day = new DailySchedule(
                    state.dayIndex() + 1, request.visitDate(), progress.visits(),
                    Math.addExact(progress.distance(), home.distanceMeters()),
                    Math.addExact(progress.travel(), home.estimatedTravelMinutes()),
                    progress.stay(), progress.waiting(), home.distanceMeters(),
                    home.estimatedTravelMinutes(), minute(arrival));
            List<DailySchedule> days = new ArrayList<>(state.days());
            days.add(day);
            int nextDay = state.dayIndex() + 1;
            if (nextDay == requests.size()) {
                return new State(nextDay, arrival, request.accommodation(), state.visitedMask(), state.spent(),
                        List.copyOf(days), DayProgress.empty(), state.weightedPriority(), state.priority(),
                        Math.addExact(state.distance(), home.distanceMeters()),
                        Math.addExact(state.travel(), home.estimatedTravelMinutes()),
                        state.stay(), state.waiting());
            }
            ScheduleRequest next = requests.get(nextDay);
            return new State(nextDay, minute(next.dailyStartTime()), next.startLocation(), state.visitedMask(), state.spent(),
                    List.copyOf(days), DayProgress.empty(), state.weightedPriority(), state.priority(),
                    Math.addExact(state.distance(), home.distanceMeters()),
                    Math.addExact(state.travel(), home.estimatedTravelMinutes()), state.stay(), state.waiting());
        }

        private boolean canAfford(State state, int selectedIndex, long selectedCost) {
            if (budget.availableMinor() == null) return true;
            long requiredAfterSelection = 0;
            for (int index = 0; index < candidates.size(); index++) {
                if (index == selectedIndex || (state.visitedMask() & (1L << index)) != 0) continue;
                ScheduleCandidate candidate = candidates.get(index);
                if (candidate.mustVisit()) {
                    requiredAfterSelection = Math.addExact(
                            requiredAfterSelection, budget.cost(candidate.placeId()));
                }
            }
            long committed = Math.addExact(state.spent(), selectedCost);
            return Math.addExact(committed, requiredAfterSelection) <= budget.availableMinor();
        }

        private RouteResult route(ScheduleRequest request, Location from, Location to, int departureMinute) {
            if (from.equals(to)) return new RouteResult(0, 0);
            int bucket = floor(departureMinute, bucketMinutes);
            RouteMatrix matrix = matrices.get(new TimeKey(request.visitDate(), bucket));
            if (matrix == null) throw new IllegalStateException("시간대별 Route Matrix가 누락됐습니다.");
            return matrix.getRoute(from, to, request.transportMode());
        }

        private List<State> prune(List<State> values) {
            Map<StateKey, State> bestByKey = new HashMap<>();
            for (State value : values) {
                StateKey key = new StateKey(value.dayIndex(), value.minute(), value.current(),
                        value.visitedMask(), value.spent());
                State current = bestByKey.get(key);
                if (current == null || betterPartial(value, current)) bestByKey.put(key, value);
            }
            return bestByKey.values().stream()
                    .sorted(partialComparator())
                    .limit(properties.getBeamWidth())
                    .toList();
        }

        private Comparator<State> partialComparator() {
            return Comparator
                    .comparingInt(this::potentialScore).reversed()
                    .thenComparing(Comparator.comparingInt((State state) -> Long.bitCount(state.visitedMask())).reversed())
                    .thenComparingInt(State::travel)
                    .thenComparingInt(State::waiting)
                    .thenComparingLong(State::distance)
                    .thenComparingLong(State::visitedMask);
        }

        private boolean betterPartial(State candidate, State current) {
            return partialComparator().compare(candidate, current) < 0;
        }

        private int potentialScore(State state) {
            long weighted = state.weightedPriority();
            for (int index = 0; index < optimisticWeights.length; index++) {
                if ((state.visitedMask() & (1L << index)) == 0) weighted += optimisticWeights[index];
            }
            long score = weighted * 10_000L - state.travel() * 5L - state.waiting() * 2L;
            return (int) Math.max(0, Math.min(Integer.MAX_VALUE, score));
        }

        private boolean betterComplete(State candidate, State current) {
            int score = Integer.compare(score(candidate), score(current));
            if (score != 0) return score > 0;
            if (candidate.travel() != current.travel()) return candidate.travel() < current.travel();
            if (candidate.waiting() != current.waiting()) return candidate.waiting() < current.waiting();
            return candidate.distance() < current.distance();
        }

        private int score(State state) {
            long value = state.weightedPriority() * 10_000L - state.travel() * 5L - state.waiting() * 2L;
            return (int) Math.max(0, Math.min(Integer.MAX_VALUE, value));
        }

        private MultiDaySchedule schedule(State state) {
            List<ScheduledVisit> visits = state.days().stream().flatMap(day -> day.visits().stream()).toList();
            List<ExcludedVisit> exclusions = new ArrayList<>();
            for (int index = 0; index < candidates.size(); index++) {
                if ((state.visitedMask() & (1L << index)) != 0) continue;
                ScheduleCandidate candidate = candidates.get(index);
                exclusions.add(new ExcludedVisit(candidate.placeId(), candidate.placeName(), candidate.priority(),
                        exclusionReason(candidate, state)));
            }
            return new MultiDaySchedule(
                    state.days(), visits, exclusions, score(state), state.priority(), state.distance(), state.travel(),
                    state.stay(), state.waiting(),
                    state.days().stream().mapToLong(DailySchedule::returnTravelDistanceMeters).sum(),
                    state.days().stream().mapToInt(DailySchedule::returnTravelMinutes).sum(),
                    state.days().getLast().returnArrivalTime());
        }

        private ExclusionReason exclusionReason(ScheduleCandidate candidate, State state) {
            if (budget.availableMinor() != null
                    && state.spent() + budget.cost(candidate.placeId()) > budget.availableMinor()) {
                return ExclusionReason.BUDGET;
            }
            List<ScheduleCandidate> daily = requests.stream()
                    .map(request -> candidatesByDate.get(request.visitDate()).get(candidate.tripPlaceId()))
                    .filter(value -> value != null)
                    .toList();
            if (daily.stream().allMatch(ScheduleCandidate::closed)) return ExclusionReason.CLOSED;
            boolean noWindow = requests.stream().allMatch(request -> {
                ScheduleCandidate value = candidatesByDate.get(request.visitDate()).get(candidate.tripPlaceId());
                return value == null || value.closed()
                        || value.earliestStart(minute(request.dailyStartTime()),
                        request.dailyStartTime(), request.dailyEndTime()) < 0;
            });
            return noWindow ? ExclusionReason.TIME_WINDOW : ExclusionReason.DAILY_LIMIT;
        }

        private boolean limitExceeded() {
            return evaluatedStates >= properties.getMaxEvaluatedStates()
                    || System.nanoTime() - started > properties.getMaxSearchDuration().toNanos();
        }

        private boolean hasRequired(long mask) {
            return (mask & requiredMask) == requiredMask;
        }
    }

    private long requiredMask(List<ScheduleCandidate> candidates) {
        long result = 0;
        for (int index = 0; index < candidates.size(); index++) {
            if (candidates.get(index).mustVisit()) result |= 1L << index;
        }
        return result;
    }

    private Map<LocalDate, Map<Long, ScheduleCandidate>> candidatesByDate(List<ScheduleRequest> requests) {
        Map<LocalDate, Map<Long, ScheduleCandidate>> result = new HashMap<>();
        for (ScheduleRequest request : requests) {
            Map<Long, ScheduleCandidate> values = new HashMap<>();
            request.candidates().forEach(candidate -> values.put(candidate.tripPlaceId(), candidate));
            result.put(request.visitDate(), values);
        }
        return result;
    }

    private int[] optimisticWeights(List<ScheduleRequest> requests, List<ScheduleCandidate> candidates) {
        int[] result = new int[candidates.size()];
        Map<LocalDate, Map<Long, ScheduleCandidate>> byDate = candidatesByDate(requests);
        for (int index = 0; index < candidates.size(); index++) {
            long id = candidates.get(index).tripPlaceId();
            result[index] = requests.stream()
                    .map(request -> byDate.get(request.visitDate()).get(id))
                    .filter(candidate -> candidate != null && !candidate.closed())
                    .mapToInt(ScheduleCandidate::weatherAdjustedPriority)
                    .max()
                    .orElse(0);
        }
        return result;
    }

    private record Applicability(boolean applicable, String reason) {}
    private record TimeKey(LocalDate date, int minute) {}
    private record StateKey(int dayIndex, int minute, Location current, long visitedMask, long spent) {}

    private record DayProgress(
            List<ScheduledVisit> visits, long distance, int travel, int stay, int waiting
    ) {
        private DayProgress {
            visits = List.copyOf(visits);
        }

        static DayProgress empty() { return new DayProgress(List.of(), 0, 0, 0, 0); }

        DayProgress add(ScheduledVisit visit) {
            List<ScheduledVisit> values = new ArrayList<>(visits);
            values.add(visit);
            return new DayProgress(values,
                    Math.addExact(distance, visit.travelDistanceMeters()),
                    Math.addExact(travel, visit.travelMinutes()),
                    Math.addExact(stay, visit.stayMinutes()),
                    Math.addExact(waiting, visit.waitingMinutes()));
        }
    }

    private record State(
            int dayIndex,
            int minute,
            Location current,
            long visitedMask,
            long spent,
            List<DailySchedule> days,
            DayProgress progress,
            int weightedPriority,
            int priority,
            long distance,
            int travel,
            int stay,
            int waiting
    ) {
        private State {
            days = List.copyOf(days);
        }
    }

    public record Result(
            MultiDaySchedule schedule,
            boolean applied,
            RouteMatrix matrixMeasurement,
            int evaluatedStates,
            List<String> warnings
    ) {
        public Result {
            warnings = List.copyOf(warnings);
        }

        public int providerCalls() {
            return matrixMeasurement == null ? 0 : matrixMeasurement.providerCallCount();
        }

        public long matrixMillis() {
            return matrixMeasurement == null ? 0 : matrixMeasurement.buildMillis();
        }
    }
}
