package com.routeplan.community;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class SharedRouteApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publishesDiscoversLikesCopiesAndReoptimizesSharedRoute() throws Exception {
        long ownerId = postAndReadId("/api/v1/users", """
                {"nickname":"community-owner"}
                """);
        long travelerId = postAndReadId("/api/v1/users", """
                {"nickname":"community-traveler"}
                """);
        long osakaCastleId = postAndReadId("/api/v1/places", """
                {
                  "name":"오사카성",
                  "latitude":34.687300,
                  "longitude":135.526200,
                  "category":"ATTRACTION",
                  "averageStayMinutes":90
                }
                """);
        long dotonboriId = postAndReadId("/api/v1/places", """
                {
                  "name":"도톤보리",
                  "latitude":34.668700,
                  "longitude":135.501300,
                  "category":"FOOD",
                  "averageStayMinutes":60
                }
                """);
        long sourceTripId = postAndReadId("/api/v1/trips", """
                {
                  "userId":%d,
                  "name":"오사카 원본 여행",
                  "startDate":"2026-09-10",
                  "endDate":"2026-09-10",
                  "dailyStartTime":"09:00",
                  "dailyEndTime":"20:00",
                  "accommodationName":"난바 숙소",
                  "accommodationLatitude":34.665400,
                  "accommodationLongitude":135.501900,
                  "transportMode":"WALKING",
                  "pace":"RELAXED"
                }
                """.formatted(ownerId));
        addPlace(sourceTripId, osakaCastleId, 100, true);
        addPlace(sourceTripId, dotonboriId, 70, false);

        MvcResult optimization = mockMvc.perform(post(
                        "/api/v1/trips/{tripId}/optimize", sourceTripId
                ))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn();
        Number itineraryId = JsonPath.read(
                optimization.getResponse().getContentAsString(), "$.itineraryId"
        );

        MvcResult publication = mockMvc.perform(post(
                        "/api/v1/itineraries/{itineraryId}/share", itineraryId.longValue()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId":%d,
                                  "title":"처음 가는 오사카 핵심 루트",
                                  "description":"오사카성과 도톤보리를 여유롭게 방문하는 하루",
                                  "region":"오사카",
                                  "visibility":"PUBLIC"
                                }
                                """.formatted(ownerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceTripId").value(sourceTripId))
                .andExpect(jsonPath("$.sourceItineraryId").value(itineraryId.longValue()))
                .andExpect(jsonPath("$.sourceItineraryVersion").value(1))
                .andExpect(jsonPath("$.sourceTripName").value("오사카 원본 여행"))
                .andExpect(jsonPath("$.ownerNickname").value("community-owner"))
                .andExpect(jsonPath("$.placeCount").value(2))
                .andExpect(jsonPath("$.items[0].placeName").exists())
                .andExpect(jsonPath("$.items[0].startTime").exists())
                .andExpect(jsonPath("$.viewCount").value(0))
                .andReturn();
        Number routeId = JsonPath.read(publication.getResponse().getContentAsString(), "$.routeId");

        mockMvc.perform(post("/api/v1/itineraries/{itineraryId}/share", itineraryId.longValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId":%d,
                                  "title":"중복 공개",
                                  "region":"오사카"
                                }
                                """.formatted(ownerId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITINERARY_ALREADY_SHARED"));

        mockMvc.perform(get("/api/v1/routes")
                        .queryParam("region", "오사")
                        .queryParam("travelDays", "1")
                        .queryParam("sort", "LATEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].routeId").value(routeId.longValue()))
                .andExpect(jsonPath("$.content[0].placePreview").value("오사카성 · 도톤보리"))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/v1/routes")
                        .queryParam("sort", "POPULAR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].routeId").value(routeId.longValue()));

        mockMvc.perform(get("/api/v1/routes/{routeId}", routeId.longValue())
                        .queryParam("viewerUserId", String.valueOf(travelerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(1))
                .andExpect(jsonPath("$.likedByViewer").value(false));

        mockMvc.perform(post("/api/v1/routes/{routeId}/likes", routeId.longValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + travelerId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.liked").value(true));
        mockMvc.perform(post("/api/v1/routes/{routeId}/likes", routeId.longValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + travelerId + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ROUTE_LIKE"));

        mockMvc.perform(patch("/api/v1/trips/{tripId}", sourceTripId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"수정된 원본 여행",
                                  "accommodationName":"우메다 숙소",
                                  "accommodationLatitude":34.705500,
                                  "accommodationLongitude":135.498300
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/routes/{routeId}", routeId.longValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceTripName").value("오사카 원본 여행"))
                .andExpect(jsonPath("$.accommodationName").value("난바 숙소"))
                .andExpect(jsonPath("$.likeCount").value(1));

        MvcResult copiedTrip = mockMvc.perform(post(
                        "/api/v1/routes/{routeId}/copy", routeId.longValue()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId":%d,
                                  "name":"내 오사카 여행",
                                  "startDate":"2026-10-15",
                                  "dailyStartTime":"10:00",
                                  "dailyEndTime":"21:00",
                                  "accommodationName":"우메다 숙소",
                                  "accommodationLatitude":34.705500,
                                  "accommodationLongitude":135.498300,
                                  "transportMode":"PUBLIC_TRANSIT",
                                  "pace":"ACTIVE"
                                }
                                """.formatted(travelerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(travelerId))
                .andExpect(jsonPath("$.name").value("내 오사카 여행"))
                .andExpect(jsonPath("$.accommodationName").value("우메다 숙소"))
                .andExpect(jsonPath("$.transportMode").value("PUBLIC_TRANSIT"))
                .andExpect(jsonPath("$.pace").value("ACTIVE"))
                .andExpect(jsonPath("$.places.length()").value(2))
                .andReturn();
        Number copiedTripId = JsonPath.read(
                copiedTrip.getResponse().getContentAsString(), "$.id"
        );

        mockMvc.perform(post("/api/v1/trips/{tripId}/optimize", copiedTripId.longValue())
                        .queryParam("algorithm", "NEAREST_NEIGHBOR_2_OPT"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tripId").value(copiedTripId.longValue()))
                .andExpect(jsonPath("$.algorithm").value("NEAREST_NEIGHBOR_2_OPT"))
                .andExpect(jsonPath("$.items.length()").value(2));

        mockMvc.perform(get("/api/v1/routes/{routeId}", routeId.longValue())
                        .queryParam("viewerUserId", String.valueOf(travelerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.copyCount").value(1))
                .andExpect(jsonPath("$.likedByViewer").value(true));

        mockMvc.perform(delete("/api/v1/routes/{routeId}/likes", routeId.longValue())
                        .queryParam("userId", String.valueOf(travelerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.liked").value(false));
    }

    @Test
    void rejectsPublishingAnotherUsersItinerary() throws Exception {
        long ownerId = postAndReadId("/api/v1/users", """
                {"nickname":"ownership-owner"}
                """);
        long strangerId = postAndReadId("/api/v1/users", """
                {"nickname":"ownership-stranger"}
                """);
        long placeId = postAndReadId("/api/v1/places", """
                {
                  "name":"소유권 장소",
                  "latitude":34.687300,
                  "longitude":135.526200
                }
                """);
        long tripId = postAndReadId("/api/v1/trips", """
                {
                  "userId":%d,
                  "name":"소유권 여행",
                  "startDate":"2026-09-10",
                  "endDate":"2026-09-10",
                  "accommodationName":"난바 숙소",
                  "accommodationLatitude":34.665400,
                  "accommodationLongitude":135.501900,
                  "transportMode":"WALKING"
                }
                """.formatted(ownerId));
        addPlace(tripId, placeId, 50, false);
        MvcResult optimization = mockMvc.perform(post("/api/v1/trips/{tripId}/optimize", tripId))
                .andExpect(status().isCreated())
                .andReturn();
        Number itineraryId = JsonPath.read(
                optimization.getResponse().getContentAsString(), "$.itineraryId"
        );

        mockMvc.perform(post("/api/v1/itineraries/{itineraryId}/share", itineraryId.longValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId":%d,
                                  "title":"권한 없는 공개",
                                  "region":"오사카"
                                }
                                """.formatted(strangerId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ITINERARY_OWNER_MISMATCH"));
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

    private void addPlace(
            long tripId,
            long placeId,
            int priority,
            boolean mustVisit
    ) throws Exception {
        mockMvc.perform(post("/api/v1/trips/{tripId}/places", tripId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "placeId":%d,
                                  "priority":%d,
                                  "mustVisit":%s
                                }
                                """.formatted(placeId, priority, mustVisit)))
                .andExpect(status().isCreated());
    }
}
