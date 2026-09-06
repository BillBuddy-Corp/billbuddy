package com.billbuddy.backend.features.expenses.service;

import com.billbuddy.backend.exception.FxRateLookupFailedException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDate;

// Frankfurter (https://frankfurter.dev) is a free, keyless FX API backed by ECB reference rates.
// No API key means nothing to leak or rate-limit -- worth it even though coverage is limited to
// the ~30 currencies the ECB publishes, since this rate is only ever a suggestion the caller can
// override.
@Slf4j
@Service
public class FrankfurterExchangeRateService implements ExchangeRateService {

    private final RestClient restClient;

    public FrankfurterExchangeRateService(@Value("${fx.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    public ExchangeRateQuote getRate(String fromCurrency, String toCurrency) {
        JsonNode response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/latest")
                            .queryParam("base", fromCurrency)
                            .queryParam("symbols", toCurrency)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            log.warn("Frankfurter API call failed, status={}, body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new FxRateLookupFailedException("Could not look up an exchange rate for " + fromCurrency + " to " + toCurrency);
        } catch (RestClientException ex) {
            log.warn("Frankfurter API call failed", ex);
            throw new FxRateLookupFailedException("Could not look up an exchange rate for " + fromCurrency + " to " + toCurrency);
        }

        if (response == null || !response.hasNonNull("rates") || !response.path("rates").hasNonNull(toCurrency)) {
            throw new FxRateLookupFailedException("No exchange rate available for " + fromCurrency + " to " + toCurrency);
        }

        BigDecimal rate = response.path("rates").path(toCurrency).decimalValue();
        LocalDate asOf = LocalDate.parse(response.path("date").asText());
        return new ExchangeRateQuote(rate, asOf);
    }
}
