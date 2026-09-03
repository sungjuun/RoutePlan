package com.routeplan.wishlist;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.routeplan.auth.AuthenticatedMockMvc;
import com.routeplan.user.application.UserService;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest(properties = "routeplan.place.provider=DISABLED")
@AutoConfigureMockMvc
@Testcontainers
class WishlistApiIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = com.routeplan.testsupport.PostgisTestContainer.create();

    @Autowired MockMvc rawMockMvc;
    @Autowired UserService userService;
    private AuthenticatedMockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = new AuthenticatedMockMvc(rawMockMvc);
    }

    @Test
    void createsWishlistCopiesSelectedPlacesToTripAndEnforcesOwnership() throws Exception {
        long ownerId = postId("/api/v1/users", "{\"nickname\":\"wishlist-owner\"}");
        long otherId = postId("/api/v1/users", "{\"nickname\":\"wishlist-other\"}");
        mockMvc.authenticate(ownerId);
        long palaceId = postId("/api/v1/places", """
                {"name":"경복궁","latitude":37.579617,"longitude":126.977041,"category":"ATTRACTION"}
                """);
        long villageId = postId("/api/v1/places", """
                {"name":"북촌한옥마을","latitude":37.582604,"longitude":126.983132,"category":"ATTRACTION"}
                """);
        long wishlistId = postId("/api/v1/wishlists", """
                {"name":"서울 가을","country":"대한민국","city":"서울"}
                """);

        long firstWishlistPlaceId = addPlace(wishlistId, palaceId, "MUST");
        long secondWishlistPlaceId = addPlace(wishlistId, villageId, "HIGH");

        mockMvc.perform(post("/api/v1/wishlists/{id}/trips", wishlistId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"서울 2일 여행",
                                  "startDate":"2026-10-10",
                                  "endDate":"2026-10-11",
                                  "dailyStartTime":"09:00",
                                  "dailyEndTime":"20:00",
                                  "accommodationName":"서울 숙소",
                                  "accommodationLatitude":37.570000,
                                  "accommodationLongitude":126.980000,
                                  "transportMode":"PUBLIC_TRANSIT",
                                  "pace":"STANDARD",
                                  "wishlistPlaceIds":[%d,%d]
                                }
                                """.formatted(firstWishlistPlaceId, secondWishlistPlaceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.places.length()").value(2))
                .andExpect(jsonPath("$.places[0].name").value("경복궁"))
                .andExpect(jsonPath("$.places[0].mustVisit").value(true))
                .andExpect(jsonPath("$.places[1].name").value("북촌한옥마을"))
                .andExpect(jsonPath("$.places[1].priority").value(80));

        mockMvc.authenticate(otherId);
        mockMvc.perform(get("/api/v1/wishlists/{id}", wishlistId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WISHLIST_NOT_FOUND"));
    }

    @Test
    void importsInstagramTextAsUnmatchedCandidatesWhenPlacesProviderIsDisabled() throws Exception {
        long userId = postId("/api/v1/users", "{\"nickname\":\"import-owner\"}");
        mockMvc.authenticate(userId);
        long wishlistId = postId("/api/v1/wishlists", "{\"name\":\"도쿄 후보\"}");

        MvcResult started = mockMvc.perform(post("/api/v1/imports/url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "url":"https://www.instagram.com/p/routeplan-example/",
                                  "inputText":"센소지\\n도쿄 스카이트리",
                                  "wishlistId":%d
                                }
                                """.formatted(wishlistId)))
                .andExpect(status().isAccepted())
                .andReturn();
        Number importId = JsonPath.read(started.getResponse().getContentAsString(), "$.id");

        String response = null;
        for (int attempt = 0; attempt < 30; attempt++) {
            MvcResult current = mockMvc.perform(get("/api/v1/imports/{id}", importId.longValue()))
                    .andExpect(status().isOk()).andReturn();
            response = current.getResponse().getContentAsString();
            String state = JsonPath.read(response, "$.status");
            if (state.equals("COMPLETED") || state.equals("FAILED")) break;
            Thread.sleep(100);
        }
        org.assertj.core.api.Assertions.assertThat(JsonPath.<String>read(response, "$.status")).isEqualTo("COMPLETED");
        org.assertj.core.api.Assertions.assertThat(JsonPath.<java.util.List<?>>read(response, "$.candidates")).hasSize(2);
        org.assertj.core.api.Assertions.assertThat(JsonPath.<String>read(response, "$.warning")).contains("Provider");
    }

    private long addPlace(long wishlistId, long placeId, String priority) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/wishlists/{id}/places", wishlistId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\":%d,\"priority\":\"%s\"}".formatted(placeId, priority)))
                .andExpect(status().isCreated()).andReturn();
        java.util.List<Number> ids = JsonPath.read(result.getResponse().getContentAsString(), "$.places[*].id");
        return ids.get(ids.size() - 1).longValue();
    }

    private long postId(String path, String body) throws Exception {
        if ("/api/v1/users".equals(path)) {
            String nickname = JsonPath.read(body, "$.nickname");
            long userId = userService.create(nickname).id();
            mockMvc.authenticate(userId);
            return userId;
        }
        MvcResult result = mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }
}
