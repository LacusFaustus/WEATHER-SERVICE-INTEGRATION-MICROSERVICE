package com.weatherservice.resilience;

import com.weatherservice.model.WeatherRequest;
import com.weatherservice.model.WeatherResponse;
import com.weatherservice.model.WeatherProvider;
import com.weatherservice.service.WeatherCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.concurrent.TimeoutException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeatherErrorHandlerTest {

    @Mock
    private WeatherCacheService cacheService;

    @Test
    void handleProviderError_WithRateLimitError_ShouldUseCachedFallback() {
        // Given
        WeatherErrorHandler handler = new WeatherErrorHandler(cacheService);
        WeatherRequest request = WeatherRequest.builder().city("London").build();

        // Create proper WebClientResponseException with all required parameters
        WebClientResponseException error = WebClientResponseException.create(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Rate limit exceeded",
                HttpHeaders.EMPTY,
                null,
                null
        );

        WeatherResponse cachedResponse = createCachedResponse();
        when(cacheService.getCachedWeather(any())).thenReturn(Mono.just(cachedResponse));

        // When & Then
        StepVerifier.create(handler.handleProviderError(error, request))
                .expectNext(cachedResponse)
                .verifyComplete();
    }

    @Test
    void handleProviderError_WithNotFound_ShouldReturnLocationNotFoundException() {
        // Given
        WeatherErrorHandler handler = new WeatherErrorHandler(cacheService);
        WeatherRequest request = WeatherRequest.builder().city("UnknownCity").build();

        WebClientResponseException error = WebClientResponseException.create(
                HttpStatus.NOT_FOUND.value(),
                "Location not found",
                HttpHeaders.EMPTY,
                null,
                null
        );

        // When & Then
        StepVerifier.create(handler.handleProviderError(error, request))
                .expectError(com.weatherservice.exception.LocationNotFoundException.class)
                .verify();
    }

    @Test
    void handleProviderError_WithTimeout_ShouldTryFallback() {
        // Given
        WeatherErrorHandler handler = new WeatherErrorHandler(cacheService);
        WeatherRequest request = WeatherRequest.builder().city("London").build();
        TimeoutException error = new TimeoutException("Request timeout");

        WeatherResponse cachedResponse = createCachedResponse();
        when(cacheService.getCachedWeather(any())).thenReturn(Mono.just(cachedResponse));

        // When & Then
        StepVerifier.create(handler.handleProviderError(error, request))
                .expectNext(cachedResponse)
                .verifyComplete();
    }

    @Test
    void handleProviderError_WithServiceUnavailable_ShouldUseCachedFallback() {
        // Given
        WeatherErrorHandler handler = new WeatherErrorHandler(cacheService);
        WeatherRequest request = WeatherRequest.builder().city("London").build();

        WebClientResponseException error = WebClientResponseException.create(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "Service unavailable",
                HttpHeaders.EMPTY,
                null,
                null
        );

        WeatherResponse cachedResponse = createCachedResponse();
        when(cacheService.getCachedWeather(any())).thenReturn(Mono.just(cachedResponse));

        // When & Then
        StepVerifier.create(handler.handleProviderError(error, request))
                .expectNext(cachedResponse)
                .verifyComplete();
    }

    @Test
    void handleProviderError_WithGenericErrorAndNoCache_ShouldReturnServiceUnavailable() {
        // Given
        WeatherErrorHandler handler = new WeatherErrorHandler(cacheService);
        WeatherRequest request = WeatherRequest.builder().city("London").build();
        RuntimeException error = new RuntimeException("Generic error");

        when(cacheService.getCachedWeather(any())).thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(handler.handleProviderError(error, request))
                .expectError(com.weatherservice.exception.ServiceUnavailableException.class)
                .verify();
    }

    @Test
    void handleProviderError_WithGenericErrorAndCacheAvailable_ShouldReturnCachedData() {
        // Given
        WeatherErrorHandler handler = new WeatherErrorHandler(cacheService);
        WeatherRequest request = WeatherRequest.builder().city("London").build();
        RuntimeException error = new RuntimeException("Generic error");

        WeatherResponse cachedResponse = createCachedResponse();
        when(cacheService.getCachedWeather(any())).thenReturn(Mono.just(cachedResponse));

        // When & Then
        StepVerifier.create(handler.handleProviderError(error, request))
                .expectNext(cachedResponse)
                .verifyComplete();
    }

    private WeatherResponse createCachedResponse() {
        return WeatherResponse.builder()
                .location(com.weatherservice.model.LocationInfo.builder()
                        .name("London")
                        .country("GB")
                        .build())
                .current(com.weatherservice.model.CurrentWeather.builder()
                        .temperature(15.0)
                        .timestamp(LocalDateTime.now())
                        .build())
                .source(WeatherProvider.OPENWEATHER_MAP)
                .cachedUntil(LocalDateTime.now().plusMinutes(30))
                .build();
    }
}
