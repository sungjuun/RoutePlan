package com.routeplan.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.routeplan.auth.AuthenticatedMockMvc;
import com.routeplan.user.application.UserService;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TripBudgetApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = com.routeplan.testsupport.PostgisTestContainer.create();

    @Autowired private MockMvc rawMockMvc;
    @Autowired private UserService users;
    private AuthenticatedMockMvc mvc;

    @BeforeEach
    void authenticate() {
        mvc = new AuthenticatedMockMvc(rawMockMvc);
        mvc.authenticate(users.create("budget-" + UUID.randomUUID()).id());
    }

    @Test
    void budgetsOptionalVisitsAndPreservesCompletedCostsWhenReoptimizing() throws Exception {
        long trip = trip();
        long must = place(trip, 100, true);
        long expensive = place(trip, 70, false);
        long cheap = place(trip, 50, false);
        long[] places = {must, expensive, cheap};
        save(trip, "KRW", 4_000L, 500, places, new Long[]{1_000L, 4_000L, 1_500L}).andExpect(status().isOk());

        String initial = mvc.perform(post("/api/v1/trips/{id}/optimize", trip))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].placeId").value(must))
                .andExpect(jsonPath("$.exclusions[0].placeId").value(expensive))
                .andExpect(jsonPath("$.exclusions[0].reason").value("BUDGET"))
                .andExpect(jsonPath("$.costSummary.estimatedTotalMinor").value(3_000))
                .andExpect(jsonPath("$.costSummary.remainingMinor").value(1_000))
                .andReturn().getResponse().getContentAsString();
        long initialId = number(initial, "$.itineraryId");
        long completedId = number(initial, "$.items[0].itineraryItemId");

        save(trip, "KRW", 4_000L, 500, places, new Long[]{2_000L, 1_000L, 1_500L}).andExpect(status().isOk());
        mvc.perform(get("/api/v1/trips/{id}", trip)).andExpect(jsonPath("$.status").value("DRAFT"));
        mvc.perform(get("/api/v1/itineraries/{id}", initialId))
                .andExpect(jsonPath("$.items[0].estimatedCostMinor").value(1_000))
                .andExpect(jsonPath("$.costSummary.estimatedTotalMinor").value(3_000));

        String next = reoptimize(trip, initialId, completedId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.changeReason").value("BUDGET"))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].estimatedCostMinor").value(1_000))
                .andExpect(jsonPath("$.costSummary.fixedCostMinor").value(500))
                .andExpect(jsonPath("$.costSummary.estimatedTotalMinor").value(4_000))
                .andExpect(jsonPath("$.costSummary.remainingMinor").value(0))
                .andReturn().getResponse().getContentAsString();
        long nextId = number(next, "$.itineraryId");
        long nextCompletedId = number(next, "$.items[0].itineraryItemId");

        save(trip, "KRW", 800L, 500, places, new Long[]{2_000L, 1_000L, 1_500L}).andExpect(status().isOk());
        reoptimize(trip, nextId, nextCompletedId).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INFEASIBLE_BUDGET"));
        save(trip, "USD", 4_000L, 500, places, new Long[]{2_000L, 1_000L, 1_500L}).andExpect(status().isOk());
        reoptimize(trip, nextId, nextCompletedId).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BUDGET_CURRENCY_MISMATCH"));
    }

    @Test
    void distinguishesUnknownFromFreeAndRequiresCompletePricesForABudget() throws Exception {
        long trip = trip();
        long place = place(trip, 70, false);
        mvc.perform(post("/api/v1/trips/{id}/optimize", trip))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.costSummary.unpricedPlaceCount").value(1))
                .andExpect(jsonPath("$.costSummary.remainingMinor").isEmpty());
        save(trip, "JPY", 0L, 0, new long[]{place}, new Long[]{null}).andExpect(status().isOk());
        mvc.perform(post("/api/v1/trips/{id}/optimize", trip))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("COST_ESTIMATES_REQUIRED"));
        save(trip, "JPY", 0L, 0, new long[]{place}, new Long[]{0L}).andExpect(status().isOk());
        mvc.perform(post("/api/v1/trips/{id}/optimize", trip))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].estimatedCostMinor").value(0))
                .andExpect(jsonPath("$.costSummary.currency").value("JPY"))
                .andExpect(jsonPath("$.costSummary.unpricedPlaceCount").value(0));
    }

    @Test
    void rejectsMandatoryAndFixedCostsAboveBudgetWithoutSavingAVersion() throws Exception {
        long trip = trip();
        long must = place(trip, 100, true);
        save(trip, "EUR", 100L, 20, new long[]{must}, new Long[]{81L}).andExpect(status().isOk());
        mvc.perform(post("/api/v1/trips/{id}/optimize", trip))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INFEASIBLE_BUDGET"));
        mvc.perform(get("/api/v1/trips/{id}/itineraries/latest", trip)).andExpect(status().isNotFound());
        save(trip, "EUR", 0L, 20, new long[]{must}, new Long[]{0L}).andExpect(status().isOk());
        mvc.perform(post("/api/v1/trips/{id}/optimize", trip))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INFEASIBLE_BUDGET"));
    }

    @Test
    void validatesMoneyAndPlaceMembershipAtomically() throws Exception {
        long trip = trip();
        long place = place(trip, 70, false);
        save(trip, "USD", 1_000L, 100, new long[]{place}, new Long[]{200L}).andExpect(status().isOk());
        save(trip, "USD", -1L, 100, new long[]{place}, new Long[]{200L}).andExpect(status().isBadRequest());
        save(trip, "USD", 1_000_000_000_001L, 100, new long[]{place}, new Long[]{200L}).andExpect(status().isBadRequest());
        save(trip, "USD", 1_000L, 100, new long[]{place, place}, new Long[]{200L, 300L}).andExpect(status().isBadRequest());
        save(trip, "USD", 1_000L, 100, new long[]{place + 999_999}, new Long[]{200L}).andExpect(status().isConflict());
        mvc.perform(put("/api/v1/trips/{id}/budget", trip).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency":"USD","limitMinor":0.5,"fixedCostMinor":0,"placeCosts":[]}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/trips/{id}/budget", trip).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency":"USD","limitMinor":1000,"fixedCostMinor":0,"placeCosts":[null]}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/trips/{id}/budget", trip))
                .andExpect(status().isOk()).andExpect(jsonPath("$.limitMinor").value(1_000))
                .andExpect(jsonPath("$.placeCosts[0].estimatedCostMinor").value(200));
    }

    @Test
    void rejectsForeignOwnersAndAnonymousAccess() throws Exception {
        long trip = trip();
        long place = place(trip, 50, false);
        mvc.authenticate(users.create("guest-" + UUID.randomUUID()).id());
        mvc.perform(get("/api/v1/trips/{id}/budget", trip)).andExpect(status().isForbidden());
        save(trip, "KRW", 0L, 0, new long[]{place}, new Long[]{0L}).andExpect(status().isForbidden());
        rawMockMvc.perform(get("/api/v1/trips/{id}/budget", trip)).andExpect(status().isUnauthorized());
    }

    private long trip() throws Exception {
        return createdId("/api/v1/trips", """
                {"name":"예산 테스트","startDate":"2026-09-10","endDate":"2026-09-10",
                 "accommodationName":"숙소","accommodationLatitude":37.57,"accommodationLongitude":126.98,
                 "transportMode":"WALKING"}
                """);
    }

    private long place(long trip, int priority, boolean must) throws Exception {
        long id = createdId("/api/v1/places", """
                {"name":"비용 장소","latitude":37.57,"longitude":126.98,"averageStayMinutes":60}
                """);
        mvc.perform(post("/api/v1/trips/{id}/places", trip).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\":" + id + ",\"priority\":" + priority + ",\"mustVisit\":" + must + "}"))
                .andExpect(status().isCreated());
        return id;
    }

    private long createdId(String path, String body) throws Exception {
        String json = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return number(json, "$.id");
    }

    private ResultActions save(long trip, String currency, Long limit, long fixed, long[] places, Long[] costs) throws Exception {
        String entries = IntStream.range(0, places.length)
                .mapToObj(i -> "{\"placeId\":" + places[i] + ",\"estimatedCostMinor\":" + costs[i] + "}")
                .collect(Collectors.joining(","));
        return mvc.perform(put("/api/v1/trips/{id}/budget", trip).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currency\":\"" + currency + "\",\"limitMinor\":" + limit
                        + ",\"fixedCostMinor\":" + fixed + ",\"placeCosts\":[" + entries + "]}"));
    }

    private ResultActions reoptimize(long trip, long source, long completed) throws Exception {
        return mvc.perform(post("/api/v1/trips/{id}/reoptimize", trip).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sourceItineraryId":%d,"currentDate":"2026-09-10","currentTime":"10:00",
                         "currentLatitude":37.57,"currentLongitude":126.98,"completedItemIds":[%d],"reason":"BUDGET"}
                        """.formatted(source, completed)));
    }

    private long number(String json, String path) {
        Number value = JsonPath.read(json, path);
        assertThat(value).isNotNull();
        return value.longValue();
    }
}
