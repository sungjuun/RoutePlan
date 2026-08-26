package com.routeplan.ai.application;

import com.routeplan.ai.domain.MealType;
import com.routeplan.ai.domain.PlacePreference;
import com.routeplan.ai.domain.TravelConstraints;
import com.routeplan.ai.domain.TravelConstraints.PlaceConstraint;
import com.routeplan.ai.domain.WalkingPreference;
import com.routeplan.trip.domain.TransportMode;
import com.routeplan.trip.domain.TripPace;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "routeplan.ai",
        name = "provider",
        havingValue = "RULE_BASED",
        matchIfMissing = true
)
public class RuleBasedTravelConstraintInterpreter implements TravelConstraintInterpreter {

    private static final Pattern TIME_PATTERN = Pattern.compile(
            "(오전|아침|오후|저녁|밤)?\\s*(\\d{1,2})\\s*시(?:\\s*(\\d{1,2})\\s*분)?"
    );

    @Override
    public String providerName() {
        return "RULE_BASED";
    }

    @Override
    public TravelConstraints interpret(TravelInterpretationContext context) {
        String text = context.userRequest().trim();
        String normalized = text.toLowerCase(Locale.ROOT);
        TimeRange timeRange = extractDailyTimes(text);
        List<PlaceConstraint> places = context.knownPlaces().stream()
                .filter(place -> containsNormalized(text, place.name()))
                .map(place -> placeConstraint(text, place.name()))
                .toList();
        List<String> notes = new ArrayList<>();
        notes.add("로컬 규칙 기반 해석 결과입니다. OpenAI Provider를 설정하면 더 자유로운 표현을 해석할 수 있습니다.");

        return new TravelConstraints(
                timeRange.startTime(),
                timeRange.endTime(),
                pace(normalized),
                transportMode(normalized),
                walkingPreference(normalized),
                places,
                notes
        );
    }

    private TimeRange extractDailyTimes(String text) {
        LocalTime start = null;
        LocalTime end = null;
        Matcher matcher = TIME_PATTERN.matcher(text);
        while (matcher.find()) {
            LocalTime time = toTime(matcher.group(1), matcher.group(2), matcher.group(3));
            String context = around(text, matcher.start(), matcher.end(), 9);
            if (containsAny(context, "이전", "까지", "종료", "돌아", "복귀", "마쳐")) {
                end = time;
            } else if (containsAny(context, "부터", "시작", "출발", "나서")) {
                start = time;
            }
        }
        return new TimeRange(start, end);
    }

    private PlaceConstraint placeConstraint(String text, String placeName) {
        String context = sentenceContaining(text, placeName);
        PlacePreference preference = containsAny(context, "꼭", "반드시", "필수")
                ? PlacePreference.MUST_VISIT
                : containsAny(context, "별로", "시간 남으면", "시간이 남으면", "가능하면 제외", "우선순위 낮")
                        ? PlacePreference.OPTIONAL
                        : PlacePreference.PREFERRED;
        MealType mealType = containsAny(context, "점심", "런치")
                ? MealType.LUNCH
                : containsAny(context, "저녁", "디너")
                        ? MealType.DINNER
                        : containsAny(context, "아침", "조식") ? MealType.BREAKFAST : null;
        return new PlaceConstraint(
                placeName,
                preference,
                null,
                null,
                null,
                null,
                mealType
        );
    }

    private TripPace pace(String text) {
        if (containsAny(text, "여유", "느긋", "천천히", "쉬엄")) {
            return TripPace.RELAXED;
        }
        if (containsAny(text, "알차", "빡빡", "많이 보고", "빠르게")) {
            return TripPace.ACTIVE;
        }
        if (containsAny(text, "균형", "보통 속도")) {
            return TripPace.STANDARD;
        }
        return null;
    }

    private TransportMode transportMode(String text) {
        if (containsAny(text, "대중교통", "지하철", "버스로", "전철")) {
            return TransportMode.PUBLIC_TRANSIT;
        }
        if (containsAny(text, "자동차", "렌터카", "차로 이동", "운전")) {
            return TransportMode.DRIVING;
        }
        if (containsAny(text, "도보로", "걸어서 이동")) {
            return TransportMode.WALKING;
        }
        return null;
    }

    private WalkingPreference walkingPreference(String text) {
        if (containsAny(text, "많이 걷고 싶지", "걷기 싫", "적게 걷", "도보를 줄")) {
            return WalkingPreference.LOW;
        }
        if (containsAny(text, "많이 걷고 싶", "걷는 게 좋", "도보 중심")) {
            return WalkingPreference.HIGH;
        }
        return null;
    }

    private LocalTime toTime(String period, String hourText, String minuteText) {
        int hour = Integer.parseInt(hourText);
        int minute = minuteText == null ? 0 : Integer.parseInt(minuteText);
        if (minute > 59 || hour > 24 || (hour == 24 && minute > 0)) {
            throw new IllegalArgumentException("자연어에 포함된 시간이 올바르지 않습니다.");
        }
        if (period != null) {
            if ((period.equals("오후") || period.equals("저녁") || period.equals("밤")) && hour < 12) {
                hour += 12;
            } else if ((period.equals("오전") || period.equals("아침")) && hour == 12) {
                hour = 0;
            }
        }
        return hour == 24 ? LocalTime.MIDNIGHT : LocalTime.of(hour, minute);
    }

    private boolean containsNormalized(String source, String target) {
        return normalize(source).contains(normalize(target));
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private String around(String text, int start, int end, int radius) {
        return text.substring(Math.max(0, start - radius), Math.min(text.length(), end + radius));
    }

    private String sentenceContaining(String text, String placeName) {
        for (String sentence : text.split("[.!?\\n]+")) {
            if (containsNormalized(sentence, placeName)) {
                return sentence;
            }
        }
        return text;
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private record TimeRange(LocalTime startTime, LocalTime endTime) {
    }
}
