package com.weatherservice.service;

import com.weatherservice.client.WeatherProviderClient;
import com.weatherservice.model.*;
import com.weatherservice.resilience.WeatherErrorHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherServiceFacade {

    private final Map<String, WeatherProviderClient> clients;
    private final WeatherCacheService cacheService;
    private final WeatherErrorHandler errorHandler;
    private final WeatherMetrics metrics;

    public Mono<WeatherResponse> getWeather(WeatherRequest request) {
        if (request == null) {
            return Mono.error(new IllegalArgumentException("WeatherRequest cannot be null"));
        }

        log.debug("Getting weather for: {}", request.getCity());
        long startTime = System.currentTimeMillis();

        return getCachedWeatherSafe(request)
                .switchIfEmpty(Mono.defer(() -> getFromProvider(request)))
                .doOnSuccess(response -> {
                    if (response != null) {
                        recordSuccessMetrics(response, startTime);
                        log.debug("Successfully retrieved weather for {}", request.getCity());
                    }
                })
                .onErrorResume(error -> {
                    log.debug("Error in getWeather for {}: {}", request.getCity(), error.getMessage());
                    return handleWeatherError(error, request, startTime);
                });
    }

    public Mono<AggregatedWeatherResponse> getAggregatedWeather(WeatherRequest request) {
        if (request == null) {
            return Mono.error(new IllegalArgumentException("WeatherRequest cannot be null"));
        }

        log.debug("Getting aggregated weather for: {}", request.getCity());

        List<WeatherProviderClient> realProviders = getRealProviders();
        if (realProviders.isEmpty()) {
            return Mono.error(new com.weatherservice.exception.ServiceUnavailableException(
                    "No weather providers available"));
        }

        List<Mono<WeatherResponse>> providerRequests = realProviders.stream()
                .map(client -> executeProviderRequest(client, request))
                .collect(Collectors.toList());

        return Flux.merge(providerRequests)
                .collectList()
                .filter(responses -> !responses.isEmpty())
                .map(this::createAggregatedResponse)
                .switchIfEmpty(Mono.error(new com.weatherservice.exception.ServiceUnavailableException(
                        "All weather providers failed")));
    }

    private Mono<WeatherResponse> getCachedWeatherSafe(WeatherRequest request) {
        try {
            return cacheService.getCachedWeather(request)
                    .doOnNext(data -> {
                        if (data != null) {
                            log.debug("Cache hit for: {}", request.getCity());
                            metrics.recordCacheHit();
                        } else {
                            metrics.recordCacheMiss();
                        }
                    })
                    .onErrorResume(e -> {
                        log.debug("Cache error for {}: {}", request.getCity(), e.getMessage());
                        return Mono.empty();
                    });
        } catch (Exception e) {
            log.warn("Error getting cached weather for {}: {}", request.getCity(), e.getMessage());
            return Mono.empty();
        }
    }

    private Mono<WeatherResponse> getFromProvider(WeatherRequest request) {
        log.debug("Cache miss for: {}, fetching from provider", request.getCity());

        WeatherProviderClient client = selectProviderClient(request);
        if (client == null) {
            log.warn("No provider found for request: {}", request);
            return Mono.error(new com.weatherservice.exception.ServiceUnavailableException(
                    "No supported weather provider found"));
        }

        log.debug("Selected provider: {}", client.getClass().getSimpleName());
        long providerStartTime = System.currentTimeMillis();

        return client.getCurrentWeather(request)
                .flatMap(response -> {
                    if (response == null) {
                        log.warn("Provider returned null response for: {}", request.getCity());
                        return Mono.error(new RuntimeException("Provider returned null response"));
                    }
                    log.debug("Successfully got response from provider for: {}", request.getCity());
                    return cacheWeatherData(request, response);
                })
                .doOnNext(response -> recordProviderMetrics(response, providerStartTime))
                .onErrorResume(error -> {
                    log.warn("Provider error for {}: {}", request.getCity(), error.getMessage());
                    return getCachedFallback(request);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Provider returned empty response for: {}", request.getCity());
                    return getCachedFallback(request);
                }));
    }

    private Mono<WeatherResponse> getCachedFallback(WeatherRequest request) {
        log.debug("Trying cached fallback for: {}", request.getCity());
        return getCachedWeatherSafe(request)
                .switchIfEmpty(Mono.error(new com.weatherservice.exception.ServiceUnavailableException(
                        "Service unavailable and no cached data for: " + request.getCity())));
    }

    private Mono<WeatherResponse> handleWeatherError(Throwable error, WeatherRequest request, long startTime) {
        log.debug("Handling weather error for {}: {}", request.getCity(), error.getClass().getSimpleName());
        recordErrorMetrics(request, startTime);

        if (errorHandler != null) {
            try {
                return errorHandler.handleProviderError(error, request)
                        .onErrorResume(e -> {
                            log.error("Error handler failed for {}: {}", request.getCity(), e.getMessage());
                            return getCachedFallback(request);
                        });
            } catch (Exception e) {
                log.error("Error in error handler for {}: {}", request.getCity(), e.getMessage());
                return getCachedFallback(request);
            }
        } else {
            return getCachedFallback(request);
        }
    }

    private WeatherProviderClient selectProviderClient(WeatherRequest request) {
        log.debug("Selecting provider for: {}", request.getCity());

        if (request.getProvider() != null) {
            return clients.values().stream()
                    .filter(client -> client != null && client.supportsProvider(request.getProvider().name()))
                    .findFirst()
                    .orElseGet(this::getDefaultProvider);
        }

        return getDefaultProvider();
    }

    private WeatherProviderClient getDefaultProvider() {
        return clients.values().stream()
                .filter(client -> client != null && client.isRealProvider())
                .findFirst()
                .orElse(null);
    }

    private List<WeatherProviderClient> getRealProviders() {
        return clients.values().stream()
                .filter(client -> client != null && client.isRealProvider())
                .collect(Collectors.toList());
    }

    private Mono<WeatherResponse> executeProviderRequest(WeatherProviderClient client, WeatherRequest request) {
        if (client == null) {
            return Mono.empty();
        }

        Mono<WeatherResponse> providerCall = client.getCurrentWeather(request);
        if (providerCall == null) {
            return Mono.empty();
        }

        return providerCall
                .onErrorResume(error -> {
                    log.debug("Provider {} failed: {}", client.getClass().getSimpleName(), error.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<WeatherResponse> cacheWeatherData(WeatherRequest request, WeatherResponse response) {
        try {
            return cacheService.cacheWeatherData(request, response)
                    .thenReturn(response)
                    .onErrorResume(e -> {
                        log.warn("Failed to cache weather data for {}, but returning response", request.getCity());
                        return Mono.just(response);
                    });
        } catch (Exception e) {
            log.warn("Error caching weather data for {}: {}", request.getCity(), e.getMessage());
            return Mono.just(response);
        }
    }

    private AggregatedWeatherResponse createAggregatedResponse(List<WeatherResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            throw new IllegalArgumentException("Responses cannot be null or empty");
        }

        List<WeatherResponse> validResponses = responses.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (validResponses.isEmpty()) {
            throw new IllegalArgumentException("No valid responses to aggregate");
        }

        AggregatedWeatherResponse aggregated = AggregatedWeatherResponse.builder()
                .location(validResponses.get(0).getLocation())
                .temperature(calculateAverageTemperature(validResponses))
                .humidity(calculateAverageHumidity(validResponses))
                .pressure(calculateAveragePressure(validResponses))
                .windSpeed(calculateAverageWindSpeed(validResponses))
                .description(getMostCommonDescription(validResponses))
                .providersUsed(validResponses.stream()
                        .map(WeatherResponse::getSource)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()))
                .sourcesCount(validResponses.size())
                .timestamp(LocalDateTime.now())
                .build();

        aggregated.setRecommendation(aggregated.generateRecommendation());
        return aggregated;
    }

    private Double calculateAverageTemperature(List<WeatherResponse> responses) {
        List<Double> temperatures = responses.stream()
                .map(r -> r.getCurrent() != null ? r.getCurrent().getTemperature() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (temperatures.isEmpty()) {
            return 0.0;
        }

        return temperatures.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private Integer calculateAverageHumidity(List<WeatherResponse> responses) {
        List<Integer> humidities = responses.stream()
                .map(r -> r.getCurrent() != null ? r.getCurrent().getHumidity() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (humidities.isEmpty()) {
            return 0;
        }

        return (int) Math.round(humidities.stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0));
    }

    private Integer calculateAveragePressure(List<WeatherResponse> responses) {
        List<Integer> pressures = responses.stream()
                .map(r -> r.getCurrent() != null ? r.getCurrent().getPressure() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (pressures.isEmpty()) {
            return 0;
        }

        return (int) Math.round(pressures.stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0));
    }

    private Double calculateAverageWindSpeed(List<WeatherResponse> responses) {
        List<Double> windSpeeds = responses.stream()
                .map(r -> r.getCurrent() != null ? r.getCurrent().getWindSpeed() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (windSpeeds.isEmpty()) {
            return 0.0;
        }

        return windSpeeds.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private String getMostCommonDescription(List<WeatherResponse> responses) {
        return responses.stream()
                .map(r -> r.getCurrent() != null ? r.getCurrent().getDescription() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Unknown");
    }

    private void recordSuccessMetrics(WeatherResponse response, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        if (metrics != null && response != null && response.getSource() != null) {
            metrics.recordWeatherRequest(response.getSource(), true, Duration.ofMillis(duration));
        }
    }

    private void recordErrorMetrics(WeatherRequest request, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        WeatherProvider provider = request != null ? request.getProvider() : null;
        if (metrics != null) {
            metrics.recordWeatherRequest(provider, false, Duration.ofMillis(duration));
        }
    }

    private void recordProviderMetrics(WeatherResponse response, long providerStartTime) {
        long providerDuration = System.currentTimeMillis() - providerStartTime;
        if (metrics != null && response != null && response.getSource() != null) {
            metrics.recordProviderResponseTime(response.getSource(), Duration.ofMillis(providerDuration));
        }
        log.info("Fetched fresh weather data from {} for {}",
                response != null ? response.getSource() : "unknown",
                response != null && response.getLocation() != null ? response.getLocation().getName() : "unknown");
    }
}
