package com.routeplan.integration.google;

import static org.assertj.core.api.Assertions.*;
import static com.routeplan.integration.retry.ExternalRetryTestSupport.noDelayRetryExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeplan.integration.TravelTime;
import com.routeplan.place.search.LiveOpeningHours;
import com.routeplan.weather.application.OpenMeteoClient;
import com.routeplan.weather.domain.WeatherCondition;
import com.routeplan.trip.persistence.TripPlaceRepository;
import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class LiveProviderParsingTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void parsesWeatherWithIanaTimezoneAndSnowStormCodes() throws Exception {
        var value=OpenMeteoClient.parse(json.readTree("""
                {"timezone":"Europe/Paris","daily":{"time":["2026-09-10","2026-09-11","2026-09-12"],
                "weather_code":[0,73,95],"precipitation_probability_max":[0,90,100]}}
                """),Instant.EPOCH);
        assertThat(value.timeZoneId()).isEqualTo("Europe/Paris");
        assertThat(value.days()).extracting(OpenMeteoClient.Day::condition).containsExactly(WeatherCondition.CLEAR,WeatherCondition.SNOW,WeatherCondition.EXTREME);
        assertThatThrownBy(()->OpenMeteoClient.parse(json.readTree("{\"timezone\":\"bad/zone\"}"),Instant.EPOCH)).isInstanceOf(DateTimeException.class);
    }

    @Test void weatherCachesSuccessfulCoordinatesButNotProviderErrors() throws Exception {
        try(var server=new GoogleMapsStubServer()) {
            server.respondWith(r->new GoogleMapsStubServer.StubResponse(200,"{\"timezone\":\"Asia/Seoul\",\"daily\":{\"time\":[\"2026-09-10\"],\"weather_code\":[1],\"precipitation_probability_max\":[15]}}"));
            var client=new OpenMeteoClient(server.baseUri());
            client.fetch(BigDecimal.ONE,BigDecimal.TEN); client.fetch(BigDecimal.ONE,BigDecimal.TEN);
            assertThat(server.requests()).hasSize(1);
            server.respondWith(r->new GoogleMapsStubServer.StubResponse(429,"{}"));
            assertThatThrownBy(()->client.fetch(BigDecimal.TEN,BigDecimal.ONE)).isInstanceOf(ExternalProviderException.class);
            assertThatThrownBy(()->client.fetch(BigDecimal.TEN,BigDecimal.ONE)).isInstanceOf(ExternalProviderException.class);
            assertThat(server.requests()).hasSize(3);
        }
    }

    @Test void convertsTravelDateAndLocalTimeAndRejectsDstAmbiguity() {
        assertThat(TravelTime.departure(LocalDate.parse("2026-09-10"),LocalTime.of(9,0),"Asia/Tokyo")).isEqualTo(Instant.parse("2026-09-10T00:00:00Z"));
        assertThat(TravelTime.departure(LocalDate.parse("2026-07-10"),LocalTime.of(9,0),"Europe/Paris")).isEqualTo(Instant.parse("2026-07-10T07:00:00Z"));
        assertThatThrownBy(()->TravelTime.departure(LocalDate.parse("2026-03-29"),LocalTime.of(2,30),"Europe/Paris")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->TravelTime.departure(LocalDate.parse("2026-10-25"),LocalTime.of(2,30),"Europe/Paris")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void detailsUsesOnlyRequiredEnterpriseFieldsAndDoesNotCache() throws Exception {
        try(var server=new GoogleMapsStubServer()) {
            server.respondWith(r->new GoogleMapsStubServer.StubResponse(200,"""
                    {"regularOpeningHours":{"weekdayDescriptions":["월요일: 오전 9:00~오후 6:00"],
                    "periods":[{"open":{"day":1,"hour":9},"close":{"day":1,"hour":18}}]}}
                    """));
            var properties=new GoogleMapsProperties(); properties.setApiKey("test-key"); properties.setPlacesBaseUrl(server.baseUri());
            var hours=new LiveOpeningHours(new GoogleMapsHttpClient(properties,noDelayRetryExecutor(3)),properties,mock(TripPlaceRepository.class),"GOOGLE");
            var result=hours.fetch("test_place_id"); hours.fetch("test_place_id");
            assertThat(result.days().get(DayOfWeek.MONDAY).open()).isEqualTo(LocalTime.of(9,0));
            assertThat(result.days().get(DayOfWeek.TUESDAY).closed()).isTrue();
            assertThat(server.requests()).hasSize(2).allSatisfy(r->{
                assertThat(r.method()).isEqualTo("GET");
                assertThat(r.path()).isEqualTo("/v1/places/test_place_id");
                assertThat(r.header("X-Goog-FieldMask")).isEqualTo("id,regularOpeningHours");
            });
        }
    }

    @Test void neverCombinesSplitOrOvernightHoursIntoAnOpenGap() throws Exception {
        var split=LiveOpeningHours.parse(json.readTree("""
                {"regularOpeningHours":{"periods":[
                {"open":{"day":1,"hour":9},"close":{"day":1,"hour":12}},
                {"open":{"day":1,"hour":14},"close":{"day":1,"hour":18}}]}}
                """));
        assertThat(split.days()).isEmpty(); assertThat(split.warning()).contains("분할");
        var overnight=LiveOpeningHours.parse(json.readTree("{\"regularOpeningHours\":{\"periods\":[{\"open\":{\"day\":1,\"hour\":21},\"close\":{\"day\":2,\"hour\":3}}]}}"));
        assertThat(overnight.days()).isEmpty(); assertThat(overnight.warning()).contains("자정");
        var always=LiveOpeningHours.parse(json.readTree("{\"regularOpeningHours\":{\"periods\":[{\"open\":{\"day\":0,\"hour\":0}}]}}"));
        assertThat(always.days()).hasSize(7); assertThat(always.days().values()).allSatisfy(d->assertThat(d.closed()).isFalse());
    }

    @Test void eachRetryReservesBillableMatrixElementsAndAppLimitStopsNetwork() throws Exception {
        try(var server=new GoogleMapsStubServer()) {
            var attempts=new java.util.concurrent.atomic.AtomicInteger();
            server.respondWith(r->new GoogleMapsStubServer.StubResponse(attempts.incrementAndGet()==1?429:200,"[]"));
            var properties=new GoogleMapsProperties(); properties.setApiKey("test-key");
            var guard=mock(com.routeplan.integration.ExternalUsageGuard.class);
            var client=new GoogleMapsHttpClient(properties,noDelayRetryExecutor(3));
            org.springframework.test.util.ReflectionTestUtils.setField(client,"usageGuard",guard);
            var operation=com.routeplan.integration.retry.ExternalApiOperation.GOOGLE_ROUTES;
            var body=java.util.Map.of("origins",java.util.List.of(1,2),"destinations",java.util.List.of(1,2,3));
            client.post(operation,server.baseUri(),"originIndex",body);
            verify(guard,times(2)).reserve(operation,6);
            assertThat(server.requests()).hasSize(2);
            doThrow(new ExternalProviderException(ExternalProviderFailure.RATE_LIMITED,"app limit")).when(guard).reserve(operation,6);
            assertThatThrownBy(()->client.post(operation,server.baseUri(),"originIndex",body)).isInstanceOf(ExternalProviderException.class);
            assertThat(server.requests()).hasSize(2);
        }
    }
}
