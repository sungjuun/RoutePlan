package com.routeplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                .andExpect(jsonPath("$.closedTour").value(false))
                .andExpect(jsonPath("$.routeDataType").value("STRAIGHT_LINE_ESTIMATE"))
                .andExpect(jsonPath("$.items[0].placeId").value(dotonboriId))
                .andExpect(jsonPath("$.items[1].placeId").value(osakaCastleId));

        mockMvc.perform(post("/api/v1/trips/{tripId}/optimize", tripId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(get("/api/v1/trips/{tripId}/itineraries/latest", tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
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
}
