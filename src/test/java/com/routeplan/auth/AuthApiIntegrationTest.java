package com.routeplan.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.routeplan.user.domain.User;
import com.routeplan.user.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import jakarta.servlet.http.Cookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = com.routeplan.testsupport.PostgisTestContainer.create();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void signsUpWithHashedPasswordAndPersistsTheSession() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());

        MvcResult signup = signup("Traveler@Example.com", "session-traveler", "routeplan12!")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.email").value("traveler@example.com"))
                .andReturn();
        Cookie session = signup.getResponse().getCookie("ROUTEPLAN_SESSION");

        assertThat(session).isNotNull();
        User saved = userRepository.findByEmailIgnoreCase("traveler@example.com").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo("routeplan12!");
        assertThat(passwordEncoder.matches("routeplan12!", saved.getPasswordHash())).isTrue();

        mockMvc.perform(get("/api/v1/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.id").value(saved.getId()))
                .andExpect(jsonPath("$.user.nickname").value("session-traveler"));

        signup("traveler@example.com", "other-nickname", "otherpass12!")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void rejectsMissingCsrfInvalidCredentialsAndAnonymousProtectedAccess() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("csrf@example.com", "csrf-user", "routeplan12!")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        signup("login@example.com", "login-user", "routeplan12!")
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"login@example.com","password":"incorrect12!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));

        mockMvc.perform(get("/api/v1/trips/999999"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void derivesTripOwnerFromTheSessionAndDeniesAnotherUser() throws Exception {
        MvcResult ownerSignup = signup(
                "owner@example.com", "auth-owner", "routeplan12!"
        ).andExpect(status().isCreated()).andReturn();
        MvcResult strangerSignup = signup(
                "stranger@example.com", "auth-stranger", "routeplan12!"
        ).andExpect(status().isCreated()).andReturn();
        Cookie ownerSession = ownerSignup.getResponse().getCookie("ROUTEPLAN_SESSION");
        Cookie strangerSession = strangerSignup.getResponse().getCookie("ROUTEPLAN_SESSION");
        Number ownerId = JsonPath.read(ownerSignup.getResponse().getContentAsString(), "$.user.id");
        Number strangerId = JsonPath.read(
                strangerSignup.getResponse().getContentAsString(), "$.user.id"
        );

        MvcResult created = mockMvc.perform(post("/api/v1/trips")
                        .cookie(ownerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId":%d,
                                  "name":"세션 소유권 여행",
                                  "startDate":"2026-09-10",
                                  "endDate":"2026-09-10",
                                  "accommodationName":"서울 숙소",
                                  "accommodationLatitude":37.566500,
                                  "accommodationLongitude":126.978000,
                                  "transportMode":"PUBLIC_TRANSIT"
                                }
                                """.formatted(strangerId.longValue())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(ownerId.longValue()))
                .andReturn();
        Number tripId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/trips").cookie(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(tripId.longValue()))
                .andExpect(jsonPath("$[0].name").value("세션 소유권 여행"))
                .andExpect(jsonPath("$[0].placeCount").value(0));

        mockMvc.perform(get("/api/v1/trips").cookie(strangerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/trips/{tripId}", tripId.longValue())
                        .cookie(strangerSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private org.springframework.test.web.servlet.ResultActions signup(
            String email,
            String nickname,
            String password
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody(email, nickname, password)));
    }

    private String signupBody(String email, String nickname, String password) {
        return """
                {"email":"%s","nickname":"%s","password":"%s"}
                """.formatted(email, nickname, password);
    }
}
