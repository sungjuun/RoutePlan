package com.routeplan;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.routeplan.auth.*;
import com.routeplan.integration.ExternalUsageGuard;
import com.routeplan.integration.google.*;
import com.routeplan.integration.retry.ExternalApiOperation;
import com.routeplan.user.application.UserService;
import com.routeplan.weather.application.OpenMeteoClient;
import com.routeplan.weather.domain.WeatherCondition;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"routeplan.community.moderator-emails=moderator@routeplan.test",
        "routeplan.google.monthly-details-limit=3", "routeplan.google.browser-key=test-browser-key",
        "routeplan.external.usage.open-ai-monthly-request-limit=3",
        "routeplan.external.usage.open-ai-monthly-token-limit=100",
        "routeplan.external.usage.open-ai-input-usd-per-million=2",
        "routeplan.external.usage.open-ai-output-usd-per-million=10",
        "routeplan.external.usage.open-ai-monthly-budget-usd=0.0003"})
@AutoConfigureMockMvc
@Testcontainers
class AdvancedFeaturesApiIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = com.routeplan.testsupport.PostgisTestContainer.create();
    @Autowired MockMvc raw;
    @Autowired UserService users;
    @Autowired JdbcTemplate jdbc;
    @Autowired ExternalUsageGuard usage;
    @MockitoBean OpenMeteoClient weather;
    @MockitoBean GoogleMapsHttpClient maps;
    private AuthenticatedMockMvc mvc;
    private long owner;

    @BeforeEach void setup() {
        mvc = new AuthenticatedMockMvc(raw);
        owner = users.create("advanced-" + UUID.randomUUID()).id();
        mvc.authenticate(owner);
    }

    @Test void expenseScopesAreIndependentAndRequestsAreIdempotent() throws Exception {
        long trip = trip();
        putJson("/api/v1/trips/" + trip + "/spending/allocations", """
                {"currency":"KRW","allocations":[{"date":"2026-09-10","limitMinor":100},
                 {"category":"FOOD","limitMinor":200},{"date":"2026-09-10","category":"FOOD","limitMinor":50}]}
                """).andExpect(status().isOk());
        UUID key = UUID.randomUUID();
        String body = expense(key, "2026-09-10", 80);
        String json = postJson("/api/v1/trips/" + trip + "/spending/expenses", body)
                .andExpect(status().isOk()).andExpect(jsonPath("$.spentMinor").value(80))
                .andExpect(jsonPath("$.scopes[?(@.limitMinor == 50)].remainingMinor").value(-30))
                .andReturn().getResponse().getContentAsString();
        long expense = number(json, "$.expenses[0].id");
        postJson("/api/v1/trips/" + trip + "/spending/expenses", body)
                .andExpect(status().isOk()).andExpect(jsonPath("$.expenses.length()").value(1));
        postJson("/api/v1/trips/" + trip + "/spending/expenses", expense(key,"2026-09-10",81)).andExpect(status().isConflict());
        putJson("/api/v1/trips/" + trip + "/spending/expenses/" + expense, expense(key,"2026-09-11",60))
                .andExpect(status().isOk()).andExpect(jsonPath("$.spentMinor").value(60))
                .andExpect(jsonPath("$.scopes[?(@.limitMinor == 50)].spentMinor").value(0));
        mvc.perform(delete("/api/v1/trips/{id}/spending/expenses/{expense}",trip,expense))
                .andExpect(status().isOk()).andExpect(jsonPath("$.spentMinor").value(0));
    }

    @Test void comparesLatestExpectedCostWithAPlaceLinkedExpense() throws Exception {
        long trip = trip();
        long place = addPlace(trip, "FOOD");
        putJson("/api/v1/trips/" + trip + "/budget", """
                {"currency":"KRW","limitMinor":5000,"fixedCostMinor":0,
                 "placeCosts":[{"placeId":%d,"estimatedCostMinor":1500}]}
                """.formatted(place)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/trips/{id}/optimize", trip)).andExpect(status().isCreated());

        postJson("/api/v1/trips/" + trip + "/spending/expenses", """
                {"currency":"KRW","requestId":"%s","date":"2026-09-10","category":"FOOD",
                 "description":"명소 식사","amountMinor":1780,"placeId":%d}
                """.formatted(UUID.randomUUID(), place))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedMinor").value(1500))
                .andExpect(jsonPath("$.spentMinor").value(1780))
                .andExpect(jsonPath("$.remainingExpectedMinor").value(-280))
                .andExpect(jsonPath("$.expenses[0].placeId").value(place))
                .andExpect(jsonPath("$.expenses[0].placeName").value("명소"))
                .andExpect(jsonPath("$.days[0].expectedMinor").value(1500))
                .andExpect(jsonPath("$.days[0].spentMinor").value(1780));

        postJson("/api/v1/trips/" + trip + "/spending/expenses", """
                {"currency":"KRW","requestId":"%s","date":"2026-09-10","category":"FOOD",
                 "description":"잘못된 장소","amountMinor":100,"placeId":999999}
                """.formatted(UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_PLACE_NOT_FOUND"));
    }

    @Test void validatesMoneyCurrencyDatesOwnershipAndCsrf() throws Exception {
        long trip=trip();
        String path="/api/v1/trips/"+trip+"/spending/expenses";
        postJson(path,expense(UUID.randomUUID(),"2026-09-09",10)).andExpect(status().isBadRequest());
        postJson(path,expense(UUID.randomUUID(),"2026-09-10",-1)).andExpect(status().isBadRequest());
        postJson(path,expense(UUID.randomUUID(),"2026-09-10",10).replace("\"amountMinor\":10","\"amountMinor\":0.5")).andExpect(status().isBadRequest());
        postJson(path,expense(UUID.randomUUID(),"2026-09-10",10).replace("KRW","USD")).andExpect(status().isConflict());
        postJson(path,expense(UUID.randomUUID(),"2026-09-10",10)).andExpect(status().isOk());
        putJson("/api/v1/trips/"+trip+"/budget","{\"currency\":\"USD\",\"limitMinor\":null,\"fixedCostMinor\":0,\"placeCosts\":[]}").andExpect(status().isConflict());
        raw.perform(post(path).with(user(principal(owner,"ordinary@routeplan.test"))).contentType(MediaType.APPLICATION_JSON)
                .content(expense(UUID.randomUUID(),"2026-09-10",10))).andExpect(status().isForbidden());
        mvc.authenticate(users.create("other-"+UUID.randomUUID()).id());
        mvc.perform(get("/api/v1/trips/{id}/spending",trip)).andExpect(status().isForbidden());
        postJson(path,expense(UUID.randomUUID(),"2026-09-10",10)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/trips/{id}/weather/refresh",trip)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/trips/{id}/time-zone",trip)).andExpect(status().isForbidden());
        raw.perform(get("/api/v1/me/preferences")).andExpect(status().isUnauthorized());
        verifyNoInteractions(weather);
    }

    @Test void returnsIdentityExchangeRateWithoutAnExternalCallForTheBudgetCurrency() throws Exception {
        long trip = trip();
        mvc.perform(get("/api/v1/trips/{id}/exchange-rate", trip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.base").value("KRW"))
                .andExpect(jsonPath("$.quote").value("KRW"))
                .andExpect(jsonPath("$.rate").value(1))
                .andExpect(jsonPath("$.provider").value("IDENTITY"));
    }

    @Test void automaticWeatherPreservesManualForecastAndPersistsLocalZone() throws Exception {
        long trip=trip();
        putJson("/api/v1/trips/"+trip+"/weather","""
                {"forecasts":[{"forecastDate":"2026-09-10","condition":"RAIN","precipitationProbability":80}]}
                """).andExpect(status().isOk());
        when(weather.fetch(any(),any())).thenReturn(new OpenMeteoClient.Forecast("Europe/Paris",List.of(
                new OpenMeteoClient.Day(LocalDate.parse("2026-09-10"),WeatherCondition.CLEAR,0),
                new OpenMeteoClient.Day(LocalDate.parse("2026-09-11"),WeatherCondition.CLOUDY,10)),Instant.now()));
        mvc.perform(post("/api/v1/trips/{id}/weather/refresh",trip)).andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedDates").value(1)).andExpect(jsonPath("$.preservedManualDates").value(1))
                .andExpect(jsonPath("$.timeZoneId").value("Europe/Paris"));
        mvc.perform(get("/api/v1/trips/{id}/weather",trip)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source").value("MANUAL"))
                .andExpect(jsonPath("$[0].condition").value("RAIN"))
                .andExpect(jsonPath("$[1].source").value("OPEN_METEO"));
        putJson("/api/v1/trips/"+trip+"/time-zone","{\"timeZoneId\":\"invalid/zone\"}").andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/trips/{id}/time-zone",trip)).andExpect(jsonPath("$.timeZoneId").value("Europe/Paris"));
        when(weather.fetch(any(),any())).thenThrow(new ExternalProviderException(ExternalProviderFailure.UNAVAILABLE,"offline"));
        mvc.perform(post("/api/v1/trips/{id}/weather/refresh",trip)).andExpect(status().isBadGateway());
        mvc.perform(get("/api/v1/trips/{id}/weather",trip)).andExpect(jsonPath("$.length()").value(2));
    }

    @Test void commentsReviewsReportsAndModerationEnforceOwnership() throws Exception {
        long route=route("서울", "museum", "PUBLIC");
        putJson("/api/v1/routes/"+route+"/review","{\"rating\":5,\"body\":\"own review\"}").andExpect(status().isBadRequest());
        long visitor=users.create("visitor-"+UUID.randomUUID()).id(); mvc.authenticate(visitor);
        String response=postJson("/api/v1/routes/"+route+"/comments","{\"body\":\"좋은 여행\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long comment=number(response,"$.comments[0].id");
        putJson("/api/v1/routes/"+route+"/review","{\"rating\":5,\"body\":\"추천합니다\"}").andExpect(status().isOk());
        putJson("/api/v1/routes/"+route+"/review","{\"rating\":4,\"body\":\"수정 후기\"}")
                .andExpect(jsonPath("$.reviewCount").value(1)).andExpect(jsonPath("$.averageRating").value(4));
        putJson("/api/v1/routes/"+route+"/review","{\"rating\":6,\"body\":\"범위 밖\"}").andExpect(status().isBadRequest());
        String report="{\"targetType\":\"COMMENT\",\"targetId\":"+comment+",\"reason\":\"SPAM\",\"detail\":\"검토 요청\"}";
        long reportId=number(postJson("/api/v1/routes/"+route+"/reports",report).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),"$.id");
        postJson("/api/v1/routes/"+route+"/reports",report).andExpect(jsonPath("$.id").value(reportId));
        mvc.authenticate(owner);
        putJson("/api/v1/routes/"+route+"/comments/"+comment,"{\"body\":\"타인 댓글 변경\"}").andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/routes/{id}/comments/{c}",route,comment)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/moderation/reports")).andExpect(status().isForbidden());
        postJson("/api/v1/moderation/reports/"+reportId+"/resolve","{\"resolution\":\"HIDE\"}").andExpect(status().isForbidden());
        raw.perform(get("/api/v1/routes/{id}/discussion",route)).andExpect(status().isOk()).andExpect(jsonPath("$.commentCount").value(1));
        raw.perform(get("/api/v1/moderation/reports").with(user(principal(owner,"moderator@routeplan.test"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == "+reportId+")].targetContent").value("좋은 여행"));
        raw.perform(post("/api/v1/moderation/reports/{id}/resolve",reportId).with(csrf()).with(user(principal(owner,"moderator@routeplan.test")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"resolution\":\"HIDE\"}")).andExpect(status().isNoContent());
        raw.perform(get("/api/v1/routes/{id}/discussion",route)).andExpect(jsonPath("$.commentCount").value(0));
        assertThat(jdbc.queryForObject("SELECT resolved_by FROM community_reports WHERE id=?",Long.class,reportId)).isEqualTo(owner);
        mvc.authenticate(visitor);
        putJson("/api/v1/routes/"+route+"/comments/"+comment,"{\"body\":\"숨김 우회\"}").andExpect(status().isForbidden());
    }

    @Test void personalizationRanksMatchesAndHidesUnlistedOwnAndModeratedRoutes() throws Exception {
        long match=route("서울","museum","PUBLIC");
        route("부산","restaurant","PUBLIC");
        long unlisted=route("서울","museum","UNLISTED");
        long hidden=route("서울","museum","PUBLIC");
        jdbc.update("UPDATE shared_routes SET moderated_hidden=true WHERE id=?",hidden);
        long visitor=users.create("recommend-"+UUID.randomUUID()).id(); mvc.authenticate(visitor);
        long own=route("서울","museum","PUBLIC");
        putJson("/api/v1/me/preferences","{\"interests\":[\"CULTURE\"],\"regions\":[\"서울\"],\"pace\":\"STANDARD\",\"transportMode\":\"WALKING\"}")
                .andExpect(status().isOk());
        String json=mvc.perform(get("/api/v1/me/recommendations")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].route.routeId").value(match)).andExpect(jsonPath("$[0].score").value(90))
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids=JsonPath.read(json,"$[*].route.routeId");
        assertThat(ids).doesNotContain((int)unlisted,(int)hidden,(int)own);
        raw.perform(get("/api/v1/routes/{id}",hidden)).andExpect(status().isNotFound());
        raw.perform(get("/api/v1/routes/{id}/discussion",hidden)).andExpect(status().isNotFound());
        putJson("/api/v1/me/preferences","{\"interests\":[null],\"regions\":[]}").andExpect(status().isBadRequest());
    }

    @Test void monthlyGuardReservesAtomicallyWithoutExceedingLimit() throws Exception {
        jdbc.update("DELETE FROM external_api_usage WHERE operation='GOOGLE_PLACE_DETAILS'");
        try (var executor=Executors.newFixedThreadPool(6)) {
            var tasks=new ArrayList<Callable<Boolean>>();
            for(int i=0;i<12;i++) tasks.add(()->{ try { usage.reserve(ExternalApiOperation.GOOGLE_PLACE_DETAILS,1); return true; } catch(ExternalProviderException e) { return false; } });
            long successes=0;
            for(var future:executor.invokeAll(tasks)) if(future.get()) successes++;
            assertThat(successes).isEqualTo(3);
        }
        assertThat(usage.current().stream().filter(u->u.operation().equals("GOOGLE_PLACE_DETAILS")).findFirst().orElseThrow().attemptedUnits()).isEqualTo(3);
        assertThatThrownBy(()->usage.reserve(ExternalApiOperation.GOOGLE_PLACE_DETAILS,1)).isInstanceOf(ExternalProviderException.class);
    }

    @Test void usageDashboardPersistsOutcomesLatencyTokensAndConfiguredEstimate() throws Exception {
        jdbc.update("DELETE FROM external_api_usage WHERE operation='OPENAI_RESPONSES'");
        usage.reserve(ExternalApiOperation.OPENAI_RESPONSES, 1);
        usage.recordOutcome(ExternalApiOperation.OPENAI_RESPONSES, 1, true, 25);
        usage.recordOpenAiTokens(80, 20);

        var value = usage.current().stream()
                .filter(row -> row.operation().equals("OPENAI_RESPONSES")).findFirst().orElseThrow();
        assertThat(value.attemptedUnits()).isEqualTo(1);
        assertThat(value.successCount()).isEqualTo(1);
        assertThat(value.failureCount()).isZero();
        assertThat(value.successRatePercent()).isEqualTo(100.0);
        assertThat(value.averageLatencyMs()).isEqualTo(25);
        assertThat(value.remainingTokens()).isZero();
        assertThat(value.status()).isEqualTo(ExternalUsageGuard.UsageStatus.BLOCKED);
        assertThat(value.estimatedCostUsd()).isEqualByComparingTo("0.000360");
        assertThat(value.costConfigured()).isTrue();
        mvc.perform(get("/api/v1/integrations/operations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers[?(@.provider == 'openai')].state").value("CLOSED"))
                .andExpect(jsonPath("$.alerts[?(@.code == 'USAGE_OPENAI_RESPONSES')].severity")
                        .value("CRITICAL"))
                .andExpect(jsonPath("$.alerts[?(@.code == 'COST_openai')].severity")
                        .value("CRITICAL"));
        assertThatThrownBy(() -> usage.reserve(ExternalApiOperation.OPENAI_RESPONSES, 1))
                .isInstanceOf(ExternalProviderException.class);
    }

    @Test void geometryIsOnDemandAndOwnerProtected() throws Exception {
        long trip=trip(); addPlace(trip,"museum");
        putJson("/api/v1/trips/"+trip+"/time-zone","{\"timeZoneId\":\"Asia/Tokyo\"}").andExpect(status().isOk());
        long itinerary=number(mvc.perform(post("/api/v1/trips/{id}/optimize",trip)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.timeZoneId").value("Asia/Tokyo")).andReturn().getResponse().getContentAsString(),"$.itineraryId");
        when(maps.post(eq(ExternalApiOperation.GOOGLE_GEOMETRY),any(),any(),any())).thenReturn(new ObjectMapper().readTree("{\"routes\":[{\"polyline\":{\"encodedPolyline\":\"_p~iF~ps|U_ulLnnqC_mqNvxq`@\"}}]}"));
        mvc.perform(post("/api/v1/itineraries/{id}/road-geometry?date=2026-09-10",itinerary))
                .andExpect(status().isOk()).andExpect(jsonPath("$.encodedPolylines.length()").value(2))
                .andExpect(jsonPath("$.provider").value("GOOGLE_MAPS"));
        verify(maps,times(2)).post(eq(ExternalApiOperation.GOOGLE_GEOMETRY),any(),eq("routes.polyline.encodedPolyline"),any());
        mvc.perform(get("/api/v1/integrations/maps-config")).andExpect(jsonPath("$.browserKey").value("test-browser-key"));
        mvc.authenticate(users.create("geometry-"+UUID.randomUUID()).id());
        mvc.perform(post("/api/v1/itineraries/{id}/road-geometry?date=2026-09-10",itinerary)).andExpect(status().isForbidden());
        verifyNoMoreInteractions(maps);
    }

    @Test void tripDateChangesCannotOrphanSpendingRecords() throws Exception {
        long trip=trip();
        postJson("/api/v1/trips/"+trip+"/spending/expenses",expense(UUID.randomUUID(),"2026-09-10",100)).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/trips/{id}",trip).contentType(MediaType.APPLICATION_JSON).content("{\"startDate\":\"2026-09-11\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/trips/{id}",trip)).andExpect(jsonPath("$.startDate").value("2026-09-10"));
    }

    @Test void hourlyCommunityLimitSurvivesDeletingComments() throws Exception {
        long route=route("서울","museum","PUBLIC");
        jdbc.update("INSERT INTO community_write_usage(user_id,bucket_start,units) VALUES(?,date_trunc('hour',now()),29)",owner);
        long comment=number(postJson("/api/v1/routes/"+route+"/comments","{\"body\":\"마지막 허용 댓글\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),"$.comments[0].id");
        mvc.perform(delete("/api/v1/routes/{route}/comments/{id}",route,comment)).andExpect(status().isOk());
        postJson("/api/v1/routes/"+route+"/comments","{\"body\":\"한도 초과\"}").andExpect(status().isConflict());
    }

    private long trip() throws Exception { return number(postJson("/api/v1/trips","""
            {"name":"실제 데이터 테스트","startDate":"2026-09-10","endDate":"2026-09-11",
             "accommodationName":"숙소","accommodationLatitude":37.57,"accommodationLongitude":126.98,"transportMode":"WALKING"}
            """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),"$.id"); }
    private long addPlace(long trip,String category) throws Exception {
        long place=number(postJson("/api/v1/places","{\"name\":\"명소\",\"latitude\":37.571,\"longitude\":126.981,\"averageStayMinutes\":60,\"category\":\""+category+"\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),"$.id");
        postJson("/api/v1/trips/"+trip+"/places","{\"placeId\":"+place+",\"priority\":70,\"mustVisit\":false}").andExpect(status().isCreated());
        return place;
    }
    private long route(String region,String category,String visibility) throws Exception {
        long trip=trip(); addPlace(trip,category);
        long itinerary=number(mvc.perform(post("/api/v1/trips/{id}/optimize",trip)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),"$.itineraryId");
        return number(postJson("/api/v1/itineraries/"+itinerary+"/share","{\"title\":\"추천 테스트\",\"region\":\""+region+"\",\"visibility\":\""+visibility+"\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),"$.routeId");
    }
    private String expense(UUID key,String date,long amount) { return "{\"currency\":\"KRW\",\"requestId\":\""+key+"\",\"date\":\""+date+"\",\"category\":\"FOOD\",\"description\":\"점심\",\"amountMinor\":"+amount+"}"; }
    private ResultActions postJson(String path,String body) throws Exception { return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)); }
    private ResultActions putJson(String path,String body) throws Exception { return mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(body)); }
    private long number(String json,String path) { return ((Number)JsonPath.read(json,path)).longValue(); }
    private RoutePlanPrincipal principal(long id,String email) { return new RoutePlanPrincipal(id,email,"moderator","{noop}unused",Instant.now()); }
}
