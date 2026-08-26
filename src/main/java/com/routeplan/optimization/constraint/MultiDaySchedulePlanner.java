package com.routeplan.optimization.constraint;

import com.routeplan.optimization.route.RouteProvider;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class MultiDaySchedulePlanner {

    private final ConstraintSchedulePlanner dailyPlanner;

    public MultiDaySchedulePlanner(ConstraintSchedulePlanner dailyPlanner) {
        this.dailyPlanner = dailyPlanner;
    }

    public MultiDaySchedule plan(List<ScheduleRequest> dailyRequests, RouteProvider routeProvider) {
        if (dailyRequests == null || dailyRequests.isEmpty()) {
            throw new IllegalArgumentException("일자별 일정 요청은 한 개 이상이어야 합니다.");
        }
        Objects.requireNonNull(routeProvider, "경로 제공자는 필수입니다.");

        Map<Long, ScheduleCandidate> candidatesByTripPlaceId = new LinkedHashMap<>();
        dailyRequests.getFirst().candidates().forEach(candidate ->
                candidatesByTripPlaceId.put(candidate.tripPlaceId(), candidate)
        );
        Set<Long> remaining = new LinkedHashSet<>(candidatesByTripPlaceId.keySet());
        Map<Long, List<ExclusionReason>> reasonsByTripPlaceId = new HashMap<>();
        List<DailySchedule> days = new ArrayList<>();
        List<ScheduledVisit> visits = new ArrayList<>();

        long totalDistance = 0;
        int totalTravel = 0;
        int totalStay = 0;
        int totalWaiting = 0;
        long totalReturnDistance = 0;
        int totalReturnTravel = 0;
        int sequence = 1;

        for (int index = 0; index < dailyRequests.size(); index++) {
            ScheduleRequest source = dailyRequests.get(index);
            List<ScheduleCandidate> dailyCandidates = source.candidates().stream()
                    .filter(candidate -> remaining.contains(candidate.tripPlaceId()))
                    .toList();
            ScheduleRequest request = new ScheduleRequest(
                    source.visitDate(),
                    source.dailyStartTime(),
                    source.dailyEndTime(),
                    source.startLocation(),
                    source.accommodation(),
                    source.transportMode(),
                    source.algorithm(),
                    dailyCandidates,
                    source.proposedTripPlaceOrder().stream()
                            .filter(remaining::contains)
                            .toList()
            );
            ConstraintSchedule daily = dailyPlanner.planLenient(request, routeProvider);
            List<ScheduledVisit> sequencedVisits = new ArrayList<>();
            for (ScheduledVisit visit : daily.visits()) {
                ScheduledVisit sequenced = new ScheduledVisit(
                        visit.tripPlaceId(), visit.placeId(), sequence++, visit.visitDate(),
                        visit.arrivalTime(), visit.startTime(), visit.endTime(),
                        visit.travelDistanceMeters(), visit.travelMinutes(), visit.waitingMinutes(),
                        visit.stayMinutes(), visit.priority(), visit.mustVisit()
                );
                visits.add(sequenced);
                sequencedVisits.add(sequenced);
                remaining.remove(visit.tripPlaceId());
            }
            for (ExcludedVisit exclusion : daily.exclusions()) {
                dailyCandidates.stream()
                        .filter(candidate -> candidate.placeId() == exclusion.placeId())
                        .findFirst()
                        .ifPresent(candidate -> reasonsByTripPlaceId
                                .computeIfAbsent(candidate.tripPlaceId(), ignored -> new ArrayList<>())
                                .add(exclusion.reason()));
            }

            totalDistance = Math.addExact(totalDistance, daily.totalDistanceMeters());
            totalTravel = Math.addExact(totalTravel, daily.totalTravelMinutes());
            totalStay = Math.addExact(totalStay, daily.totalStayMinutes());
            totalWaiting = Math.addExact(totalWaiting, daily.totalWaitingMinutes());
            totalReturnDistance = Math.addExact(
                    totalReturnDistance, daily.returnTravelDistanceMeters()
            );
            totalReturnTravel = Math.addExact(totalReturnTravel, daily.returnTravelMinutes());
            days.add(new DailySchedule(
                    index + 1,
                    source.visitDate(),
                    sequencedVisits,
                    daily.totalDistanceMeters(),
                    daily.totalTravelMinutes(),
                    daily.totalStayMinutes(),
                    daily.totalWaitingMinutes(),
                    daily.returnTravelDistanceMeters(),
                    daily.returnTravelMinutes(),
                    daily.returnArrivalTime()
            ));
        }

        List<ScheduleCandidate> excludedCandidates = remaining.stream()
                .map(candidatesByTripPlaceId::get)
                .sorted(Comparator
                        .comparing(ScheduleCandidate::mustVisit).reversed()
                        .thenComparing(Comparator.comparingInt(ScheduleCandidate::priority).reversed())
                        .thenComparingLong(ScheduleCandidate::tripPlaceId))
                .toList();
        List<ConstraintViolation> violations = excludedCandidates.stream()
                .filter(ScheduleCandidate::mustVisit)
                .map(candidate -> violation(
                        candidate,
                        reasonFor(reasonsByTripPlaceId.get(candidate.tripPlaceId())),
                        dailyRequests.size()
                ))
                .toList();
        if (!violations.isEmpty()) {
            throw new InfeasibleScheduleException(violations);
        }

        List<ExcludedVisit> exclusions = excludedCandidates.stream()
                .map(candidate -> new ExcludedVisit(
                        candidate.placeId(), candidate.placeName(), candidate.priority(),
                        reasonFor(reasonsByTripPlaceId.get(candidate.tripPlaceId()))
                ))
                .toList();
        int priorityScore = visits.stream().mapToInt(ScheduledVisit::priority).sum();
        int optimizationScore = Math.max(
                0, priorityScore * 10_000 - totalTravel * 5 - totalWaiting * 2
        );
        return new MultiDaySchedule(
                days,
                visits,
                exclusions,
                optimizationScore,
                priorityScore,
                totalDistance,
                totalTravel,
                totalStay,
                totalWaiting,
                totalReturnDistance,
                totalReturnTravel,
                days.getLast().returnArrivalTime()
        );
    }

    private ExclusionReason reasonFor(List<ExclusionReason> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return ExclusionReason.DAILY_LIMIT;
        }
        if (reasons.contains(ExclusionReason.DAILY_LIMIT)) {
            return ExclusionReason.DAILY_LIMIT;
        }
        if (reasons.contains(ExclusionReason.TIME_WINDOW)) {
            return ExclusionReason.TIME_WINDOW;
        }
        return ExclusionReason.CLOSED;
    }

    private ConstraintViolation violation(
            ScheduleCandidate candidate,
            ExclusionReason reason,
            int travelDays
    ) {
        String message = switch (reason) {
            case CLOSED -> travelDays == 1
                    ? candidate.placeName() + "은 여행일에 휴무입니다."
                    : candidate.placeName() + "은 여행 기간 동안 방문 가능한 영업일이 없습니다.";
            case TIME_WINDOW -> candidate.placeName() + "의 영업·선호시간과 체류시간이 충돌합니다.";
            case DAILY_LIMIT -> candidate.placeName() + "을 여행 기간 안에 배치할 시간이 부족합니다.";
        };
        return new ConstraintViolation(candidate.placeId(), candidate.placeName(), reason, message);
    }
}
