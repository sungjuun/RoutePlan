package com.routeplan.community.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeplan.community.domain.*;
import com.routeplan.community.persistence.SharedRouteRepository;
import com.routeplan.trip.domain.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class PersonalizationService {
    public enum Interest { CULTURE, NATURE, FOOD, SHOPPING, RELAXATION, ADVENTURE }
    private final JdbcTemplate jdbc;
    private final SharedRouteRepository routes;
    private final ObjectMapper json = new ObjectMapper();
    public PersonalizationService(JdbcTemplate jdbc, SharedRouteRepository routes) { this.jdbc = jdbc; this.routes = routes; }

    public Preferences get(long userId) {
        return jdbc.query("SELECT * FROM user_preferences WHERE user_id=?", (rs, row) -> {
            try {
                Set<Interest> interests = new LinkedHashSet<>();
                for (String value : rs.getString("categories").split(",")) if (!value.isBlank()) interests.add(Interest.valueOf(value));
                return new Preferences(interests, json.readValue(rs.getString("regions"), new TypeReference<List<String>>() {}),
                        rs.getString("pace") == null ? null : TripPace.valueOf(rs.getString("pace")),
                        rs.getString("transport_mode") == null ? null : TransportMode.valueOf(rs.getString("transport_mode")));
            } catch (Exception e) { throw new IllegalStateException("저장된 여행 취향을 읽을 수 없습니다.", e); }
        }, userId).stream().findFirst().orElse(new Preferences(Set.of(), List.of(), null, null));
    }

    @Transactional
    public Preferences save(long userId, Preferences p) {
        if (p == null || p.interests() == null || p.regions() == null || p.interests().stream().anyMatch(Objects::isNull) || p.interests().size() > 6 || p.regions().size() > 10
                || p.regions().stream().anyMatch(r -> r == null || r.isBlank() || r.length() > 100)) throw new IllegalArgumentException("취향 입력을 확인해 주세요.");
        try {
            jdbc.update("""
                    INSERT INTO user_preferences(user_id,categories,regions,pace,transport_mode) VALUES(?,?,?,?,?)
                    ON CONFLICT(user_id) DO UPDATE SET categories=EXCLUDED.categories,regions=EXCLUDED.regions,
                    pace=EXCLUDED.pace,transport_mode=EXCLUDED.transport_mode
                    """, userId, String.join(",", p.interests().stream().map(Enum::name).sorted().toList()),
                    json.writeValueAsString(p.regions().stream().map(String::strip).distinct().toList()),
                    p.pace() == null ? null : p.pace().name(), p.transportMode() == null ? null : p.transportMode().name());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalArgumentException("취향을 저장할 수 없습니다."); }
        return get(userId);
    }

    @Transactional(readOnly = true)
    public List<Recommendation> recommend(long userId) {
        Preferences preferences = get(userId);
        return routes.findDiscoverable(SharedRouteVisibility.PUBLIC, "", null,
                        PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "publishedAt"))).stream()
                .filter(route -> !route.getOwner().getId().equals(userId))
                .map(route -> {
                    List<String> reasons = new ArrayList<>(); int score = 0;
                    if (preferences.regions().stream().anyMatch(r -> route.getRegion().toLowerCase(Locale.ROOT).contains(r.toLowerCase(Locale.ROOT)))) { score += 40; reasons.add("관심 지역"); }
                    if (preferences.pace() == route.getPace()) { score += 15; reasons.add("선호하는 여행 속도"); }
                    if (preferences.transportMode() == route.getTransportMode()) { score += 15; reasons.add("선호 이동수단"); }
                    Set<Interest> matched = new HashSet<>();
                    route.getItems().forEach(item -> {
                        Interest interest = interest(item.getPlace().getCategory());
                        if (interest != null && preferences.interests().contains(interest)) matched.add(interest);
                    });
                    score += matched.size() * 20;
                    if (!matched.isEmpty()) reasons.add("관심 장소 유형 " + matched.size() + "개 일치");
                    if (reasons.isEmpty()) reasons.add("취향과 일치하는 정보가 부족해 공개 루트 인기도 기준");
                    return new Recommendation(SharedRouteSummaryView.from(route), score, List.copyOf(reasons));
                }).sorted(Comparator.comparingInt(Recommendation::score).reversed()
                        .thenComparing(Comparator.comparingLong((Recommendation r) -> r.route().likeCount()).reversed())
                        .thenComparing(Comparator.comparingLong((Recommendation r) -> r.route().copyCount()).reversed())
                        .thenComparing(r -> r.route().routeId(), Comparator.reverseOrder())).limit(6).toList();
    }

    public static Interest interest(String category) {
        if (category == null) return null;
        String c = category.toLowerCase(Locale.ROOT);
        if (c.matches(".*(museum|gallery|historic|temple|palace|culture|미술|박물|궁궐|사찰).*")) return Interest.CULTURE;
        if (c.matches(".*(park|garden|beach|nature|공원|자연|해변).*")) return Interest.NATURE;
        if (c.matches(".*(restaurant|cafe|food|bakery|음식|카페|식당).*")) return Interest.FOOD;
        if (c.matches(".*(shop|store|mall|market|쇼핑|시장).*")) return Interest.SHOPPING;
        if (c.matches(".*(spa|resort|relax|휴식|온천).*")) return Interest.RELAXATION;
        if (c.matches(".*(hiking|amusement|adventure|등산|체험).*")) return Interest.ADVENTURE;
        return null;
    }
    public record Preferences(Set<Interest> interests, List<String> regions, TripPace pace, TransportMode transportMode) {}
    public record Recommendation(SharedRouteSummaryView route, int score, List<String> reasons) {}
}
