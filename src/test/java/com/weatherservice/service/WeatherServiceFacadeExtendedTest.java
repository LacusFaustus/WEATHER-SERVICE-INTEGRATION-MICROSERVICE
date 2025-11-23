package com.weatherservice.service;

import com.weatherservice.client.WeatherProviderClient;
import com.weatherservice.model.*;
import com.weatherservice.resilience.WeatherErrorHandler;
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
class WeatherServiceFacadeExtendedTest {

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
    void getWeather_WithNullRequest_ShouldReturnError() {
        // When & Then
        StepVerifier.create(weatherService.getWeather(null))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void getAggregatedWeather_WithNullRequest_ShouldReturnError() {
        // When & Then
        StepVerifier.create(weatherService.getAggregatedWeather(null))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void getWeather_WhenProviderReturnsEmpty_ShouldUseCachedFallback() {
        // Given
        WeatherRequest request = createTestRequest();

        when(cacheService.getCachedWeather(request)).thenReturn(Mono.empty());
        when(realClient.isRealProvider()).thenReturn(true);
        when(realClient.getCurrentWeather(request)).thenReturn(Mono.empty());

        // When & Then - ожидаем ServiceUnavailableException, так как нет кэшированных данных
        StepVerifier.create(weatherService.getWeather(request))
                .expectError(com.weatherservice.exception.ServiceUnavailableException.class)
                .verify();
    }

    @Test
    void getAggregatedWeather_WithMultipleProviders_ShouldAggregateCorrectly() {
        // Given
        WeatherRequest request = createTestRequest();
        WeatherResponse response1 = createTestResponse(15.0, 60, 1010, 3.0, "cloudy", WeatherProvider.OPENWEATHER_MAP);
        WeatherResponse response2 = createTestResponse(17.0, 70, 1015, 4.0, "sunny", WeatherProvider.WEATHER_API);

        when(realClient.isRealProvider()).thenReturn(true);
        when(anotherClient.isRealProvider()).thenReturn(true);
        when(realClient.getCurrentWeather(request)).thenReturn(Mono.just(response1));
        when(anotherClient.getCurrentWeather(request)).thenReturn(Mono.just(response2));

        // When & Then
        StepVerifier.create(weatherService.getAggregatedWeather(request))
                .expectNextMatches(aggregated ->
                        Math.abs(aggregated.getTemperature() - 16.0) < 0.01 &&
                                aggregated.getHumidity() == 65 &&
                                aggregated.getPressure() == 1013 &&
                                Math.abs(aggregated.getWindSpeed() - 3.5) < 0.01 &&
                                aggregated.getSourcesCount() == 2)
                .verifyComplete();
    }

    @Test
    void getWeather_WhenCacheServiceThrowsException_ShouldUseProvider() {
        // Given
        WeatherRequest request = createTestRequest();
        WeatherResponse response = createTestResponse(15.0, 60, 1010, 3.0, "cloudy", WeatherProvider.OPENWEATHER_MAP);

        // Кэш выбрасывает исключение, но оно должно быть обработано и использован провайдер
        when(cacheService.getCachedWeather(request)).thenReturn(Mono.error(new RuntimeException("Cache error")));
        when(realClient.isRealProvider()).thenReturn(true);
        when(realClient.getCurrentWeather(request)).thenReturn(Mono.just(response));
        when(cacheService.cacheWeatherData(request, response)).thenReturn(Mono.just(true));

        // When & Then - ожидаем успешный ответ от провайдера
        StepVerifier.create(weatherService.getWeather(request))
                .expectNextMatches(weather ->
                        weather.getCurrent().getTemperature().equals(15.0) &&
                                weather.getLocation().getName().equals("London"))
                .verifyComplete();
    }

    @Test
    void getWeather_WhenCacheFailsButProviderSucceeds_ShouldReturnData() {
        // Given
        WeatherRequest request = createTestRequest();
        WeatherResponse response = createTestResponse(15.0, 60, 1010, 3.0, "cloudy", WeatherProvider.OPENWEATHER_MAP);

        when(cacheService.getCachedWeather(request)).thenReturn(Mono.empty());
        when(realClient.isRealProvider()).thenReturn(true);
        when(realClient.getCurrentWeather(request)).thenReturn(Mono.just(response));
        when(cacheService.cacheWeatherData(request, response)).thenReturn(Mono.error(new RuntimeException("Cache failed")));

        // When & Then
        StepVerifier.create(weatherService.getWeather(request))
                .expectNextMatches(weather ->
                        weather.getCurrent().getTemperature().equals(15.0))
                .verifyComplete();
    }

    @Test
    void getAggregatedWeather_WithNullValuesInResponses_ShouldHandleGracefully() {
        // Given
        WeatherRequest request = createTestRequest();
        WeatherResponse response = createPartialNullResponse();

        when(realClient.isRealProvider()).thenReturn(true);
        when(realClient.getCurrentWeather(request)).thenReturn(Mono.just(response));

        // When & Then
        StepVerifier.create(weatherService.getAggregatedWeather(request))
                .expectNextMatches(aggregated ->
                        aggregated.getTemperature() == 0.0 &&
                                aggregated.getHumidity() == 0 &&
                                aggregated.getPressure() == 0 &&
                                aggregated.getWindSpeed() == 0.0)
                .verifyComplete();
    }

    @Test
    void getWeather_WhenProviderReturnsData_ShouldCacheAndReturn() {
        // Given
        WeatherRequest request = createTestRequest();
        WeatherResponse response = createTestResponse(15.0, 60, 1010, 3.0, "cloudy", WeatherProvider.OPENWEATHER_MAP);

        when(cacheService.getCachedWeather(request)).thenReturn(Mono.empty());
        when(realClient.isRealProvider()).thenReturn(true);
        when(realClient.getCurrentWeather(request)).thenReturn(Mono.just(response));
        when(cacheService.cacheWeatherData(request, response)).thenReturn(Mono.just(true));

        // When & Then
        StepVerifier.create(weatherService.getWeather(request))
                .expectNextMatches(weather ->
                        weather.getCurrent().getTemperature().equals(15.0) &&
                                weather.getLocation().getName().equals("London"))
                .verifyComplete();
    }

    @Test
    void getWeather_WhenCacheAvailable_ShouldReturnCachedData() {
        // Given
        WeatherRequest request = createTestRequest();
        WeatherResponse cachedResponse = createTestResponse(15.0, 60, 1010, 3.0, "cloudy", WeatherProvider.OPENWEATHER_MAP);

        when(cacheService.getCachedWeather(request)).thenReturn(Mono.just(cachedResponse));

        // When & Then
        StepVerifier.create(weatherService.getWeather(request))
                .expectNext(cachedResponse)
                .verifyComplete();

        verify(cacheService).getCachedWeather(request);
        verify(realClient, never()).getCurrentWeather(any());
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

        // When & Then
        StepVerifier.create(weatherService.getAggregatedWeather(request))
                .expectNextMatches(aggregated ->
                        aggregated.getTemperature() == 15.0 &&
                                aggregated.getSourcesCount() == 1)
                .verifyComplete();
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

    private WeatherResponse createPartialNullResponse() {
        CurrentWeather current = CurrentWeather.builder()
                .temperature(null)
                .feelsLike(null)
                .humidity(null)
                .pressure(null)
                .windSpeed(null)
                .description(null)
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
                .source(WeatherProvider.OPENWEATHER_MAP)
                .cachedUntil(LocalDateTime.now().plusMinutes(10))
                .build();
    }
}
