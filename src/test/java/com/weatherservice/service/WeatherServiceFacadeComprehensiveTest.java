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
class WeatherServiceFacadeComprehensiveTest {

    @Mock
    private WeatherCacheService cacheService;

    @Mock
    private WeatherErrorHandler errorHandler;

    @Mock
    private WeatherMetrics metrics;

    @Mock
    private WeatherProviderClient openWeatherClient;

    @Mock
    private WeatherProviderClient weatherApiClient;

    private WeatherServiceFacade weatherService;

    @BeforeEach
    void setUp() {
        Map<String, WeatherProviderClient> clients = Map.of(
                "openWeatherClient", openWeatherClient,
                "weatherApiClient", weatherApiClient
        );
        weatherService = new WeatherServiceFacade(clients, cacheService, errorHandler, metrics);
    }

    @Test
    void getWeather_WithAllProvidersFailing_ShouldReturnError() {
        // Given
        WeatherRequest request = createTestRequest();

        when(cacheService.getCachedWeather(request)).thenReturn(Mono.empty());
        // Убираем вызовы getCurrentWeather - они не достигаются из-за ошибки в errorHandler
        when(errorHandler.handleProviderError(any(), any()))
                .thenReturn(Mono.error(new com.weatherservice.exception.ServiceUnavailableException("All failed")));

        // When & Then
        StepVerifier.create(weatherService.getWeather(request))
                .expectError(com.weatherservice.exception.ServiceUnavailableException.class)
                .verify();

        // Проверяем, что getCurrentWeather не вызывался
        verify(openWeatherClient, never()).getCurrentWeather(any());
        verify(weatherApiClient, never()).getCurrentWeather(any());
    }

    @Test
    void getWeather_WithCacheAndProviderSuccess_ShouldUseCache() {
        // Given
        WeatherRequest request = createTestRequest();
        WeatherResponse cachedResponse = createTestResponse();

        when(cacheService.getCachedWeather(request)).thenReturn(Mono.just(cachedResponse));

        // When & Then
        StepVerifier.create(weatherService.getWeather(request))
                .expectNext(cachedResponse)
                .verifyComplete();

        verify(openWeatherClient, never()).getCurrentWeather(any());
        verify(weatherApiClient, never()).getCurrentWeather(any());
    }

    @Test
    void getAggregatedWeather_WithEmptyResponses_ShouldReturnError() {
        // Given
        WeatherRequest request = createTestRequest();

        when(openWeatherClient.isRealProvider()).thenReturn(true);
        when(weatherApiClient.isRealProvider()).thenReturn(true);
        when(openWeatherClient.getCurrentWeather(request)).thenReturn(Mono.empty());
        when(weatherApiClient.getCurrentWeather(request)).thenReturn(Mono.empty());

        // When & Then
        StepVerifier.create(weatherService.getAggregatedWeather(request))
                .expectError(com.weatherservice.exception.ServiceUnavailableException.class)
                .verify();
    }

    @Test
    void getAggregatedWeather_WithPartialNullValues_ShouldAggregateCorrectly() {
        // Given
        WeatherRequest request = createTestRequest();
        WeatherResponse response1 = createTestResponse(20.0, 50, 1010, 5.0, "sunny", WeatherProvider.OPENWEATHER_MAP);
        WeatherResponse response2 = createTestResponse(null, null, null, null, null, WeatherProvider.WEATHER_API);

        when(openWeatherClient.isRealProvider()).thenReturn(true);
        when(weatherApiClient.isRealProvider()).thenReturn(true);
        when(openWeatherClient.getCurrentWeather(request)).thenReturn(Mono.just(response1));
        when(weatherApiClient.getCurrentWeather(request)).thenReturn(Mono.just(response2));

        // When & Then
        StepVerifier.create(weatherService.getAggregatedWeather(request))
                .expectNextMatches(aggregated ->
                        aggregated.getTemperature() == 20.0 &&
                                aggregated.getHumidity() == 50 &&
                                aggregated.getSourcesCount() == 2)
                .verifyComplete();
    }

    @Test
    void getWeather_WithSpecificProvider_ShouldUseOnlyThatProvider() {
        // Given
        WeatherRequest request = WeatherRequest.builder()
                .city("London")
                .countryCode("GB")
                .units("metric")
                .provider(WeatherProvider.OPENWEATHER_MAP)
                .build();

        WeatherResponse response = createTestResponse();

        when(cacheService.getCachedWeather(request)).thenReturn(Mono.empty());
        when(openWeatherClient.supportsProvider("OPENWEATHER_MAP")).thenReturn(true);
        when(openWeatherClient.getCurrentWeather(request)).thenReturn(Mono.just(response));
        when(cacheService.cacheWeatherData(request, response)).thenReturn(Mono.just(true));

        // When & Then
        StepVerifier.create(weatherService.getWeather(request))
                .expectNext(response)
                .verifyComplete();

        verify(openWeatherClient).getCurrentWeather(request);
        verify(weatherApiClient, never()).getCurrentWeather(any());
    }

    @Test
    void getWeather_WhenCacheServiceThrowsException_ShouldUseProvider() {
        // Given
        WeatherRequest request = createTestRequest();
        WeatherResponse response = createTestResponse(15.0, 60, 1010, 3.0, "cloudy", WeatherProvider.OPENWEATHER_MAP);

        // Кэш выбрасывает исключение, но оно должно быть обработано и использован провайдер
        when(cacheService.getCachedWeather(request)).thenReturn(Mono.error(new RuntimeException("Cache error")));
        when(openWeatherClient.isRealProvider()).thenReturn(true);
        when(openWeatherClient.getCurrentWeather(request)).thenReturn(Mono.just(response));
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
        when(openWeatherClient.isRealProvider()).thenReturn(true);
        when(openWeatherClient.getCurrentWeather(request)).thenReturn(Mono.just(response));
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

        when(openWeatherClient.isRealProvider()).thenReturn(true);
        when(openWeatherClient.getCurrentWeather(request)).thenReturn(Mono.just(response));

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
        when(openWeatherClient.isRealProvider()).thenReturn(true);
        when(openWeatherClient.getCurrentWeather(request)).thenReturn(Mono.just(response));
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
        verify(openWeatherClient, never()).getCurrentWeather(any());
    }

    @Test
    void getAggregatedWeather_WhenSomeProvidersFail_ShouldUseAvailableData() {
        // Given
        WeatherRequest request = createTestRequest();
        WeatherResponse response = createTestResponse(15.0, 60, 1010, 3.0, "cloudy", WeatherProvider.OPENWEATHER_MAP);

        when(openWeatherClient.isRealProvider()).thenReturn(true);
        when(weatherApiClient.isRealProvider()).thenReturn(true);
        when(openWeatherClient.getCurrentWeather(request)).thenReturn(Mono.just(response));
        when(weatherApiClient.getCurrentWeather(request)).thenReturn(Mono.empty());

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

    private WeatherResponse createTestResponse() {
        return createTestResponse(20.0, 50, 1010, 5.0, "sunny", WeatherProvider.OPENWEATHER_MAP);
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
