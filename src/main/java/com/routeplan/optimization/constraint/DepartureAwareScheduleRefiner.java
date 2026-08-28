package com.routeplan.optimization.constraint;

import com.routeplan.integration.TravelTime;
import com.routeplan.integration.google.*;
import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.RouteResult;
import com.routeplan.optimization.route.GoogleRoutesMatrixProvider;
import com.routeplan.optimization.route.RouteDataType;
import com.routeplan.trip.domain.TransportMode;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Bounded final-pass validation, not an external API call inside the combinatorial optimizer. */
@Component
public class DepartureAwareScheduleRefiner {
    private final ObjectProvider<GoogleRoutesMatrixProvider> providers;
    public DepartureAwareScheduleRefiner(ObjectProvider<GoogleRoutesMatrixProvider> providers) { this.providers = providers; }

    public Result refine(MultiDaySchedule initial, List<ScheduleRequest> requests, String zone, RouteDataType dataType) {
        if (dataType != RouteDataType.GOOGLE_ROUTES || requests.getFirst().transportMode() != TransportMode.PUBLIC_TRANSIT) {
            return new Result(initial, 0, 0, List.of());
        }
        return refine(initial, requests, zone, providers.getObject()::transitLeg);
    }

    public static Result refine(MultiDaySchedule initial, List<ScheduleRequest> requests, String zone, TransitLookup lookup) {
        long started = System.nanoTime();
        Map<LocalDate, ScheduleRequest> byDate = new HashMap<>();
        requests.forEach(r -> byDate.put(r.visitDate(), r));
        List<DailySchedule> days = new ArrayList<>();
        List<ScheduledVisit> visits = new ArrayList<>();
        List<ExcludedVisit> exclusions = new ArrayList<>(initial.exclusions());
        Map<Leg, RouteResult> cache = new HashMap<>();
        int limit = initial.visits().size() * 2 + initial.days().size();
        TransitLookup bounded = (from, to, departure) -> {
            if (from.equals(to)) return new RouteResult(0, 0);
            return cache.computeIfAbsent(new Leg(from, to, departure), ignored -> {
                if (cache.size() >= limit || System.nanoTime() - started > Duration.ofSeconds(45).toNanos()) {
                    throw new ExternalProviderException(ExternalProviderFailure.UNAVAILABLE,
                            "출발 시각별 검증 한도를 초과했습니다. 장소 수나 여행 기간을 줄여 다시 계산하세요.");
                }
                return lookup.route(from, to, departure);
            });
        };
        for (DailySchedule day : initial.days()) {
            var request = byDate.get(day.visitDate());
            Map<Long, ScheduleCandidate> candidates = new HashMap<>();
            request.candidates().forEach(c -> candidates.put(c.tripPlaceId(), c));
            Location origin = request.startLocation();
            int minute = request.dailyStartTime().toSecondOfDay() / 60;
            RouteResult home = bounded.route(origin, request.accommodation(), TravelTime.departure(day.visitDate(), time(minute), zone));
            if (minute + home.estimatedTravelMinutes() > request.dailyEndTime().toSecondOfDay() / 60) throw new InfeasibleReturnException();
            List<ScheduledVisit> today = new ArrayList<>();
            for (var oldVisit : day.visits()) {
                ScheduleCandidate candidate = candidates.get(oldVisit.tripPlaceId());
                RouteResult leg = bounded.route(origin, candidate.location(), TravelTime.departure(day.visitDate(), time(minute), zone));
                int arrival = Math.addExact(minute, leg.estimatedTravelMinutes());
                int start = candidate.earliestStart(arrival, request.dailyStartTime(), request.dailyEndTime());
                ExclusionReason failure = start < 0 ? ExclusionReason.TIME_WINDOW : null;
                int end = start + candidate.stayMinutes();
                RouteResult nextHome = null;
                if (failure == null) {
                    nextHome = bounded.route(candidate.location(), request.accommodation(), TravelTime.departure(day.visitDate(), time(end), zone));
                    if ((long) end + nextHome.estimatedTravelMinutes() > request.dailyEndTime().toSecondOfDay() / 60) failure = ExclusionReason.DAILY_LIMIT;
                }
                if (failure != null) {
                    if (candidate.mustVisit()) throw new InfeasibleScheduleException(List.of(new ConstraintViolation(
                            candidate.placeId(), candidate.placeName(), failure,
                            candidate.placeName() + ": 실제 출발 시각의 대중교통으로는 영업시간 또는 귀환 시간을 지킬 수 없습니다. 시간을 늘려 다시 계산하세요.")));
                    exclusions.add(new ExcludedVisit(candidate.placeId(), candidate.placeName(), candidate.priority(), failure));
                    continue;
                }
                var visit = new ScheduledVisit(candidate.tripPlaceId(), candidate.placeId(), visits.size() + 1,
                        day.visitDate(), time(arrival), time(start), time(end), leg.distanceMeters(),
                        leg.estimatedTravelMinutes(), start - arrival, candidate.stayMinutes(), candidate.priority(),
                        candidate.mustVisit(), candidate.weatherScoreAdjustment());
                today.add(visit); visits.add(visit);
                minute = end; origin = candidate.location(); home = nextHome;
            }
            days.add(new DailySchedule(day.dayNumber(), day.visitDate(), today,
                    today.stream().mapToLong(ScheduledVisit::travelDistanceMeters).sum() + home.distanceMeters(),
                    today.stream().mapToInt(ScheduledVisit::travelMinutes).sum() + home.estimatedTravelMinutes(),
                    today.stream().mapToInt(ScheduledVisit::stayMinutes).sum(), today.stream().mapToInt(ScheduledVisit::waitingMinutes).sum(),
                    home.distanceMeters(), home.estimatedTravelMinutes(), time(minute + home.estimatedTravelMinutes())));
        }
        int travel = days.stream().mapToInt(DailySchedule::totalTravelMinutes).sum();
        int waiting = days.stream().mapToInt(DailySchedule::totalWaitingMinutes).sum();
        int weighted = visits.stream().mapToInt(v -> Math.max(1, Math.min(150, v.priority() + v.weatherScoreAdjustment()))).sum();
        var schedule = new MultiDaySchedule(days, visits, exclusions, Math.max(0, weighted * 10_000 - travel * 5 - waiting * 2),
                visits.stream().mapToInt(ScheduledVisit::priority).sum(), days.stream().mapToLong(DailySchedule::totalDistanceMeters).sum(),
                travel, days.stream().mapToInt(DailySchedule::totalStayMinutes).sum(), waiting,
                days.stream().mapToLong(DailySchedule::returnTravelDistanceMeters).sum(), days.stream().mapToInt(DailySchedule::returnTravelMinutes).sum(),
                days.getLast().returnArrivalTime());
        return new Result(schedule, cache.size(), (System.nanoTime() - started) / 1_000_000,
                List.of("대중교통은 각 방문 종료 후 출발 시각으로 재검증했습니다(" + cache.size()
                        + "개 추가 Matrix 요소). 방문 순서는 유지하며 시간 제약을 넘는 선택 장소는 제외합니다. 운행 변경은 방문 전 확인하세요."));
    }
    private static LocalTime time(int minute) { return LocalTime.ofSecondOfDay(minute * 60L); }
    private record Leg(Location from, Location to, Instant departure) {}
    public interface TransitLookup { RouteResult route(Location from, Location to, Instant departure); }
    public record Result(MultiDaySchedule schedule, int calls, long millis, List<String> warnings) {}
}
