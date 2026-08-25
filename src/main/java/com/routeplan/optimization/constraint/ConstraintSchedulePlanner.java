package com.routeplan.optimization.constraint;

import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.OptimizationAlgorithm;
import com.routeplan.optimization.domain.RouteResult;
import com.routeplan.optimization.route.RouteProvider;
import com.routeplan.trip.domain.TransportMode;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ConstraintSchedulePlanner {

    private static final int EXACT_REPAIR_LIMIT = 10;

    private final RouteProvider routeProvider;

    public ConstraintSchedulePlanner(RouteProvider routeProvider) {
        this.routeProvider = routeProvider;
    }

    public ConstraintSchedule plan(ScheduleRequest request) {
        return plan(request, routeProvider);
    }

    public ConstraintSchedule plan(ScheduleRequest request, RouteProvider routeProvider) {
        RouteCache routes = new RouteCache(routeProvider, request.transportMode());
        Map<Long, Integer> proposedRank = proposedRank(request.proposedTripPlaceOrder());
        List<ScheduleCandidate> candidates = request.candidates().stream()
                .sorted(Comparator
                        .comparing(ScheduleCandidate::mustVisit).reversed()
                        .thenComparing(Comparator.comparingInt(ScheduleCandidate::priority).reversed())
                        .thenComparingInt(candidate -> proposedRank.getOrDefault(
                                candidate.tripPlaceId(), Integer.MAX_VALUE
                        ))
                        .thenComparingLong(ScheduleCandidate::tripPlaceId))
                .toList();

        List<ScheduleCandidate> selected = new ArrayList<>();
        List<ExcludedVisit> exclusions = new ArrayList<>();
        Evaluation selectedEvaluation = evaluate(request, selected, routes).success();

        for (ScheduleCandidate candidate : candidates) {
            if (candidate.closed()) {
                if (candidate.mustVisit()) {
                    throw infeasible(List.of(candidate), ExclusionReason.CLOSED);
                }
                exclusions.add(exclusion(candidate, ExclusionReason.CLOSED));
                continue;
            }

            Evaluation insertion = bestInsertion(request, selected, candidate, routes);
            if (insertion != null) {
                selected = new ArrayList<>(insertion.order());
                selectedEvaluation = insertion;
                continue;
            }

            if (candidate.mustVisit()) {
                List<ScheduleCandidate> mandatory = new ArrayList<>(selected);
                mandatory.add(candidate);
                Evaluation repaired = mandatory.size() <= EXACT_REPAIR_LIMIT
                        ? bestPermutation(request, mandatory, routes)
                        : null;
                if (repaired == null) {
                    throw infeasible(mandatory, classify(request, candidate, routes));
                }
                selected = new ArrayList<>(repaired.order());
                selectedEvaluation = repaired;
                continue;
            }

            exclusions.add(exclusion(candidate, classify(request, candidate, routes)));
        }

        selectedEvaluation = improveForAlgorithm(request, selected, selectedEvaluation, routes);
        return toResult(selectedEvaluation, exclusions);
    }

    private Evaluation improveForAlgorithm(
            ScheduleRequest request,
            List<ScheduleCandidate> selected,
            Evaluation current,
            RouteCache routes
    ) {
        if (selected.isEmpty()) {
            return current;
        }
        if (request.algorithm() == OptimizationAlgorithm.EXACT_SEARCH) {
            Map<Long, ScheduleCandidate> byId = new HashMap<>();
            selected.forEach(candidate -> byId.put(candidate.tripPlaceId(), candidate));
            List<ScheduleCandidate> proposed = request.proposedTripPlaceOrder().stream()
                    .map(byId::get)
                    .filter(candidate -> candidate != null)
                    .toList();
            Attempt proposedAttempt = evaluate(request, proposed, routes);
            if (proposedAttempt.isSuccess()) {
                return proposedAttempt.success();
            }
            if (selected.size() <= EXACT_REPAIR_LIMIT) {
                Evaluation repaired = bestPermutation(request, selected, routes);
                if (repaired != null) {
                    return repaired;
                }
            }
            return current;
        }
        if (request.algorithm() != OptimizationAlgorithm.NEAREST_NEIGHBOR_2_OPT) {
            return current;
        }

        Evaluation best = current;
        boolean improved;
        do {
            improved = false;
            Evaluation roundBest = best;
            for (int start = 0; start < best.order().size() - 1; start++) {
                for (int end = start + 1; end < best.order().size(); end++) {
                    List<ScheduleCandidate> reversed = reverse(best.order(), start, end);
                    Attempt attempt = evaluate(request, reversed, routes);
                    if (attempt.isSuccess() && better(attempt.success(), roundBest)) {
                        roundBest = attempt.success();
                    }
                }
            }
            if (better(roundBest, best)) {
                best = roundBest;
                improved = true;
            }
        } while (improved);
        return best;
    }

    private Evaluation bestInsertion(
            ScheduleRequest request,
            List<ScheduleCandidate> selected,
            ScheduleCandidate candidate,
            RouteCache routes
    ) {
        Evaluation best = null;
        for (int index = 0; index <= selected.size(); index++) {
            List<ScheduleCandidate> order = new ArrayList<>(selected);
            order.add(index, candidate);
            Attempt attempt = evaluate(request, order, routes);
            if (attempt.isSuccess() && (best == null || better(attempt.success(), best))) {
                best = attempt.success();
            }
        }
        return best;
    }

    private Evaluation bestPermutation(
            ScheduleRequest request,
            List<ScheduleCandidate> candidates,
            RouteCache routes
    ) {
        BestEvaluation best = new BestEvaluation();
        permute(request, candidates, new boolean[candidates.size()], new ArrayList<>(), routes, best);
        return best.value;
    }

    private void permute(
            ScheduleRequest request,
            List<ScheduleCandidate> candidates,
            boolean[] used,
            List<ScheduleCandidate> order,
            RouteCache routes,
            BestEvaluation best
    ) {
        if (order.size() == candidates.size()) {
            Attempt attempt = evaluate(request, order, routes);
            if (attempt.isSuccess() && (best.value == null || better(attempt.success(), best.value))) {
                best.value = attempt.success();
            }
            return;
        }
        for (int index = 0; index < candidates.size(); index++) {
            if (used[index]) {
                continue;
            }
            used[index] = true;
            order.add(candidates.get(index));
            permute(request, candidates, used, order, routes, best);
            order.removeLast();
            used[index] = false;
        }
    }

    private Attempt evaluate(
            ScheduleRequest request,
            List<ScheduleCandidate> order,
            RouteCache routes
    ) {
        int currentMinute = minuteOfDay(request.dailyStartTime());
        Location currentLocation = request.accommodation();
        long totalDistance = 0;
        int totalTravel = 0;
        int totalStay = 0;
        int totalWaiting = 0;
        int priorityScore = 0;
        List<ScheduledVisit> visits = new ArrayList<>(order.size());

        for (int index = 0; index < order.size(); index++) {
            ScheduleCandidate candidate = order.get(index);
            if (candidate.closed()) {
                return Attempt.failure(candidate, ExclusionReason.CLOSED);
            }
            RouteResult leg = routes.route(currentLocation, candidate.location());
            int arrivalMinute = currentMinute + leg.estimatedTravelMinutes();
            int windowStart = Math.max(
                    minuteOfDay(request.dailyStartTime()),
                    maximumTime(candidate.openingTime(), candidate.preferredStartTime())
            );
            int windowEnd = Math.min(
                    minuteOfDay(request.dailyEndTime()),
                    minimumTime(candidate.closingTime(), candidate.preferredEndTime())
            );
            int startMinute = Math.max(arrivalMinute, windowStart);
            int endMinute = startMinute + candidate.stayMinutes();
            if (windowEnd <= windowStart || endMinute > windowEnd) {
                return Attempt.failure(candidate, ExclusionReason.TIME_WINDOW);
            }

            int waiting = startMinute - arrivalMinute;
            visits.add(new ScheduledVisit(
                    candidate.tripPlaceId(),
                    candidate.placeId(),
                    index + 1,
                    request.visitDate(),
                    toTime(arrivalMinute),
                    toTime(startMinute),
                    toTime(endMinute),
                    leg.distanceMeters(),
                    leg.estimatedTravelMinutes(),
                    waiting,
                    candidate.stayMinutes(),
                    candidate.priority(),
                    candidate.mustVisit()
            ));
            totalDistance = Math.addExact(totalDistance, leg.distanceMeters());
            totalTravel = Math.addExact(totalTravel, leg.estimatedTravelMinutes());
            totalStay = Math.addExact(totalStay, candidate.stayMinutes());
            totalWaiting = Math.addExact(totalWaiting, waiting);
            priorityScore = Math.addExact(priorityScore, candidate.priority());
            currentMinute = endMinute;
            currentLocation = candidate.location();
        }

        RouteResult returnLeg = order.isEmpty()
                ? new RouteResult(0, 0)
                : routes.route(currentLocation, request.accommodation());
        int returnMinute = currentMinute + returnLeg.estimatedTravelMinutes();
        if (returnMinute > minuteOfDay(request.dailyEndTime())) {
            ScheduleCandidate culprit = order.getLast();
            return Attempt.failure(culprit, ExclusionReason.DAILY_LIMIT);
        }
        totalDistance = Math.addExact(totalDistance, returnLeg.distanceMeters());
        totalTravel = Math.addExact(totalTravel, returnLeg.estimatedTravelMinutes());
        int optimizationScore = Math.max(
                0,
                priorityScore * 10_000 - totalTravel * 5 - totalWaiting * 2
        );
        return Attempt.success(new Evaluation(
                List.copyOf(order),
                List.copyOf(visits),
                optimizationScore,
                priorityScore,
                totalDistance,
                totalTravel,
                totalStay,
                totalWaiting,
                returnLeg,
                toTime(returnMinute)
        ));
    }

    private boolean better(Evaluation candidate, Evaluation current) {
        int scoreComparison = Integer.compare(candidate.optimizationScore(), current.optimizationScore());
        if (scoreComparison != 0) {
            return scoreComparison > 0;
        }
        int travelComparison = Integer.compare(candidate.totalTravelMinutes(), current.totalTravelMinutes());
        if (travelComparison != 0) {
            return travelComparison < 0;
        }
        int waitingComparison = Integer.compare(candidate.totalWaitingMinutes(), current.totalWaitingMinutes());
        if (waitingComparison != 0) {
            return waitingComparison < 0;
        }
        int distanceComparison = Long.compare(candidate.totalDistanceMeters(), current.totalDistanceMeters());
        if (distanceComparison != 0) {
            return distanceComparison < 0;
        }
        return compareOrder(candidate.order(), current.order()) < 0;
    }

    private int compareOrder(List<ScheduleCandidate> left, List<ScheduleCandidate> right) {
        for (int index = 0; index < Math.min(left.size(), right.size()); index++) {
            int comparison = Long.compare(
                    left.get(index).tripPlaceId(),
                    right.get(index).tripPlaceId()
            );
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    private ConstraintSchedule toResult(Evaluation evaluation, List<ExcludedVisit> exclusions) {
        return new ConstraintSchedule(
                evaluation.visits(),
                exclusions,
                evaluation.optimizationScore(),
                evaluation.priorityScore(),
                evaluation.totalDistanceMeters(),
                evaluation.totalTravelMinutes(),
                evaluation.totalStayMinutes(),
                evaluation.totalWaitingMinutes(),
                evaluation.returnLeg().distanceMeters(),
                evaluation.returnLeg().estimatedTravelMinutes(),
                evaluation.returnArrivalTime()
        );
    }

    private InfeasibleScheduleException infeasible(
            List<ScheduleCandidate> candidates,
            ExclusionReason fallbackReason
    ) {
        List<ConstraintViolation> violations = candidates.stream()
                .filter(ScheduleCandidate::mustVisit)
                .map(candidate -> new ConstraintViolation(
                        candidate.placeId(),
                        candidate.placeName(),
                        candidate.closed() ? ExclusionReason.CLOSED : fallbackReason,
                        violationMessage(candidate, candidate.closed() ? ExclusionReason.CLOSED : fallbackReason)
                ))
                .toList();
        return new InfeasibleScheduleException(violations);
    }

    private ExclusionReason classify(
            ScheduleRequest request,
            ScheduleCandidate candidate,
            RouteCache routes
    ) {
        if (candidate.closed()) {
            return ExclusionReason.CLOSED;
        }
        Attempt alone = evaluate(request, List.of(candidate), routes);
        return alone.isSuccess() ? ExclusionReason.DAILY_LIMIT : alone.failure().reason();
    }

    private String violationMessage(ScheduleCandidate candidate, ExclusionReason reason) {
        return switch (reason) {
            case CLOSED -> candidate.placeName() + "은 여행일에 휴무입니다.";
            case TIME_WINDOW -> candidate.placeName() + "의 영업·선호시간과 체류시간이 충돌합니다.";
            case DAILY_LIMIT -> candidate.placeName() + "을 포함하면 하루 종료 전 숙소로 돌아올 수 없습니다.";
        };
    }

    private ExcludedVisit exclusion(ScheduleCandidate candidate, ExclusionReason reason) {
        return new ExcludedVisit(
                candidate.placeId(), candidate.placeName(), candidate.priority(), reason
        );
    }

    private Map<Long, Integer> proposedRank(List<Long> proposedOrder) {
        Map<Long, Integer> rank = new HashMap<>();
        for (int index = 0; index < proposedOrder.size(); index++) {
            rank.putIfAbsent(proposedOrder.get(index), index);
        }
        return rank;
    }

    private List<ScheduleCandidate> reverse(List<ScheduleCandidate> route, int start, int end) {
        List<ScheduleCandidate> result = new ArrayList<>(route);
        while (start < end) {
            ScheduleCandidate value = result.get(start);
            result.set(start, result.get(end));
            result.set(end, value);
            start++;
            end--;
        }
        return result;
    }

    private int maximumTime(LocalTime first, LocalTime second) {
        int firstMinute = first == null ? 0 : minuteOfDay(first);
        int secondMinute = second == null ? 0 : minuteOfDay(second);
        return Math.max(firstMinute, secondMinute);
    }

    private int minimumTime(LocalTime first, LocalTime second) {
        int firstMinute = first == null ? 24 * 60 : minuteOfDay(first);
        int secondMinute = second == null ? 24 * 60 : minuteOfDay(second);
        return Math.min(firstMinute, secondMinute);
    }

    private int minuteOfDay(LocalTime time) {
        return time.toSecondOfDay() / 60;
    }

    private LocalTime toTime(int minuteOfDay) {
        return LocalTime.ofSecondOfDay(minuteOfDay * 60L);
    }

    private static final class BestEvaluation {
        private Evaluation value;
    }

    private record Evaluation(
            List<ScheduleCandidate> order,
            List<ScheduledVisit> visits,
            int optimizationScore,
            int priorityScore,
            long totalDistanceMeters,
            int totalTravelMinutes,
            int totalStayMinutes,
            int totalWaitingMinutes,
            RouteResult returnLeg,
            LocalTime returnArrivalTime
    ) {
    }

    private record Failure(ScheduleCandidate candidate, ExclusionReason reason) {
    }

    private record Attempt(Evaluation success, Failure failure) {

        static Attempt success(Evaluation evaluation) {
            return new Attempt(evaluation, null);
        }

        static Attempt failure(ScheduleCandidate candidate, ExclusionReason reason) {
            return new Attempt(null, new Failure(candidate, reason));
        }

        boolean isSuccess() {
            return success != null;
        }
    }

    private static final class RouteCache {

        private final RouteProvider routeProvider;
        private final TransportMode transportMode;
        private final Map<Leg, RouteResult> cache = new HashMap<>();

        private RouteCache(RouteProvider routeProvider, TransportMode transportMode) {
            this.routeProvider = routeProvider;
            this.transportMode = transportMode;
        }

        private RouteResult route(Location origin, Location destination) {
            return cache.computeIfAbsent(
                    new Leg(origin, destination),
                    ignored -> routeProvider.getRoute(origin, destination, transportMode)
            );
        }
    }

    private record Leg(Location origin, Location destination) {
    }
}
