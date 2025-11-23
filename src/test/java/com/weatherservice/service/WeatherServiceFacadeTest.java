package com.weatherservice.service;

import com.weatherservice.client.WeatherProviderClient;
import com.weatherservice.model.*;
import com.weatherservice.resilience.WeatherErrorHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeatherServiceFacadeTest {

    @Mock
    private WeatherCacheService cacheService;

    @Mock
    private WeatherErrorHandler errorHandler;

    @Mock
    private WeatherMetrics metrics;

    @Test
    void getWeather_WhenCachedDataExists_ShouldReturnCachedData() {
        // Given
        WeatherRequest request = createTestRequest();
        WeatherResponse cachedResponse = createTestResponse();

        // Используем NoOp клиент чтобы избежать вызовов supportsProvider
        WeatherProviderClient noOpClient = new com.weatherservice.client.NoOpWeatherProviderClient();
        Map<String, WeatherProviderClient> clients = Map.of("noOpClient", noOpClient);
        WeatherServiceFacade weatherService = new WeatherServiceFacade(clients, cacheService, errorHandler, metrics);

        when(cacheService.getCachedWeather(request)).thenReturn(Mono.just(cachedResponse));

        // When
        Mono<WeatherResponse> result = weatherService.getWeather(request);

        // Then
        StepVerifier.create(result)
                .expectNext(cachedResponse)
                .verifyComplete();

        verify(cacheService).getCachedWeather(request);
        verifyNoInteractions(errorHandler);
    }

    @Test
    void getWeather_WhenNoCache_ShouldFetchFromProvider() {
        // Given
        WeatherRequest request = createTestRequest();
        WeatherResponse freshResponse = createTestResponse();
        WeatherProviderClient realClient = mock(WeatherProviderClient.class);

        Map<String, WeatherProviderClient> clients = Map.of("realClient", realClient);
        WeatherServiceFacade weatherService = new WeatherServiceFacade(clients, cacheService, errorHandler, metrics);

        when(cacheService.getCachedWeather(request)).thenReturn(Mono.empty());
        when(realClient.isRealProvider()).thenReturn(true);
        when(realClient.getCurrentWeather(request)).thenReturn(Mono.just(freshResponse));
        when(cacheService.cacheWeatherData(request, freshResponse)).thenReturn(Mono.just(true));

        // When
        Mono<WeatherResponse> result = weatherService.getWeather(request);

        // Then
        StepVerifier.create(result)
                .expectNext(freshResponse)
                .verifyComplete();

        verify(realClient).getCurrentWeather(request);
        verify(cacheService).cacheWeatherData(request, freshResponse);
    }

    @Test
    void getWeather_WhenProviderError_ShouldUseErrorHandler() {
        // Given
        WeatherRequest request = createTestRequest();
        WeatherResponse fallbackResponse = createTestResponse();
        WeatherProviderClient realClient = mock(WeatherProviderClient.class);

        Map<String, WeatherProviderClient> clients = Map.of("realClient", realClient);
        WeatherServiceFacade weatherService = new WeatherServiceFacade(clients, cacheService, errorHandler, metrics);

        when(cacheService.getCachedWeather(request)).thenReturn(Mono.empty());
        when(realClient.isRealProvider()).thenReturn(true);
        when(realClient.getCurrentWeather(request)).thenReturn(Mono.error(new RuntimeException("API error")));
        when(errorHandler.handleProviderError(any(), any())).thenReturn(Mono.just(fallbackResponse));

        // When
        Mono<WeatherResponse> result = weatherService.getWeather(request);

        // Then
        StepVerifier.create(result)
                .expectNext(fallbackResponse)
                .verifyComplete();

        verify(errorHandler).handleProviderError(any(), any());
    }

    @Test
    void getWeather_WhenAllProvidersFailAndNoCache_ShouldReturnError() {
        // Given
        WeatherRequest request = createTestRequest();
        WeatherProviderClient realClient = mock(WeatherProviderClient.class);

        Map<String, WeatherProviderClient> clients = Map.of("realClient", realClient);
        WeatherServiceFacade weatherService = new WeatherServiceFacade(clients, cacheService, errorHandler, metrics);

        when(cacheService.getCachedWeather(request)).thenReturn(Mono.empty());
        when(realClient.isRealProvider()).thenReturn(true);
        when(realClient.getCurrentWeather(request)).thenReturn(Mono.error(new RuntimeException("All providers failed")));
        when(errorHandler.handleProviderError(any(), any())).thenReturn(Mono.error(new com.weatherservice.exception.ServiceUnavailableException("Service unavailable")));

        // When
        Mono<WeatherResponse> result = weatherService.getWeather(request);

        // Then
        StepVerifier.create(result)
                .expectError(com.weatherservice.exception.ServiceUnavailableException.class)
                .verify();
    }

    @Test
    void getWeather_WhenNoProviderAvailable_ShouldReturnError() {
        // Given
        WeatherRequest request = createTestRequest();
        WeatherProviderClient noOpClient = new com.weatherservice.client.NoOpWeatherProviderClient();

        Map<String, WeatherProviderClient> noOpClients = Map.of("noOpClient", noOpClient);
        WeatherServiceFacade noOpWeatherService = new WeatherServiceFacade(
                noOpClients, cacheService, errorHandler, metrics);

        when(cacheService.getCachedWeather(request)).thenReturn(Mono.empty());
        when(errorHandler.handleProviderError(any(), any())).thenReturn(Mono.error(new com.weatherservice.exception.ServiceUnavailableException("No providers")));

        // When & Then
        StepVerifier.create(noOpWeatherService.getWeather(request))
                .expectError(com.weatherservice.exception.ServiceUnavailableException.class)
                .verify();
    }

    private WeatherRequest createTestRequest() {
        return WeatherRequest.builder()
                .city("London")
                .countryCode("GB")
                .units("metric")
                .provider(WeatherProvider.OPENWEATHER_MAP)
                .build();
    }

    private WeatherResponse createTestResponse() {
        return WeatherResponse.builder()
                .location(LocationInfo.builder()
                        .name("London")
                        .country("GB")
                        .lat(51.5074)
                        .lon(-0.1278)
                        .build())
                .current(CurrentWeather.builder()
                        .temperature(15.5)
                        .feelsLike(14.8)
                        .humidity(65)
                        .pressure(1013)
                        .windSpeed(3.6)
                        .description("cloudy")
                        .timestamp(LocalDateTime.now())
                        .build())
                .source(WeatherProvider.OPENWEATHER_MAP)
                .cachedUntil(LocalDateTime.now().plusMinutes(10))
                .build();
    }
}
