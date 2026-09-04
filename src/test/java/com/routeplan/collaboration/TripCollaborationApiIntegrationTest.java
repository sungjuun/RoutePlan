package com.routeplan.collaboration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.routeplan.auth.AuthenticatedMockMvc;
import com.routeplan.user.application.UserService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TripCollaborationApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = com.routeplan.testsupport.PostgisTestContainer.create();

    @Autowired MockMvc raw;
    @Autowired UserService users;
    @Autowired JdbcTemplate jdbc;

    private AuthenticatedMockMvc mvc;
    private long ownerId;
    private long editorId;
    private long viewerId;
    private String editorEmail;
    private String viewerEmail;

    @BeforeEach
    void setUp() {
        mvc = new AuthenticatedMockMvc(raw);
        ownerId = user("owner");
        editorId = user("editor");
        viewerId = user("viewer");
        editorEmail = email(editorId);
        viewerEmail = email(viewerId);
        mvc.authenticate(ownerId);
    }

    @Test
    void managesMembersAndEnforcesEditorViewerPermissions() throws Exception {
        long tripId = trip("권한 여행");
        addMember(tripId, editorEmail, "EDITOR").andExpect(status().isCreated());
        String withViewer = addMember(tripId, viewerEmail, "VIEWER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.members.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        long ownerMemberId = number(withViewer, "$.members[0].memberId");

        mvc.authenticate(editorId);
        mvc.perform(get("/api/v1/trips/{tripId}", tripId))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/trips/{tripId}", tripId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"편집자가 수정한 여행\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("편집자가 수정한 여행"));
        mvc.perform(get("/api/v1/trips"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(tripId))
                .andExpect(jsonPath("$[0].accessRole").value("EDITOR"));
        addMember(tripId, "nobody@example.com", "VIEWER").andExpect(status().isForbidden());

        mvc.authenticate(viewerId);
        mvc.perform(get("/api/v1/trips/{tripId}", tripId)).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/trips/{tripId}", tripId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"수정 불가\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/trips"))
                .andExpect(jsonPath("$[0].accessRole").value("VIEWER"));

        mvc.authenticate(ownerId);
        mvc.perform(delete("/api/v1/trips/{tripId}/members/{memberId}", tripId, ownerMemberId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRIP_OWNER_ROLE_IMMUTABLE"));
    }

    @Test
    void appliesVotesToTheNextOptimizationAndKeepsMustVisitHighest() throws Exception {
        long tripId = trip("투표 여행");
        long normalPlace = place("함께 갈 장소", 37.571, 126.981);
        long mustPlace = place("필수 장소", 37.572, 126.982);
        addPlace(tripId, normalPlace, 50, false);
        addPlace(tripId, mustPlace, 10, true);
        addMember(tripId, editorEmail, "EDITOR").andExpect(status().isCreated());
        addMember(tripId, viewerEmail, "VIEWER").andExpect(status().isCreated());

        vote(tripId, normalPlace, ownerId, "YES");
        vote(tripId, normalPlace, editorId, "YES");
        vote(tripId, normalPlace, viewerId, "YES");

        mvc.authenticate(ownerId);
        mvc.perform(get("/api/v1/trips/{tripId}/collaboration", tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[0].priorityBand").value("HIGH"))
                .andExpect(jsonPath("$.places[0].effectivePriority").value(90))
                .andExpect(jsonPath("$.places[1].priorityBand").value("MUST"))
                .andExpect(jsonPath("$.places[1].effectivePriority").value(100));

        vote(tripId, normalPlace, viewerId, "NO");
        mvc.authenticate(ownerId);
        mvc.perform(get("/api/v1/trips/{tripId}/collaboration", tripId))
                .andExpect(jsonPath("$.places[0].priorityBand").value("NORMAL"))
                .andExpect(jsonPath("$.places[0].effectivePriority").value(60));

        vote(tripId, normalPlace, editorId, "NO");
        mvc.authenticate(ownerId);
        mvc.perform(get("/api/v1/trips/{tripId}/collaboration", tripId))
                .andExpect(jsonPath("$.places[0].priorityBand").value("LOW"))
                .andExpect(jsonPath("$.places[0].effectivePriority").value(30));

        vote(tripId, normalPlace, editorId, "YES");
        vote(tripId, normalPlace, viewerId, "YES");

        mvc.authenticate(ownerId);
        mvc.perform(post("/api/v1/trips/{tripId}/optimize", tripId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[?(@.placeId == " + normalPlace + ")].priority").value(hasItem(90)))
                .andExpect(jsonPath("$.items[?(@.placeId == " + mustPlace + ")].priority").value(hasItem(100)));
    }

    @Test
    void splitsSharedExpensesAndProducesAMinimumTransferPlan() throws Exception {
        long tripId = trip("정산 여행");
        addMember(tripId, editorEmail, "EDITOR").andExpect(status().isCreated());
        addMember(tripId, viewerEmail, "VIEWER").andExpect(status().isCreated());

        mvc.perform(post("/api/v1/trips/{tripId}/settlement/expenses", tripId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId":"%s","date":"2026-09-10","category":"FOOD",
                                  "description":"야키니쿠","amountMinor":12000,"currency":"KRW",
                                  "payerUserId":%d,"participantUserIds":[%d,%d,%d]
                                }
                                """.formatted(UUID.randomUUID(), ownerId, ownerId, editorId, viewerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expenses[0].participants.length()").value(3))
                .andExpect(jsonPath("$.balances[?(@.userId == " + ownerId + ")].netMinor").value(hasItem(8_000)))
                .andExpect(jsonPath("$.balances[?(@.userId == " + editorId + ")].netMinor").value(hasItem(-4_000)))
                .andExpect(jsonPath("$.balances[?(@.userId == " + viewerId + ")].netMinor").value(hasItem(-4_000)))
                .andExpect(jsonPath("$.transfers.length()").value(2))
                .andExpect(jsonPath("$.exactMinimum").value(true));

        mvc.perform(get("/api/v1/trips/{tripId}/spending", tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenses[0].payerUserId").value(ownerId))
                .andExpect(jsonPath("$.expenses[0].participantCount").value(3))
                .andExpect(jsonPath("$.spentMinor").value(12_000));
    }

    @Test
    void findsTheMinimumWithoutMakingCreditorsRelayPayments() throws Exception {
        long tripId = trip("최소 송금 여행");
        long debtorA = user("debtor-a");
        long debtorB = user("debtor-b");
        long debtorC = user("debtor-c");
        addMember(tripId, editorEmail, "EDITOR").andExpect(status().isCreated());
        addMember(tripId, email(debtorA), "EDITOR").andExpect(status().isCreated());
        addMember(tripId, email(debtorB), "VIEWER").andExpect(status().isCreated());
        addMember(tripId, email(debtorC), "VIEWER").andExpect(status().isCreated());

        long dinner = expense(tripId, ownerId, 10, "저녁");
        participant(dinner, debtorB, 7);
        participant(dinner, debtorC, 3);
        long tickets = expense(tripId, editorId, 8, "입장권");
        participant(tickets, debtorA, 8);

        mvc.perform(get("/api/v1/trips/{tripId}/settlement", tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transfers.length()").value(3))
                .andExpect(jsonPath("$.exactMinimum").value(true))
                .andExpect(jsonPath("$.transfers[?(@.fromUserId == " + ownerId + ")]").isEmpty())
                .andExpect(jsonPath("$.transfers[?(@.fromUserId == " + editorId + ")]").isEmpty())
                .andExpect(jsonPath("$.transfers[?(@.toUserId == " + debtorA + ")]").isEmpty())
                .andExpect(jsonPath("$.transfers[?(@.toUserId == " + debtorB + ")]").isEmpty())
                .andExpect(jsonPath("$.transfers[?(@.toUserId == " + debtorC + ")]").isEmpty());
    }

    @Test
    void recommendsOpenNearbyPlacesThatFitTheAvailableGap() throws Exception {
        long tripId = trip("주변 추천 여행");
        long nextPlace = place("다음 일정", 37.575, 126.985);
        addPlace(tripId, nextPlace, 50, false);
        long candidate = place("잠깐 들를 카페", 37.572, 126.982);
        jdbc.update("UPDATE places SET category='CAFE', average_stay_minutes=30 WHERE id=?", candidate);
        jdbc.update("""
                INSERT INTO place_opening_hours(place_id,day_of_week,open_time,close_time,closed)
                VALUES(?,?,'13:00','18:00',false)
                """, candidate, java.time.LocalDate.of(2026, 9, 10).getDayOfWeek().name());
        long closedCandidate = place("오늘 쉬는 카페", 37.5715, 126.9815);
        jdbc.update("UPDATE places SET category='CAFE', average_stay_minutes=30 WHERE id=?", closedCandidate);
        jdbc.update("""
                INSERT INTO place_opening_hours(place_id,day_of_week,closed)
                VALUES(?,?,true)
                """, closedCandidate, java.time.LocalDate.of(2026, 9, 10).getDayOfWeek().name());

        mvc.perform(get("/api/v1/trips/{tripId}/nearby-recommendations", tripId)
                        .queryParam("date", "2026-09-10")
                        .queryParam("currentTime", "14:00")
                        .queryParam("currentLatitude", "37.570")
                        .queryParam("currentLongitude", "126.980")
                        .queryParam("nextPlaceId", String.valueOf(nextPlace))
                        .queryParam("availableMinutes", "90"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.placeId == " + candidate + ")]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.placeId == " + candidate + ")].openingHoursKnown").value(hasItem(true)))
                .andExpect(jsonPath("$[?(@.placeId == " + candidate + ")].requiredMinutes")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.lessThanOrEqualTo(90))))
                .andExpect(jsonPath("$[?(@.placeId == " + closedCandidate + ")]").isEmpty());
    }

    private long user(String prefix) {
        long id = users.create(prefix + "-" + UUID.randomUUID()).id();
        jdbc.update("UPDATE users SET email=? WHERE id=?", prefix + "-" + id + "@routeplan.test", id);
        return id;
    }

    private String email(long userId) {
        return jdbc.queryForObject("SELECT email FROM users WHERE id=?", String.class, userId);
    }

    private String nickname(long userId) {
        return jdbc.queryForObject("SELECT nickname FROM users WHERE id=?", String.class, userId);
    }

    private long expense(long tripId, long payerUserId, long amount, String description) {
        return jdbc.queryForObject("""
                INSERT INTO trip_expenses(
                    trip_id,request_id,spend_date,category,description,amount_minor,
                    payer_user_id,created_by_user_id,payer_nickname_snapshot
                ) VALUES(?,?,'2026-09-10','OTHER',?,?,?,?,?) RETURNING id
                """, Long.class, tripId, UUID.randomUUID(), description, amount,
                payerUserId, ownerId, nickname(payerUserId));
    }

    private void participant(long expenseId, long userId, long share) {
        jdbc.update("""
                INSERT INTO trip_expense_participants(expense_id,user_id,nickname_snapshot,share_minor)
                VALUES(?,?,?,?)
                """, expenseId, userId, nickname(userId), share);
    }

    private long trip(String name) throws Exception {
        return createdId("/api/v1/trips", """
                {"name":"%s","startDate":"2026-09-10","endDate":"2026-09-10",
                 "dailyStartTime":"09:00","dailyEndTime":"20:00","accommodationName":"서울 숙소",
                 "accommodationLatitude":37.570,"accommodationLongitude":126.980,"transportMode":"WALKING"}
                """.formatted(name));
    }

    private long place(String name, double latitude, double longitude) throws Exception {
        return createdId("/api/v1/places", """
                {"name":"%s","latitude":%s,"longitude":%s,"averageStayMinutes":45}
                """.formatted(name, latitude, longitude));
    }

    private void addPlace(long tripId, long placeId, int priority, boolean mustVisit) throws Exception {
        mvc.perform(post("/api/v1/trips/{tripId}/places", tripId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\":" + placeId + ",\"priority\":" + priority
                                + ",\"mustVisit\":" + mustVisit + "}"))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions addMember(
            long tripId, String email, String role
    ) throws Exception {
        return mvc.perform(post("/api/v1/trips/{tripId}/members", tripId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"role\":\"" + role + "\"}"));
    }

    private void vote(long tripId, long placeId, long userId, String value) throws Exception {
        mvc.authenticate(userId);
        mvc.perform(put("/api/v1/trips/{tripId}/places/{placeId}/vote", tripId, placeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"" + value + "\"}"))
                .andExpect(status().isOk());
    }

    private long createdId(String path, String body) throws Exception {
        String json = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return number(json, "$.id");
    }

    private long number(String json, String path) {
        Number value = JsonPath.read(json, path);
        assertThat(value).isNotNull();
        return value.longValue();
    }
}
