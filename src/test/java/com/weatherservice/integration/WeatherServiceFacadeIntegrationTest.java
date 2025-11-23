package com.weatherservice.integration;

import com.weatherservice.client.WeatherProviderClient;
import com.weatherservice.model.*;
import com.weatherservice.resilience.WeatherErrorHandler;
import com.weatherservice.service.WeatherCacheService;
import com.weatherservice.service.WeatherMetrics;
import com.weatherservice.service.WeatherServiceFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeatherServiceFacadeIntegrationTest {

    @Mock
    private WeatherCacheService cacheService;

    @Mock
    private WeatherErrorHandler errorHandler;

    @Mock
    private WeatherMetrics metrics;

    @Mock
    private WeatherProviderClient realClient;

    @Mock
    private WeatherProviderClient anotherClient;

    private WeatherServiceFacade weatherService;

    @BeforeEach
    void setUp() {
        Map<String, WeatherProviderClient> clients = Map.of(
                "realClient", realClient,
                "anotherClient", anotherClient
        );
        weatherService = new WeatherServiceFacade(clients, cacheService, errorHandler, metrics);
    }

    @Test
    void getAggregatedWeather_WithMultipleProviders_ShouldCombineResults() {
        // Given
        WeatherRequest request = createTestRequest();
        WeatherResponse response1 = createTestResponse(15.0, 60, 1010, 3.0, "cloudy", WeatherProvider.OPENWEATHER_MAP);
        WeatherResponse response2 = createTestResponse(17.0, 70, 1015, 4.0, "sunny", WeatherProvider.WEATHER_API);

        when(realClient.isRealProvider()).thenReturn(true);
        when(anotherClient.isRealProvider()).thenReturn(true);
        when(realClient.getCurrentWeather(request)).thenReturn(Mono.just(response1));
        when(anotherClient.getCurrentWeather(request)).thenReturn(Mono.just(response2));

        // When
        Mono<AggregatedWeatherResponse> result = weatherService.getAggregatedWeather(request);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(aggregated ->
                        aggregated.getTemperature() == 16.0 &&
                                aggregated.getHumidity() == 65 &&
                                aggregated.getPressure() == 1013 &&
                                aggregated.getWindSpeed() == 3.5 &&
                                aggregated.getSourcesCount() == 2 &&
                                aggregated.getProvidersUsed().size() == 2)
                .verifyComplete();
    }

    @Test
    void getAggregatedWeather_WhenSomeProvidersFail_ShouldUseAvailableData() {
        // Given
        WeatherRequest request = createTestRequest();
        WeatherResponse response = createTestResponse(15.0, 60, 1010, 3.0, "cloudy", WeatherProvider.OPENWEATHER_MAP);

        when(realClient.isRealProvider()).thenReturn(true);
        when(anotherClient.isRealProvider()).thenReturn(true);
        when(realClient.getCurrentWeather(request)).thenReturn(Mono.just(response));
        when(anotherClient.getCurrentWeather(request)).thenReturn(Mono.empty());

        // When
        Mono<AggregatedWeatherResponse> result = weatherService.getAggregatedWeather(request);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(aggregated ->
                        aggregated.getTemperature() == 15.0 &&
                                aggregated.getSourcesCount() == 1)
                .verifyComplete();
    }

    @Test
    void getWeather_WithSpecificProvider_ShouldUseThatProvider() {
        // Given
        WeatherRequest request = WeatherRequest.builder()
                .city("London")
                .countryCode("GB")
                .units("metric")
                .provider(WeatherProvider.WEATHER_API)
                .build();

        WeatherResponse response = createTestResponse(15.0, 60, 1010, 3.0, "cloudy", WeatherProvider.WEATHER_API);

        // Убираем все лишние stubbing и используем lenient для необходимых
        lenient().when(realClient.supportsProvider("WEATHER_API")).thenReturn(true);
        lenient().when(realClient.isRealProvider()).thenReturn(true);
        when(realClient.getCurrentWeather(request)).thenReturn(Mono.just(response));

        // When
        Mono<WeatherResponse> result = weatherService.getWeather(request);

        // Then
        StepVerifier.create(result)
                .expectNext(response)
                .verifyComplete();

        verify(realClient).getCurrentWeather(request);
    }

    @Test
    void getWeather_WhenCacheAndProviderAvailable_ShouldUseCacheFirst() {
        // Given
        WeatherRequest request = createTestRequest();
        WeatherResponse cachedResponse = createTestResponse(15.0, 60, 1010, 3.0, "cloudy", WeatherProvider.OPENWEATHER_MAP);

        when(cacheService.getCachedWeather(request)).thenReturn(Mono.just(cachedResponse));

        // When
        Mono<WeatherResponse> result = weatherService.getWeather(request);

        // Then
        StepVerifier.create(result)
                .expectNext(cachedResponse)
                .verifyComplete();

        verify(cacheService).getCachedWeather(request);
        verify(realClient, never()).getCurrentWeather(any());
        verify(anotherClient, never()).getCurrentWeather(any());
    }

    private WeatherRequest createTestRequest() {
        return WeatherRequest.builder()
                .city("London")
                .countryCode("GB")
                .units("metric")
                .build();
    }

    private WeatherResponse createTestResponse(Double temp, Integer humidity, Integer pressure,
                                               Double windSpeed, String description, WeatherProvider provider) {
        CurrentWeather current = CurrentWeather.builder()
                .temperature(temp)
                .feelsLike(temp != null ? temp - 1 : null)
                .humidity(humidity)
                .pressure(pressure)
                .windSpeed(windSpeed)
                .description(description)
                .timestamp(LocalDateTime.now())
                .build();

        return WeatherResponse.builder()
                .location(LocationInfo.builder()
                        .name("London")
                        .country("GB")
                        .lat(51.5074)
                        .lon(-0.1278)
                        .build())
                .current(current)
                .source(provider)
                .cachedUntil(LocalDateTime.now().plusMinutes(10))
                .build();
    }
}
