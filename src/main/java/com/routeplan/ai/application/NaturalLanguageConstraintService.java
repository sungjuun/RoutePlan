package com.routeplan.ai.application;

import com.routeplan.ai.domain.MealType;
import com.routeplan.ai.domain.PlacePreference;
import com.routeplan.ai.domain.TravelConstraints;
import com.routeplan.ai.domain.TravelConstraints.PlaceConstraint;
import com.routeplan.ai.domain.WalkingPreference;
import com.routeplan.trip.application.TripService;
import com.routeplan.trip.application.TripService.ApplyPlaceConstraintCommand;
import com.routeplan.trip.application.TripService.ApplyStructuredConstraintsCommand;
import com.routeplan.trip.application.TripService.TripPlaceResult;
import com.routeplan.trip.application.TripService.TripResult;
import com.routeplan.trip.domain.TransportMode;
import com.routeplan.trip.domain.TripPace;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class NaturalLanguageConstraintService {

    private final TripService tripService;
    private final TravelConstraintInterpreter interpreter;

    public NaturalLanguageConstraintService(
            TripService tripService,
            TravelConstraintInterpreter interpreter
    ) {
        this.tripService = tripService;
        this.interpreter = interpreter;
    }

    public PreviewResult preview(Long tripId, String userRequest) {
        TripResult trip = tripService.get(tripId);
        TravelInterpretationContext context = new TravelInterpretationContext(
                trip.userId(),
                userRequest,
                trip.dailyStartTime(),
                trip.dailyEndTime(),
                trip.pace(),
                trip.transportMode(),
                trip.places().stream()
                        .map(place -> new TravelInterpretationContext.KnownPlace(
                                place.placeId(), place.name()
                        ))
                        .toList()
        );
        TravelConstraints structured = interpreter.interpret(context);
        return resolve(userRequest, interpreter.providerName(), trip, structured);
    }

    public TripResult apply(Long tripId, ApplyProposal proposal) {
        return tripService.applyStructuredConstraints(
                tripId,
                new ApplyStructuredConstraintsCommand(
                        proposal.trip().dailyStartTime(),
                        proposal.trip().dailyEndTime(),
                        proposal.trip().pace(),
                        proposal.trip().transportMode(),
                        proposal.places().stream()
                                .map(place -> new ApplyPlaceConstraintCommand(
                                        place.placeId(),
                                        place.priority(),
                                        place.mustVisit(),
                                        place.preferredStartTime(),
                                        place.preferredEndTime(),
                                        place.minimumStayMinutes(),
                                        place.maximumStayMinutes()
                                ))
                                .toList()
                )
        );
    }

    private PreviewResult resolve(
            String userRequest,
            String provider,
            TripResult trip,
            TravelConstraints structured
    ) {
        List<String> warnings = new ArrayList<>();
        TripSettings beforeTrip = new TripSettings(
                trip.dailyStartTime(), trip.dailyEndTime(), trip.pace(), trip.transportMode()
        );
        LocalTime proposedStart = valueOr(structured.dailyStartTime(), trip.dailyStartTime());
        LocalTime proposedEnd = valueOr(structured.dailyEndTime(), trip.dailyEndTime());
        if (!proposedEnd.isAfter(proposedStart)) {
            warnings.add("해석된 하루 시작·종료 시간이 충돌하여 기존 시간을 유지했습니다.");
            proposedStart = trip.dailyStartTime();
            proposedEnd = trip.dailyEndTime();
        }
        TripSettings afterTrip = new TripSettings(
                proposedStart,
                proposedEnd,
                valueOr(structured.pace(), trip.pace()),
                valueOr(structured.transportMode(), trip.transportMode())
        );

        if (structured.walkingPreference() != null) {
            warnings.add(walkingPreferenceWarning(structured.walkingPreference()));
        }

        List<PlaceChange> placeChanges = new ArrayList<>();
        HashSet<Long> resolvedPlaceIds = new HashSet<>();
        for (PlaceConstraint constraint : structured.placeConstraints()) {
            List<TripPlaceResult> matches = matchPlaces(trip.places(), constraint.placeName());
            if (matches.isEmpty()) {
                warnings.add("현재 여행에서 장소를 찾지 못했습니다: " + safeName(constraint.placeName()));
                continue;
            }
            if (matches.size() > 1) {
                warnings.add("장소명이 모호하여 적용하지 않았습니다: " + safeName(constraint.placeName()));
                continue;
            }
            TripPlaceResult place = matches.getFirst();
            if (!resolvedPlaceIds.add(place.placeId())) {
                warnings.add("같은 장소가 여러 번 해석되어 첫 번째 조건만 사용했습니다: " + place.name());
                continue;
            }
            PlaceSettings before = settings(place);
            PlaceSettings after = resolvePlace(constraint, before, afterTrip, warnings);
            placeChanges.add(new PlaceChange(before, after, !before.equals(after)));
        }

        TripChange tripChange = new TripChange(beforeTrip, afterTrip, !beforeTrip.equals(afterTrip));
        boolean hasChanges = tripChange.changed()
                || placeChanges.stream().anyMatch(PlaceChange::changed);
        return new PreviewResult(
                userRequest,
                provider,
                structured,
                tripChange,
                List.copyOf(placeChanges),
                List.copyOf(warnings),
                hasChanges
        );
    }

    private PlaceSettings resolvePlace(
            PlaceConstraint constraint,
            PlaceSettings current,
            TripSettings trip,
            List<String> warnings
    ) {
        int priority = current.priority();
        boolean mustVisit = current.mustVisit();
        if (constraint.preference() != null) {
            priority = switch (constraint.preference()) {
                case MUST_VISIT -> 100;
                case PREFERRED -> 70;
                case OPTIONAL -> 30;
            };
            mustVisit = constraint.preference() == PlacePreference.MUST_VISIT;
        }

        LocalTime preferredStart = constraint.preferredStartTime();
        LocalTime preferredEnd = constraint.preferredEndTime();
        if (constraint.mealType() != null) {
            MealWindow mealWindow = mealWindow(constraint.mealType());
            if (preferredStart == null) {
                preferredStart = later(mealWindow.start(), trip.dailyStartTime());
            }
            if (preferredEnd == null) {
                preferredEnd = earlier(mealWindow.end(), trip.dailyEndTime());
            }
        }
        preferredStart = valueOr(preferredStart, current.preferredStartTime());
        preferredEnd = valueOr(preferredEnd, current.preferredEndTime());
        if (preferredStart != null && preferredEnd != null && !preferredEnd.isAfter(preferredStart)) {
            warnings.add(current.placeName() + "의 해석된 방문시간이 충돌하여 기존 시간 조건을 유지했습니다.");
            preferredStart = current.preferredStartTime();
            preferredEnd = current.preferredEndTime();
        }

        Integer minimumStay = valueOr(constraint.minimumStayMinutes(), current.minimumStayMinutes());
        Integer maximumStay = valueOr(constraint.maximumStayMinutes(), current.maximumStayMinutes());
        if (minimumStay != null && maximumStay != null && maximumStay < minimumStay) {
            warnings.add(current.placeName() + "의 최소·최대 체류시간이 충돌하여 기존 체류 조건을 유지했습니다.");
            minimumStay = current.minimumStayMinutes();
            maximumStay = current.maximumStayMinutes();
        }
        return new PlaceSettings(
                current.placeId(),
                current.placeName(),
                priority,
                mustVisit,
                preferredStart,
                preferredEnd,
                minimumStay,
                maximumStay
        );
    }

    private List<TripPlaceResult> matchPlaces(List<TripPlaceResult> places, String requestedName) {
        if (requestedName == null || requestedName.isBlank()) {
            return List.of();
        }
        String normalizedRequest = normalize(requestedName);
        List<TripPlaceResult> exact = places.stream()
                .filter(place -> normalize(place.name()).equals(normalizedRequest))
                .toList();
        if (!exact.isEmpty()) {
            return exact;
        }
        return places.stream()
                .filter(place -> normalize(place.name()).contains(normalizedRequest)
                        || normalizedRequest.contains(normalize(place.name())))
                .toList();
    }

    private PlaceSettings settings(TripPlaceResult place) {
        return new PlaceSettings(
                place.placeId(),
                place.name(),
                place.priority(),
                place.mustVisit(),
                place.preferredStartTime(),
                place.preferredEndTime(),
                place.minimumStayMinutes(),
                place.maximumStayMinutes()
        );
    }

    private MealWindow mealWindow(MealType mealType) {
        return switch (mealType) {
            case BREAKFAST -> new MealWindow(LocalTime.of(7, 0), LocalTime.of(10, 0));
            case LUNCH -> new MealWindow(LocalTime.of(11, 30), LocalTime.of(14, 0));
            case DINNER -> new MealWindow(LocalTime.of(17, 30), LocalTime.of(20, 30));
        };
    }

    private String walkingPreferenceWarning(WalkingPreference preference) {
        return switch (preference) {
            case LOW -> "도보 선호 LOW를 인식했습니다. 현재 엔진은 세부 도보량을 직접 적용하지 않으므로 이동수단을 함께 지정해 주세요.";
            case STANDARD -> "도보 선호 STANDARD는 참고 정보이며 현재 최적화 Score에는 직접 반영되지 않습니다.";
            case HIGH -> "도보 선호 HIGH는 참고 정보이며 현재 최적화 Score에는 직접 반영되지 않습니다.";
        };
    }

    private String safeName(String placeName) {
        return placeName == null || placeName.isBlank() ? "(이름 없음)" : placeName.trim();
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private LocalTime later(LocalTime left, LocalTime right) {
        return left.isAfter(right) ? left : right;
    }

    private LocalTime earlier(LocalTime left, LocalTime right) {
        return left.isBefore(right) ? left : right;
    }

    private <T> T valueOr(T value, T fallback) {
        return value == null ? fallback : value;
    }

    public record PreviewResult(
            String originalText,
            String provider,
            TravelConstraints structuredConstraints,
            TripChange trip,
            List<PlaceChange> places,
            List<String> warnings,
            boolean hasChanges
    ) {
    }

    public record TripChange(TripSettings before, TripSettings after, boolean changed) {
    }

    public record TripSettings(
            LocalTime dailyStartTime,
            LocalTime dailyEndTime,
            TripPace pace,
            TransportMode transportMode
    ) {
    }

    public record PlaceChange(PlaceSettings before, PlaceSettings after, boolean changed) {
    }

    public record PlaceSettings(
            Long placeId,
            String placeName,
            int priority,
            boolean mustVisit,
            LocalTime preferredStartTime,
            LocalTime preferredEndTime,
            Integer minimumStayMinutes,
            Integer maximumStayMinutes
    ) {
    }

    public record ApplyProposal(TripSettings trip, List<PlaceSettings> places) {
        public ApplyProposal {
            places = places == null ? List.of() : List.copyOf(places);
        }
    }

    private record MealWindow(LocalTime start, LocalTime end) {
    }
}
