package com.routeplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.routeplan.auth.AuthenticatedMockMvc;
import com.routeplan.common.observability.CorrelationIdFilter;
import com.routeplan.optimization.domain.Location;
import com.routeplan.optimization.domain.RouteResult;
import com.routeplan.optimization.route.cache.PostgisRouteLegCache;
import com.routeplan.optimization.route.cache.RouteCacheKey;
import com.routeplan.user.application.UserService;
import com.routeplan.trip.domain.TransportMode;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = "routeplan.route.cache.persistent-enabled=true")
@AutoConfigureMockMvc
@Testcontainers
class RoutePlanApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = com.routeplan.testsupport.PostgisTestContainer.create();

    @Autowired
    private MockMvc rawMockMvc;

    private AuthenticatedMockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private PostgisRouteLegCache persistentRouteCache;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpSecurity() {
        mockMvc = new AuthenticatedMockMvc(rawMockMvc);
    }

    @Test
    void migratesPostgisAndPersistsTimeBucketedRouteLegs() {
        assertThat(jdbcTemplate.queryForObject("SELECT PostGIS_Version()", String.class)).isNotBlank();
        RouteCacheKey first = new RouteCacheKey(
                Location.of(BigDecimal.valueOf(37.5665), BigDecimal.valueOf(126.9780)),
                Location.of(BigDecimal.valueOf(37.5796), BigDecimal.valueOf(126.9770)),
                TransportMode.DRIVING,
                Instant.parse("2026-09-10T00:05:00Z"));
        RouteCacheKey sameBucket = new RouteCacheKey(
                first.origin(), first.destination(), first.transportMode(),
                Instant.parse("2026-09-10T00:10:00Z"));

        assertThat(persistentRouteCache.putAll(Map.of(first, new RouteResult(2_100, 17)))).isZero();
        assertThat(persistentRouteCache.getAll(Set.of(sameBucket)).routes())
                .containsEntry(sameBucket, new RouteResult(2_100, 17));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT ST_SRID(origin::geometry) FROM route_leg_cache WHERE transport_mode = 'DRIVING' LIMIT 1",
                Integer.class)).isEqualTo(4326);
    }

    @Test
    void exposesReadinessAndPropagatesCorrelationIdIntoErrors() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        postAndReadId("/api/v1/users", "{\"nickname\":\"correlation-user\"}");

        mockMvc.perform(get("/api/v1/trips/{tripId}", 999_999)
                        .header(CorrelationIdFilter.HEADER_NAME, "routeplan-test-123"))
                .andExpect(status().isNotFound())
                .andExpect(result -> assertThat(
                        result.getResponse().getHeader(CorrelationIdFilter.HEADER_NAME)
                ).isEqualTo("routeplan-test-123"))
                .andExpect(jsonPath("$.correlationId").value("routeplan-test-123"));
    }

    @Test
    void createsTripOptimizesTwiceAndReturnsLatestVersion() throws Exception {
        long userId = postAndReadId("/api/v1/users", """
                {"nickname":"route-tester"}
                """);
        long osakaCastleId = postAndReadId("/api/v1/places", """
                {
                  "name":"오사카성",
                  "latitude":34.687300,
                  "longitude":135.526200,
                  "category":"ATTRACTION"
                }
                """);
        long dotonboriId = postAndReadId("/api/v1/places", """
                {
                  "name":"도톤보리",
                  "latitude":34.668700,
                  "longitude":135.501300,
                  "category":"FOOD"
                }
                """);
        long tripId = postAndReadId("/api/v1/trips", """
                {
                  "userId":%d,
                  "name":"오사카 하루 여행",
                  "startDate":"2026-09-10",
                  "endDate":"2026-09-10",
                  "accommodationName":"난바 숙소",
                  "accommodationLatitude":34.665400,
                  "accommodationLongitude":135.501900,
                  "transportMode":"PUBLIC_TRANSIT"
                }
                """.formatted(userId));

        addPlace(tripId, osakaCastleId);
        addPlace(tripId, dotonboriId);
        mockMvc.perform(post("/api/v1/trips/{tripId}/places", tripId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\":" + dotonboriId + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_TRIP_PLACE"));

        mockMvc.perform(post("/api/v1/trips/{tripId}/optimize", tripId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.algorithm").value("NEAREST_NEIGHBOR"))
                .andExpect(jsonPath("$.closedTour").value(true))
                .andExpect(jsonPath("$.returnArrivalTime").exists())
                .andExpect(jsonPath("$.totalStayMinutes").value(120))
                .andExpect(jsonPath("$.routeDataType").value("STRAIGHT_LINE_ESTIMATE"))
                .andExpect(jsonPath("$.routeProviderCallCount").value(0))
                .andExpect(jsonPath("$.routeMatrixElementCount").value(9))
                .andExpect(jsonPath("$.routeMatrixBuildMillis").isNumber())
                .andExpect(jsonPath("$.routeCacheEnabled").value(false))
                .andExpect(jsonPath("$.routeCacheHitCount").value(0))
                .andExpect(jsonPath("$.routeCacheMissCount").value(0))
                .andExpect(jsonPath("$.routeCacheFailureCount").value(0))
                .andExpect(jsonPath("$.routeCacheHitRatio").value(0.0))
                .andExpect(jsonPath("$.items[0].placeId").value(osakaCastleId))
                .andExpect(jsonPath("$.items[1].placeId").value(dotonboriId));

        mockMvc.perform(post("/api/v1/trips/{tripId}/optimize", tripId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(post("/api/v1/trips/{tripId}/optimize", tripId)
                        .queryParam("algorithm", "EXACT_SEARCH"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.algorithm").value("EXACT_SEARCH"));

        mockMvc.perform(post("/api/v1/trips/{tripId}/optimize", tripId)
                        .queryParam("algorithm", "NEAREST_NEIGHBOR_2_OPT"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(4))
                .andExpect(jsonPath("$.algorithm").value("NEAREST_NEIGHBOR_2_OPT"));

        mockMvc.perform(get("/api/v1/trips/{tripId}/itineraries/latest", tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(4))
                .andExpect(jsonPath("$.algorithm").value("NEAREST_NEIGHBOR_2_OPT"))
                .andExpect(jsonPath("$.items.length()").value(2));

        mockMvc.perform(get("/api/v1/trips/{tripId}", tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPTIMIZED"));

        assertThat(meterRegistry.find("routeplan.itinerary.generation.duration")
                .tags(
                        "type", "optimization",
                        "algorithm", "NEAREST_NEIGHBOR_2_OPT",
                        "outcome", "success"
                )
                .timer().count()).isGreaterThanOrEqualTo(1);
        assertThat(meterRegistry.find("routeplan.route.matrix.build.duration")
                .tag("data_type", "STRAIGHT_LINE_ESTIMATE")
                .timer().count()).isGreaterThanOrEqualTo(1);
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("routeplan_itinerary_generation_duration_seconds_count"));
    }

    @Test
    void createsMultiDayTripAndRejectsOptimizationWithoutPlaces() throws Exception {
        long userId = postAndReadId("/api/v1/users", """
                {"nickname":"boundary-tester"}
                """);

        mockMvc.perform(post("/api/v1/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId":%d,
                                  "name":"다일 여행",
                                  "startDate":"2026-09-10",
                                  "endDate":"2026-09-11",
                                  "accommodationName":"난바 숙소",
                                  "accommodationLatitude":34.665400,
                                  "accommodationLongitude":135.501900,
                                  "transportMode":"WALKING"
                                }
                                """.formatted(userId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startDate").value("2026-09-10"))
                .andExpect(jsonPath("$.endDate").value("2026-09-11"));

        long emptyTripId = postAndReadId("/api/v1/trips", """
                {
                  "userId":%d,
                  "name":"빈 여행",
                  "startDate":"2026-09-10",
                  "endDate":"2026-09-10",
                  "accommodationName":"난바 숙소",
                  "accommodationLatitude":34.665400,
                  "accommodationLongitude":135.501900,
                  "transportMode":"WALKING"
                }
                """.formatted(userId));

        mockMvc.perform(post("/api/v1/trips/{tripId}/optimize", emptyTripId))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TRIP_HAS_NO_PLACES"));
        assertThat(meterRegistry.find("routeplan.itinerary.generation.total")
                .tags("type", "optimization", "outcome", "failure")
                .counter().count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void distributesPlacesAcrossDatesAndPersistsDailySummaries() throws Exception {
        long userId = postAndReadId("/api/v1/users", """
                {"nickname":"multi-day-tester"}
                """);
        long firstPlaceId = postAndReadId("/api/v1/places", """
                {
                  "name":"첫날 우선 장소",
                  "latitude":37.566500,
                  "longitude":126.978000,
                  "averageStayMinutes":90
                }
                """);
        long secondPlaceId = postAndReadId("/api/v1/places", """
                {
                  "name":"둘째 날 이월 장소",
                  "latitude":37.566500,
                  "longitude":126.978000,
                  "averageStayMinutes":90
                }
                """);
        long tripId = postAndReadId("/api/v1/trips", """
                {
                  "userId":%d,
                  "name":"서울 이틀 여행",
                  "startDate":"2026-09-10",
                  "endDate":"2026-09-11",
                  "dailyStartTime":"09:00",
                  "dailyEndTime":"11:00",
                  "accommodationName":"서울 숙소",
                  "accommodationLatitude":37.566500,
                  "accommodationLongitude":126.978000,
                  "transportMode":"WALKING"
                }
                """.formatted(userId));
        addPlace(tripId, firstPlaceId);
        addPlace(tripId, secondPlaceId);

        MvcResult optimized = mockMvc.perform(post("/api/v1/trips/{tripId}/optimize", tripId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.days.length()").value(2))
                .andExpect(jsonPath("$.days[0].dayNumber").value(1))
                .andExpect(jsonPath("$.days[0].visitDate").value("2026-09-10"))
                .andExpect(jsonPath("$.days[1].dayNumber").value(2))
                .andExpect(jsonPath("$.days[1].visitDate").value("2026-09-11"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].visitDate").value("2026-09-10"))
                .andExpect(jsonPath("$.items[1].visitDate").value("2026-09-11"))
                .andExpect(jsonPath("$.exclusions.length()").value(0))
                .andReturn();
        Number itineraryId = JsonPath.read(
                optimized.getResponse().getContentAsString(), "$.itineraryId"
        );
        Number firstDayItemId = JsonPath.read(
                optimized.getResponse().getContentAsString(), "$.items[0].itineraryItemId"
        );

        mockMvc.perform(get("/api/v1/itineraries/{itineraryId}", itineraryId.longValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(2))
                .andExpect(jsonPath("$.days[1].returnArrivalTime").value("10:30:00"));

        mockMvc.perform(post("/api/v1/trips/{tripId}/reoptimize", tripId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceItineraryId":%d,
                                  "currentDate":"2026-09-11",
                                  "currentTime":"09:00",
                                  "currentLatitude":37.566500,
                                  "currentLongitude":126.978000,
                                  "completedItemIds":[],
                                  "reason":"USER_REQUEST"
                                }
                                """.formatted(itineraryId.longValue())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_REOPTIMIZATION_STATE"));

        mockMvc.perform(post("/api/v1/trips/{tripId}/reoptimize", tripId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceItineraryId":%d,
                                  "currentDate":"2026-09-11",
                                  "currentTime":"09:00",
                                  "currentLatitude":37.566500,
                                  "currentLongitude":126.978000,
                                  "completedItemIds":[%d],
                                  "reason":"USER_REQUEST",
                                  "reasonDetail":"둘째 날 동선 변경"
                                }
                                """.formatted(
                                itineraryId.longValue(), firstDayItemId.longValue()
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.generationType").value("REOPTIMIZATION"))
                .andExpect(jsonPath("$.reoptimizationStartDate").value("2026-09-11"))
                .andExpect(jsonPath("$.reoptimizationStartTime").value("09:00:00"))
                .andExpect(jsonPath("$.days.length()").value(2))
                .andExpect(jsonPath("$.days[0].visitDate").value("2026-09-10"))
                .andExpect(jsonPath("$.days[0].totalStayMinutes").value(90))
                .andExpect(jsonPath("$.days[1].visitDate").value("2026-09-11"))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].visitDate").value("2026-09-10"))
                .andExpect(jsonPath("$.items[1].status").value("PLANNED"))
                .andExpect(jsonPath("$.items[1].visitDate").value("2026-09-11"))
                .andExpect(jsonPath("$.totalStayMinutes").value(180));
        assertThat(meterRegistry.find("routeplan.itinerary.reoptimization.total")
                .tags("algorithm", "NEAREST_NEIGHBOR", "outcome", "success")
                .counter().count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void rejectsExactSearchForMoreThanTenPlaces() throws Exception {
        long userId = postAndReadId("/api/v1/users", """
                {"nickname":"exact-limit-tester"}
                """);
        long tripId = postAndReadId("/api/v1/trips", """
                {
                  "userId":%d,
                  "name":"Exact Search 제한 검증",
                  "startDate":"2026-09-10",
                  "endDate":"2026-09-10",
                  "accommodationName":"서울 숙소",
                  "accommodationLatitude":37.566500,
                  "accommodationLongitude":126.978000,
                  "transportMode":"WALKING"
                }
                """.formatted(userId));

        for (int index = 1; index <= 11; index++) {
            String latitude = "37.5" + String.format("%04d", index);
            long placeId = postAndReadId("/api/v1/places", """
                    {
                      "name":"장소 %d",
                      "latitude":%s,
                      "longitude":126.978000
                    }
                    """.formatted(index, latitude));
            addPlace(tripId, placeId);
        }

        mockMvc.perform(post("/api/v1/trips/{tripId}/optimize", tripId)
                        .queryParam("algorithm", "EXACT_SEARCH"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("EXACT_SEARCH_LIMIT_EXCEEDED"));
    }

    @Test
    void appliesTimeWindowPriorityAndMustVisitConstraints() throws Exception {
        long userId = postAndReadId("/api/v1/users", """
                {"nickname":"constraint-tester"}
                """);
        long highPriorityId = postAndReadId("/api/v1/places", """
                {
                  "name":"예약 전시",
                  "latitude":34.665400,
                  "longitude":135.501900,
                  "averageStayMinutes":90
                }
                """);
        long lowPriorityId = postAndReadId("/api/v1/places", """
                {
                  "name":"선택 카페",
                  "latitude":34.665400,
                  "longitude":135.501900,
                  "averageStayMinutes":90
                }
                """);
        setOpeningHour(highPriorityId, "THURSDAY", """
                {"closed":false,"openTime":"09:30","closeTime":"11:00"}
                """);
        long tripId = postAndReadId("/api/v1/trips", """
                {
                  "userId":%d,
                  "name":"제약 일정",
                  "startDate":"2026-09-10",
                  "endDate":"2026-09-10",
                  "dailyStartTime":"09:00",
                  "dailyEndTime":"11:00",
                  "accommodationName":"난바 숙소",
                  "accommodationLatitude":34.665400,
                  "accommodationLongitude":135.501900,
                  "transportMode":"WALKING",
                  "pace":"STANDARD"
                }
                """.formatted(userId));

        mockMvc.perform(post("/api/v1/trips/{tripId}/places", tripId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "placeId":%d,
                                  "priority":100,
                                  "mustVisit":true,
                                  "minimumStayMinutes":90,
                                  "maximumStayMinutes":90
                                }
                                """.formatted(highPriorityId)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/trips/{tripId}/places", tripId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "placeId":%d,
                                  "priority":10,
                                  "mustVisit":false,
                                  "minimumStayMinutes":90,
                                  "maximumStayMinutes":90
                                }
                                """.formatted(lowPriorityId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/trips/{tripId}/optimize", tripId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].placeId").value(highPriorityId))
                .andExpect(jsonPath("$.items[0].arrivalTime").value("09:00:00"))
                .andExpect(jsonPath("$.items[0].startTime").value("09:30:00"))
                .andExpect(jsonPath("$.items[0].endTime").value("11:00:00"))
                .andExpect(jsonPath("$.items[0].waitingMinutes").value(30))
                .andExpect(jsonPath("$.visitedPriorityScore").value(100))
                .andExpect(jsonPath("$.exclusions[0].placeId").value(lowPriorityId))
                .andExpect(jsonPath("$.exclusions[0].reason").value("DAILY_LIMIT"));
    }

    @Test
    void explainsClosedMustVisitConflict() throws Exception {
        long userId = postAndReadId("/api/v1/users", """
                {"nickname":"must-visit-tester"}
                """);
        long closedPlaceId = postAndReadId("/api/v1/places", """
                {
                  "name":"목요일 휴무 박물관",
                  "latitude":34.665400,
                  "longitude":135.501900,
                  "averageStayMinutes":60
                }
                """);
        setOpeningHour(closedPlaceId, "THURSDAY", """
                {"closed":true}
                """);
        long tripId = postAndReadId("/api/v1/trips", """
                {
                  "userId":%d,
                  "name":"Must Visit 충돌",
                  "startDate":"2026-09-10",
                  "endDate":"2026-09-10",
                  "dailyStartTime":"09:00",
                  "dailyEndTime":"20:00",
                  "accommodationName":"난바 숙소",
                  "accommodationLatitude":34.665400,
                  "accommodationLongitude":135.501900,
                  "transportMode":"WALKING"
                }
                """.formatted(userId));
        mockMvc.perform(post("/api/v1/trips/{tripId}/places", tripId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"placeId":%d,"priority":100,"mustVisit":true}
                                """.formatted(closedPlaceId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/trips/{tripId}/optimize", tripId))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INFEASIBLE_MUST_VISIT"))
                .andExpect(jsonPath("$.violations[0].placeId").value(closedPlaceId))
                .andExpect(jsonPath("$.violations[0].reason").value("CLOSED"))
                .andExpect(jsonPath("$.violations[0].message").value("목요일 휴무 박물관은 여행일에 휴무입니다."));
    }

    @Test
    void importsExternalPlaceIdempotentlyAndExplainsDisabledSearchProvider() throws Exception {
        postAndReadId("/api/v1/users", "{\"nickname\":\"place-import-user\"}");

        mockMvc.perform(get("/api/v1/places/search")
                        .queryParam("query", "오사카성"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EXTERNAL_PROVIDER_NOT_CONFIGURED"));

        String body = """
                {
                  "externalPlaceId":"google-osaka-castle-import",
                  "name":"오사카성 외부 검색",
                  "latitude":34.687300,
                  "longitude":135.526200,
                  "category":"historical_landmark",
                  "averageStayMinutes":120
                }
                """;
        MvcResult created = mockMvc.perform(post("/api/v1/places/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalPlaceId").value("google-osaka-castle-import"))
                .andReturn();
        Number placeId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/v1/places/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(placeId.longValue()));
    }

    @Test
    void reoptimizesOnlyRemainingPlacesAndPreservesCompletedPrefix() throws Exception {
        long userId = postAndReadId("/api/v1/users", """
                {"nickname":"reoptimization-tester"}
                """);
        long completedPlaceId = postAndReadId("/api/v1/places", """
                {
                  "name":"완료 장소 A",
                  "latitude":34.665400,
                  "longitude":135.501900,
                  "averageStayMinutes":60
                }
                """);
        long removedPlaceId = postAndReadId("/api/v1/places", """
                {
                  "name":"삭제 장소 B",
                  "latitude":34.665400,
                  "longitude":135.501900,
                  "averageStayMinutes":60
                }
                """);
        long addedPlaceId = postAndReadId("/api/v1/places", """
                {
                  "name":"추가 장소 C",
                  "latitude":34.665400,
                  "longitude":135.501900,
                  "averageStayMinutes":60
                }
                """);
        long tripId = postAndReadId("/api/v1/trips", """
                {
                  "userId":%d,
                  "name":"재최적화 여행",
                  "startDate":"2026-09-10",
                  "endDate":"2026-09-10",
                  "dailyStartTime":"09:00",
                  "dailyEndTime":"20:00",
                  "accommodationName":"난바 숙소",
                  "accommodationLatitude":34.665400,
                  "accommodationLongitude":135.501900,
                  "transportMode":"WALKING"
                }
                """.formatted(userId));
        addPlace(tripId, completedPlaceId);
        addPlace(tripId, removedPlaceId);

        MvcResult initialResult = mockMvc.perform(post("/api/v1/trips/{tripId}/optimize", tripId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.generationType").value("INITIAL_OPTIMIZATION"))
                .andExpect(jsonPath("$.parentItineraryId").doesNotExist())
                .andExpect(jsonPath("$.items[0].status").value("PLANNED"))
                .andExpect(jsonPath("$.items[1].status").value("PLANNED"))
                .andReturn();
        String initialJson = initialResult.getResponse().getContentAsString();
        Number sourceItineraryId = JsonPath.read(initialJson, "$.itineraryId");
        Number completedItemId = JsonPath.read(initialJson, "$.items[0].itineraryItemId");
        String completedStartTime = JsonPath.read(initialJson, "$.items[0].startTime");
        String completedEndTime = JsonPath.read(initialJson, "$.items[0].endTime");

        mockMvc.perform(delete("/api/v1/trips/{tripId}/places/{placeId}", tripId, removedPlaceId))
                .andExpect(status().isNoContent());
        addPlace(tripId, addedPlaceId);

        String reoptimizeBody = """
                {
                  "sourceItineraryId":%d,
                  "currentTime":"11:00",
                  "currentLatitude":34.665400,
                  "currentLongitude":135.501900,
                  "completedItemIds":[%d],
                  "reason":"DELAY",
                  "reasonDetail":"첫 장소에서 한 시간 지연"
                }
                """.formatted(sourceItineraryId.longValue(), completedItemId.longValue());
        MvcResult reoptimizedResult = mockMvc.perform(
                        post("/api/v1/trips/{tripId}/reoptimize", tripId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(reoptimizeBody)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.generationType").value("REOPTIMIZATION"))
                .andExpect(jsonPath("$.parentItineraryId").value(sourceItineraryId.longValue()))
                .andExpect(jsonPath("$.changeReason").value("DELAY"))
                .andExpect(jsonPath("$.changeReasonDetail").value("첫 장소에서 한 시간 지연"))
                .andExpect(jsonPath("$.reoptimizationStartDate").value("2026-09-10"))
                .andExpect(jsonPath("$.reoptimizationStartTime").value("11:00:00"))
                .andExpect(jsonPath("$.reoptimizationStartLatitude").value(34.6654))
                .andExpect(jsonPath("$.reoptimizationStartLongitude").value(135.5019))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].placeId").value(completedPlaceId))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].startTime").value(completedStartTime))
                .andExpect(jsonPath("$.items[0].endTime").value(completedEndTime))
                .andExpect(jsonPath("$.items[1].placeId").value(addedPlaceId))
                .andExpect(jsonPath("$.items[1].status").value("PLANNED"))
                .andExpect(jsonPath("$.items[1].startTime").value("11:00:00"))
                .andExpect(jsonPath("$.totalStayMinutes").value(120))
                .andReturn();
        String reoptimizedJson = reoptimizedResult.getResponse().getContentAsString();
        Number reoptimizedItineraryId = JsonPath.read(reoptimizedJson, "$.itineraryId");
        Number firstV2ItemId = JsonPath.read(reoptimizedJson, "$.items[0].itineraryItemId");
        Number secondV2ItemId = JsonPath.read(reoptimizedJson, "$.items[1].itineraryItemId");

        mockMvc.perform(get("/api/v1/itineraries/{itineraryId}", sourceItineraryId.longValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.items[0].status").value("PLANNED"))
                .andExpect(jsonPath("$.items[1].placeId").value(removedPlaceId));

        mockMvc.perform(post("/api/v1/trips/{tripId}/reoptimize", tripId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reoptimizeBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REOPTIMIZATION_SOURCE_NOT_LATEST"));

        mockMvc.perform(post("/api/v1/trips/{tripId}/reoptimize", tripId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceItineraryId":%d,
                                  "currentTime":"13:00",
                                  "currentLatitude":34.665400,
                                  "currentLongitude":135.501900,
                                  "completedItemIds":[%d],
                                  "reason":"USER_REQUEST"
                                }
                                """.formatted(
                                reoptimizedItineraryId.longValue(),
                                secondV2ItemId.longValue()
                        )))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_REOPTIMIZATION_STATE"));

        mockMvc.perform(post("/api/v1/trips/{tripId}/reoptimize", tripId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceItineraryId":%d,
                                  "currentTime":"13:00",
                                  "currentLatitude":34.665400,
                                  "currentLongitude":135.501900,
                                  "completedItemIds":[%d,%d],
                                  "reason":"USER_REQUEST",
                                  "reasonDetail":"모든 방문 완료"
                                }
                                """.formatted(
                                reoptimizedItineraryId.longValue(),
                                firstV2ItemId.longValue(),
                                secondV2ItemId.longValue()
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.parentItineraryId").value(reoptimizedItineraryId.longValue()))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[1].status").value("COMPLETED"))
                .andExpect(jsonPath("$.returnArrivalTime").value("13:00:00"));
    }

    @Test
    void previewsAndAppliesValidatedNaturalLanguageConstraints() throws Exception {
        long userId = postAndReadId("/api/v1/users", """
                {"nickname":"natural-language-tester"}
                """);
        long osakaCastleId = postAndReadId("/api/v1/places", """
                {
                  "name":"자연어 오사카성",
                  "latitude":34.687300,
                  "longitude":135.526200,
                  "category":"ATTRACTION"
                }
                """);
        long ramenId = postAndReadId("/api/v1/places", """
                {
                  "name":"자연어 이치란 라멘",
                  "latitude":34.668700,
                  "longitude":135.501300,
                  "category":"FOOD"
                }
                """);
        long outsideTripPlaceId = postAndReadId("/api/v1/places", """
                {
                  "name":"다른 여행의 장소",
                  "latitude":34.680000,
                  "longitude":135.510000,
                  "category":"CAFE"
                }
                """);
        long tripId = postAndReadId("/api/v1/trips", """
                {
                  "userId":%d,
                  "name":"자연어 조건 여행",
                  "startDate":"2026-09-10",
                  "endDate":"2026-09-10",
                  "dailyStartTime":"09:00",
                  "dailyEndTime":"20:00",
                  "accommodationName":"난바 숙소",
                  "accommodationLatitude":34.665400,
                  "accommodationLongitude":135.501900,
                  "transportMode":"WALKING",
                  "pace":"STANDARD"
                }
                """.formatted(userId));
        addPlace(tripId, osakaCastleId);
        addPlace(tripId, ramenId);

        mockMvc.perform(post("/api/v1/trips/{tripId}/natural-language/preview", tripId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "text":"오전 10시에 출발하고 저녁 7시까지 여행할래. 자연어 오사카성은 꼭 가야 해. 점심은 자연어 이치란 라멘을 먹고 싶어. 대중교통을 이용하고 많이 걷고 싶지 않아. 일정은 여유롭게 해줘."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("RULE_BASED"))
                .andExpect(jsonPath("$.structuredConstraints.walkingPreference").value("LOW"))
                .andExpect(jsonPath("$.trip.after.dailyStartTime").value("10:00:00"))
                .andExpect(jsonPath("$.trip.after.dailyEndTime").value("19:00:00"))
                .andExpect(jsonPath("$.trip.after.pace").value("RELAXED"))
                .andExpect(jsonPath("$.trip.after.transportMode").value("PUBLIC_TRANSIT"))
                .andExpect(jsonPath("$.places.length()").value(2))
                .andExpect(jsonPath("$.places[0].after.mustVisit").value(true))
                .andExpect(jsonPath("$.places[0].after.priority").value(100))
                .andExpect(jsonPath("$.places[1].after.preferredStartTime").value("11:30:00"))
                .andExpect(jsonPath("$.places[1].after.preferredEndTime").value("14:00:00"))
                .andExpect(jsonPath("$.hasChanges").value(true));

        mockMvc.perform(post("/api/v1/trips/{tripId}/natural-language/apply", tripId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "trip":{
                                    "dailyStartTime":"10:00",
                                    "dailyEndTime":"19:00",
                                    "pace":"RELAXED",
                                    "transportMode":"PUBLIC_TRANSIT"
                                  },
                                  "places":[
                                    {
                                      "placeId":%d,
                                      "priority":100,
                                      "mustVisit":true,
                                      "preferredStartTime":null,
                                      "preferredEndTime":null,
                                      "minimumStayMinutes":null,
                                      "maximumStayMinutes":null
                                    },
                                    {
                                      "placeId":%d,
                                      "priority":70,
                                      "mustVisit":false,
                                      "preferredStartTime":null,
                                      "preferredEndTime":null,
                                      "minimumStayMinutes":null,
                                      "maximumStayMinutes":null
                                    }
                                  ]
                                }
                                """.formatted(osakaCastleId, outsideTripPlaceId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_PLACE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/trips/{tripId}", tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyStartTime").value("09:00:00"))
                .andExpect(jsonPath("$.places[0].priority").value(50));

        mockMvc.perform(post("/api/v1/trips/{tripId}/natural-language/apply", tripId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "trip":{
                                    "dailyStartTime":"10:00",
                                    "dailyEndTime":"19:00",
                                    "pace":"RELAXED",
                                    "transportMode":"PUBLIC_TRANSIT"
                                  },
                                  "places":[
                                    {
                                      "placeId":%d,
                                      "priority":100,
                                      "mustVisit":true,
                                      "preferredStartTime":null,
                                      "preferredEndTime":null,
                                      "minimumStayMinutes":null,
                                      "maximumStayMinutes":null
                                    },
                                    {
                                      "placeId":%d,
                                      "priority":70,
                                      "mustVisit":false,
                                      "preferredStartTime":"11:30",
                                      "preferredEndTime":"14:00",
                                      "minimumStayMinutes":null,
                                      "maximumStayMinutes":null
                                    }
                                  ]
                                }
                                """.formatted(osakaCastleId, ramenId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyStartTime").value("10:00:00"))
                .andExpect(jsonPath("$.dailyEndTime").value("19:00:00"))
                .andExpect(jsonPath("$.pace").value("RELAXED"))
                .andExpect(jsonPath("$.transportMode").value("PUBLIC_TRANSIT"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.places[0].mustVisit").value(true))
                .andExpect(jsonPath("$.places[1].preferredStartTime").value("11:30:00"));
    }

    @Test
    void storesOwnedWeatherAndMovesOutdoorPlaceToTheClearDay() throws Exception {
        long ownerId = postAndReadId("/api/v1/users", """
                {"nickname":"weather-owner"}
                """);
        long outdoorId = postAndReadId("/api/v1/places", """
                {
                  "name":"날씨 테스트 정원",
                  "latitude":37.570000,
                  "longitude":126.980000,
                  "category":"park",
                  "averageStayMinutes":90,
                  "environment":"OUTDOOR"
                }
                """);
        long indoorId = postAndReadId("/api/v1/places", """
                {
                  "name":"날씨 테스트 미술관",
                  "latitude":37.570000,
                  "longitude":126.980000,
                  "category":"museum",
                  "averageStayMinutes":90,
                  "environment":"INDOOR"
                }
                """);
        long tripId = postAndReadId("/api/v1/trips", """
                {
                  "name":"날씨 기반 서울 여행",
                  "startDate":"2026-09-10",
                  "endDate":"2026-09-11",
                  "dailyStartTime":"09:00",
                  "dailyEndTime":"11:00",
                  "accommodationName":"서울 숙소",
                  "accommodationLatitude":37.570000,
                  "accommodationLongitude":126.980000,
                  "transportMode":"WALKING",
                  "pace":"STANDARD"
                }
                """);
        addPlace(tripId, outdoorId);
        addPlace(tripId, indoorId);

        mockMvc.perform(put("/api/v1/trips/{tripId}/weather", tripId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "forecasts":[{
                                    "forecastDate":"2026-09-10",
                                    "condition":"RAIN",
                                    "precipitationProbability":101
                                  }]
                                }
                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/trips/{tripId}/weather", tripId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "forecasts":[{
                                    "forecastDate":"2026-09-12",
                                    "condition":"RAIN",
                                    "precipitationProbability":80
                                  }]
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/trips/{tripId}/weather", tripId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "forecasts":[
                                    {
                                      "forecastDate":"2026-09-10",
                                      "condition":"RAIN",
                                      "precipitationProbability":80
                                    },
                                    {
                                      "forecastDate":"2026-09-11",
                                      "condition":"CLEAR",
                                      "precipitationProbability":0
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].condition").value("RAIN"))
                .andExpect(jsonPath("$[1].condition").value("CLEAR"));

        mockMvc.perform(post("/api/v1/trips/{tripId}/optimize", tripId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.days[0].weatherCondition").value("RAIN"))
                .andExpect(jsonPath("$.days[0].precipitationProbability").value(80))
                .andExpect(jsonPath("$.days[1].weatherCondition").value("CLEAR"))
                .andExpect(jsonPath("$.items[0].placeId").value(indoorId))
                .andExpect(jsonPath("$.items[0].environment").value("INDOOR"))
                .andExpect(jsonPath("$.items[0].weatherScoreAdjustment").value(25))
                .andExpect(jsonPath("$.items[1].placeId").value(outdoorId))
                .andExpect(jsonPath("$.items[1].environment").value("OUTDOOR"))
                .andExpect(jsonPath("$.items[1].weatherScoreAdjustment").value(10));

        long guestId = postAndReadId("/api/v1/users", """
                {"nickname":"weather-guest"}
                """);
        assertThat(guestId).isNotEqualTo(ownerId);
        mockMvc.perform(get("/api/v1/trips/{tripId}/weather", tripId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private long postAndReadId(String path, String body) throws Exception {
        if ("/api/v1/users".equals(path)) {
            String nickname = JsonPath.read(body, "$.nickname");
            long userId = userService.create(nickname).id();
            mockMvc.authenticate(userId);
            return userId;
        }
        MvcResult result = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private void addPlace(long tripId, long placeId) throws Exception {
        mockMvc.perform(post("/api/v1/trips/{tripId}/places", tripId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\":" + placeId + "}"))
                .andExpect(status().isCreated());
    }

    private void setOpeningHour(long placeId, String dayOfWeek, String body) throws Exception {
        mockMvc.perform(put("/api/v1/places/{placeId}/opening-hours/{dayOfWeek}", placeId, dayOfWeek)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
