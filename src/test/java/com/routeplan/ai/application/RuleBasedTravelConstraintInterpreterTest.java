package com.routeplan.ai.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.routeplan.ai.domain.MealType;
import com.routeplan.ai.domain.PlacePreference;
import com.routeplan.ai.domain.TravelConstraints;
import com.routeplan.ai.domain.WalkingPreference;
import com.routeplan.trip.domain.TransportMode;
import com.routeplan.trip.domain.TripPace;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuleBasedTravelConstraintInterpreterTest {

    private final RuleBasedTravelConstraintInterpreter interpreter =
            new RuleBasedTravelConstraintInterpreter();

    @Test
    void extractsOnlySupportedConstraintsFromKoreanRequest() {
        TravelConstraints result = interpreter.interpret(new TravelInterpretationContext(
                1L,
                """
                        오전 10시에 출발하고 저녁 7시까지 여행할래.
                        오사카성은 꼭 가야 해. PORTER 오사카도 들르고 싶어.
                        점심은 이치란 라멘을 먹고 싶어. 대중교통을 이용하고 많이 걷고 싶지 않아.
                        일정은 여유롭게 해줘.
                        """,
                LocalTime.of(9, 0),
                LocalTime.of(20, 0),
                TripPace.STANDARD,
                TransportMode.WALKING,
                List.of(
                        new TravelInterpretationContext.KnownPlace(1L, "오사카성"),
                        new TravelInterpretationContext.KnownPlace(2L, "PORTER 오사카"),
                        new TravelInterpretationContext.KnownPlace(3L, "이치란 라멘")
                )
        ));

        assertThat(result.dailyStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(result.dailyEndTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(result.pace()).isEqualTo(TripPace.RELAXED);
        assertThat(result.transportMode()).isEqualTo(TransportMode.PUBLIC_TRANSIT);
        assertThat(result.walkingPreference()).isEqualTo(WalkingPreference.LOW);
        assertThat(result.placeConstraints()).hasSize(3);
        assertThat(result.placeConstraints().get(0).preference())
                .isEqualTo(PlacePreference.MUST_VISIT);
        assertThat(result.placeConstraints().get(1).preference())
                .isEqualTo(PlacePreference.PREFERRED);
        assertThat(result.placeConstraints().get(2).mealType()).isEqualTo(MealType.LUNCH);
    }

    @Test
    void leavesUnmentionedTripSettingsNull() {
        TravelConstraints result = interpreter.interpret(new TravelInterpretationContext(
                1L,
                "오사카성은 시간이 남으면 가고 싶어.",
                LocalTime.of(9, 0),
                LocalTime.of(20, 0),
                TripPace.STANDARD,
                TransportMode.WALKING,
                List.of(new TravelInterpretationContext.KnownPlace(1L, "오사카성"))
        ));

        assertThat(result.dailyStartTime()).isNull();
        assertThat(result.dailyEndTime()).isNull();
        assertThat(result.pace()).isNull();
        assertThat(result.transportMode()).isNull();
        assertThat(result.placeConstraints().getFirst().preference())
                .isEqualTo(PlacePreference.OPTIONAL);
    }
}
