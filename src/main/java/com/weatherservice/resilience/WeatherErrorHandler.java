package com.weatherservice.resilience;

import com.weatherservice.model.WeatherRequest;
import com.weatherservice.model.WeatherResponse;
import com.weatherservice.service.WeatherCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherErrorHandler {

    private final WeatherCacheService cacheService;

    public Mono<WeatherResponse> handleProviderError(Throwable error, WeatherRequest request) {
        log.warn("Handling weather provider error: {}", error.getMessage());

        if (error instanceof WebClientResponseException.TooManyRequests) {
            return handleRateLimitError(request);
        } else if (error instanceof WebClientResponseException.NotFound) {
            return Mono.error(new com.weatherservice.exception.LocationNotFoundException(
                    "Location not found: " + request.getCity()));
        } else if (error instanceof TimeoutException) {
            return fallbackToAlternativeProvider(request);
        } else if (error instanceof WebClientResponseException.ServiceUnavailable) {
            return handleServiceUnavailable(request);
        } else {
            return getCachedWeatherFallback(request, error);
        }
    }

    private Mono<WeatherResponse> handleRateLimitError(WeatherRequest request) {
        log.warn("Rate limit exceeded for provider: {}", request.getProvider());
        return getCachedWeatherFallback(request,
                new RuntimeException("Rate limit exceeded, using cached data"));
    }

    private Mono<WeatherResponse> handleServiceUnavailable(WeatherRequest request) {
        log.warn("Service unavailable for provider: {}", request.getProvider());
        return getCachedWeatherFallback(request,
                new RuntimeException("Service unavailable, using cached data"));
    }

    private Mono<WeatherResponse> fallbackToAlternativeProvider(WeatherRequest request) {
        log.warn("Timeout occurred, trying alternative provider for request: {}", request.getCity());
        // В реальной реализации здесь можно переключиться на другого провайдера
        return getCachedWeatherFallback(request,
                new RuntimeException("Timeout occurred, using cached data"));
    }

    private Mono<WeatherResponse> getCachedWeatherFallback(WeatherRequest request, Throwable error) {
        log.debug("Attempting to use cached data as fallback for: {}", request.getCity());
        return cacheService.getCachedWeather(request)
                .doOnNext(data -> {
                    if (data != null) {
                        log.info("Successfully used cached data for: {}", request.getCity());
                    }
                })
                .switchIfEmpty(Mono.error(() ->
                        new com.weatherservice.exception.ServiceUnavailableException(
                                "No cached data available: " + error.getMessage(), error)));
    }
}
