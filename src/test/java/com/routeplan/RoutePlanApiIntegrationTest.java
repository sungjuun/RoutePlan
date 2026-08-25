package com.routeplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RoutePlanApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

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
    }

    @Test
    void rejectsMultiDayTripAndOptimizationWithoutPlaces() throws Exception {
        long userId = postAndReadId("/api/v1/users", """
                {"nickname":"boundary-tester"}
                """);

        mockMvc.perform(post("/api/v1/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId":%d,
                                  "name":"지원하지 않는 다일 여행",
                                  "startDate":"2026-09-10",
                                  "endDate":"2026-09-11",
                                  "accommodationName":"난바 숙소",
                                  "accommodationLatitude":34.665400,
                                  "accommodationLongitude":135.501900,
                                  "transportMode":"WALKING"
                                }
                                """.formatted(userId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

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

    private long postAndReadId(String path, String body) throws Exception {
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
