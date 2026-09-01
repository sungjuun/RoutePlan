package com.routeplan;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.jayway.jsonpath.JsonPath;
import com.routeplan.auth.*;
import com.routeplan.user.application.UserService;
import com.routeplan.weather.application.*;
import com.routeplan.weather.domain.WeatherCondition;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"routeplan.weather.auto-refresh-enabled=false", "routeplan.route.provider=SIMPLE", "routeplan.place.provider=DISABLED"})
@AutoConfigureMockMvc
@Testcontainers
class ProfileAndWeatherApiIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES = com.routeplan.testsupport.PostgisTestContainer.create();
    @Autowired MockMvc raw;
    @Autowired UserService users;
    @Autowired WeatherRefreshSettings refresh;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean OpenMeteoClient weather;
    private AuthenticatedMockMvc mvc;
    private long owner;
    private final LocalDate date = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1);

    @BeforeEach void setup() {
        owner = users.create("profile-test-" + UUID.randomUUID()).id();
        mvc = new AuthenticatedMockMvc(raw); mvc.authenticate(owner);
    }
    @AfterEach void disableJobs() { jdbc.update("UPDATE trip_weather_refresh SET enabled = false, lease_token = NULL"); }

    @Test void uploadsPrivateAvatarUpdatesSessionViewAndResets() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(30, 20, BufferedImage.TYPE_INT_RGB), "png", bytes);
        var file = new MockMultipartFile("file", "photo.png", "image/png", bytes.toByteArray());
        String result = mvc.perform(multipart("/api/v1/profile/avatar").file(file).with(r -> { r.setMethod("PUT"); return r; }))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(result, "$.profileImageUrl");
        assertThat(url).startsWith("/api/v1/profile/avatar?v=");
        mvc.perform(get("/api/v1/auth/me")).andExpect(jsonPath("$.user.profileImageUrl").value(url));
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.authenticate(users.create("other-" + UUID.randomUUID()).id());
        mvc.perform(get(url)).andExpect(status().isNotFound());
        mvc.authenticate(owner);
        mvc.perform(delete("/api/v1/profile/avatar")).andExpect(status().isOk()).andExpect(jsonPath("$.profileImageUrl").isEmpty());
        mvc.perform(get("/api/v1/profile/avatar")).andExpect(status().isNotFound());
    }

    @Test void rejectsSpoofedImageAndRequiresAuthenticationAndCsrf() throws Exception {
        var file = new MockMultipartFile("file", "fake.png", "image/png", "<svg/>".getBytes());
        mvc.perform(multipart("/api/v1/profile/avatar").file(file).with(r -> { r.setMethod("PUT"); return r; }))
                .andExpect(status().isBadRequest());
        var principal = new RoutePlanPrincipal(owner, "test@example.com", "테스터", "unused", Instant.now());
        raw.perform(delete("/api/v1/profile/avatar").with(user(principal))).andExpect(status().isForbidden());
        raw.perform(get("/api/v1/profile/avatar")).andExpect(status().isUnauthorized());
    }

    @Test void scheduledWeatherPreservesManualDaysAndTimezoneAndHonorsCadence() throws Exception {
        long id = trip();
        mvc.perform(put("/api/v1/trips/{id}/weather", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"forecasts\":[{\"forecastDate\":\"" + date + "\",\"condition\":\"RAIN\",\"precipitationProbability\":90}]}"))
                .andExpect(status().isOk());
        stubWeather();
        enable(id);
        refresh.refreshDue(); refresh.refreshDue();
        verify(weather, times(1)).fetch(any(), any());
        mvc.perform(get("/api/v1/trips/{id}/weather", id)).andExpect(jsonPath("$[0].source").value("MANUAL"))
                .andExpect(jsonPath("$[0].condition").value("RAIN")).andExpect(jsonPath("$[1].source").value("OPEN_METEO"));
        mvc.perform(get("/api/v1/trips/{id}/time-zone", id)).andExpect(jsonPath("$.timeZoneId").value("Asia/Seoul"));
        assertThat(refresh.get(id).lastSuccessAt()).isNotNull();
        assertThat(refresh.get(id).nextRefreshAt()).isAfter(Instant.now().plus(Duration.ofHours(2)));
        refresh.set(id, true);
        refresh.refreshDue(); verifyNoMoreInteractions(weather);
    }

    @Test void failedRefreshBacksOffAndOtherUsersCannotEnableIt() throws Exception {
        long id = trip();
        when(weather.fetch(any(), any())).thenThrow(new IllegalStateException("private provider details"));
        enable(id); refresh.refreshDue();
        assertThat(refresh.get(id).lastError()).contains("30분").doesNotContain("private");
        assertThat(refresh.get(id).nextRefreshAt()).isAfter(Instant.now().plusSeconds(29 * 60));
        mvc.authenticate(users.create("no-access-" + UUID.randomUUID()).id());
        mvc.perform(put("/api/v1/trips/{id}/weather/auto-refresh", id).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test void disablingDuringFetchPreventsLateForecastWrite() throws Exception {
        long id = trip(); enable(id);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(weather.fetch(any(), any())).thenAnswer(call -> {
            entered.countDown(); assertThat(release.await(10, TimeUnit.SECONDS)).isTrue(); return forecast();
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var task = executor.submit(refresh::refreshDue);
            try { assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue(); refresh.set(id, false); }
            finally { release.countDown(); }
            task.get(10, TimeUnit.SECONDS);
        }
        mvc.perform(get("/api/v1/trips/{id}/weather", id)).andExpect(jsonPath("$.length()").value(0));
        assertThat(refresh.get(id).lastSuccessAt()).isNull();
    }

    private void enable(long id) throws Exception {
        mvc.perform(put("/api/v1/trips/{id}/weather/auto-refresh", id).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
    }
    private void stubWeather() { when(weather.fetch(any(), any())).thenReturn(forecast()); }
    private OpenMeteoClient.Forecast forecast() {
        return new OpenMeteoClient.Forecast("Asia/Tokyo", List.of(new OpenMeteoClient.Day(date, WeatherCondition.CLEAR, 0),
                new OpenMeteoClient.Day(date.plusDays(1), WeatherCondition.SNOW, 80)), Instant.now());
    }
    private long trip() throws Exception {
        String body = mvc.perform(post("/api/v1/trips").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"자동 갱신 테스트","startDate":"%s","endDate":"%s","accommodationName":"숙소",
                 "accommodationLatitude":37.57,"accommodationLongitude":126.98,"transportMode":"WALKING"}
                """.formatted(date, date.plusDays(1)))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
