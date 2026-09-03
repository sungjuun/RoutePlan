package com.routeplan.budget.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeplan.budget.domain.BudgetCurrency;
import com.routeplan.integration.google.ExternalProviderException;
import com.routeplan.integration.google.ExternalProviderFailure;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "routeplan.exchange", name = "provider", havingValue = "FRANKFURTER", matchIfMissing = true)
public class FrankfurterExchangeRateProvider implements ExchangeRateProvider {

    private final URI baseUrl;
    private final Duration requestTimeout;
    private final Duration cacheTtl;
    private final HttpClient client;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Map<Pair, RateQuote> cache = new LinkedHashMap<>();

    public FrankfurterExchangeRateProvider(
            @Value("${routeplan.exchange.base-url:https://api.frankfurter.dev}") URI baseUrl,
            @Value("${routeplan.exchange.request-timeout:5s}") Duration requestTimeout,
            @Value("${routeplan.exchange.cache-ttl:6h}") Duration cacheTtl
    ) {
        this.baseUrl = baseUrl;
        this.requestTimeout = requestTimeout;
        this.cacheTtl = cacheTtl;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    @Override
    public synchronized RateQuote latest(BudgetCurrency base, BudgetCurrency quote) {
        if (base == quote) {
            return new RateQuote(base, quote, BigDecimal.ONE, LocalDate.now(), Instant.now(), "IDENTITY");
        }
        Pair pair = new Pair(base, quote);
        RateQuote cached = cache.get(pair);
        if (cached != null && cached.fetchedAt().isAfter(Instant.now().minus(cacheTtl))) {
            return cached;
        }
        URI endpoint = baseUrl.resolve("/v2/rate/" + base.name() + "/" + quote.name());
        try {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(endpoint)
                    .timeout(requestTimeout)
                    .header("Accept", "application/json")
                    .header("User-Agent", "RoutePlan/2.0 exchange-rate")
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                throw provider(ExternalProviderFailure.RATE_LIMITED, "환율 조회 요청이 잠시 제한되었습니다.");
            }
            if (response.statusCode() != 200) {
                throw provider(ExternalProviderFailure.UNAVAILABLE, "현재 환율을 불러오지 못했습니다.");
            }
            RateQuote result = parse(objectMapper.readTree(response.body()), Instant.now());
            if (result.base() != base || result.quote() != quote) {
                throw provider(ExternalProviderFailure.INVALID_RESPONSE, "환율 공급자 응답 통화가 요청과 다릅니다.");
            }
            if (cache.size() >= 128) cache.remove(cache.keySet().iterator().next());
            cache.put(pair, result);
            return result;
        } catch (ExternalProviderException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw provider(ExternalProviderFailure.UNAVAILABLE, "환율 조회가 중단되었습니다.");
        } catch (Exception exception) {
            throw provider(ExternalProviderFailure.INVALID_RESPONSE, "환율 공급자 응답을 처리하지 못했습니다.");
        }
    }

    static RateQuote parse(JsonNode json, Instant fetchedAt) {
        try {
            BudgetCurrency base = BudgetCurrency.valueOf(json.path("base").asText());
            BudgetCurrency quote = BudgetCurrency.valueOf(json.path("quote").asText());
            BigDecimal rate = json.path("rate").decimalValue();
            LocalDate date = LocalDate.parse(json.path("date").asText());
            return new RateQuote(base, quote, rate, date, fetchedAt, "FRANKFURTER");
        } catch (RuntimeException exception) {
            throw provider(ExternalProviderFailure.INVALID_RESPONSE, "환율 공급자 응답 형식이 올바르지 않습니다.");
        }
    }

    private static ExternalProviderException provider(ExternalProviderFailure failure, String message) {
        return new ExternalProviderException(failure, message);
    }

    private record Pair(BudgetCurrency base, BudgetCurrency quote) {}
}
