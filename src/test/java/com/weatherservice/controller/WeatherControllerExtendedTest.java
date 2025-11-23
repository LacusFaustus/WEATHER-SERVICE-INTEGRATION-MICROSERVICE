package com.weatherservice.controller;

import com.weatherservice.model.*;
import com.weatherservice.service.RateLimitingService;
import com.weatherservice.service.WeatherServiceFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(WeatherController.class)
class WeatherControllerExtendedTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private WeatherServiceFacade weatherService;

    @MockBean
    private RateLimitingService rateLimitingService;

    @Test
    void getCurrentWeather_WithCityAndCountry_ShouldReturnWeather() {
        // Given
        WeatherResponse response = createTestResponse();
        when(rateLimitingService.isAllowed(any())).thenReturn(Mono.just(true));
        when(weatherService.getWeather(any(WeatherRequest.class))).thenReturn(Mono.just(response));

        // When & Then
        webTestClient.get()
                .uri("/api/v1/weather/current?city=London&country=GB")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.location.name").isEqualTo("London")
                .jsonPath("$.source").isEqualTo("OPENWEATHER_MAP");
    }

    @Test
    void getCurrentWeather_WithValidCoordinates_ShouldReturnWeather() {
        // Given
        WeatherResponse response = createTestResponse();
        when(rateLimitingService.isAllowed(any())).thenReturn(Mono.just(true));
        when(weatherService.getWeather(any(WeatherRequest.class))).thenReturn(Mono.just(response));

        // When & Then - coordinates should work
        webTestClient.get()
                .uri("/api/v1/weather/current?lat=51.5074&lon=-0.1278")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.location.name").isEqualTo("London");
    }

    @Test
    void getCurrentWeather_WithProvider_ShouldReturnWeather() {
        // Given
        WeatherResponse response = createTestResponse();
        when(rateLimitingService.isAllowed(any())).thenReturn(Mono.just(true));
        when(weatherService.getWeather(any(WeatherRequest.class))).thenReturn(Mono.just(response));

        // When & Then
        webTestClient.get()
                .uri("/api/v1/weather/current?city=London&country=GB&provider=OPENWEATHER_MAP")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.location.name").isEqualTo("London")
                .jsonPath("$.source").isEqualTo("OPENWEATHER_MAP");
    }

    @Test
    void getCurrentWeather_WhenServiceError_ShouldReturnError() {
        // Given
        when(rateLimitingService.isAllowed(any())).thenReturn(Mono.just(true));
        when(weatherService.getWeather(any(WeatherRequest.class)))
                .thenReturn(Mono.error(new RuntimeException("Service error")));

        // When & Then
        webTestClient.get()
                .uri("/api/v1/weather/current?city=London&country=GB")
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    void getAggregatedWeather_ShouldReturnAggregatedData() {
        // Given
        AggregatedWeatherResponse aggregatedResponse = AggregatedWeatherResponse.builder()
                .location(LocationInfo.builder().name("London").country("GB").build())
                .temperature(18.5)
                .humidity(65)
                .pressure(1013)
                .windSpeed(3.2)
                .description("Partly cloudy")
                .providersUsed(List.of(WeatherProvider.OPENWEATHER_MAP, WeatherProvider.WEATHER_API))
                .sourcesCount(2)
                .timestamp(LocalDateTime.now())
                .recommendation("Pleasant weather")
                .build();

        when(rateLimitingService.isAllowed(any())).thenReturn(Mono.just(true));
        when(weatherService.getAggregatedWeather(any(WeatherRequest.class))).thenReturn(Mono.just(aggregatedResponse));

        // When & Then
        webTestClient.get()
                .uri("/api/v1/weather/aggregated?city=London&country=GB")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.temperature").isEqualTo(18.5)
                .jsonPath("$.sourcesCount").isEqualTo(2)
                .jsonPath("$.recommendation").isNotEmpty();
    }

    @Test
    void getRateLimitInfo_ShouldReturnRateLimitData() {
        // Given
        when(rateLimitingService.getRemainingRequests(any())).thenReturn(Mono.just(75L));

        // When & Then
        webTestClient.get()
                .uri("/api/v1/weather/rate-limit")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.remainingRequests").isEqualTo(75)
                .jsonPath("$.limit").isEqualTo(100);
    }

    @Test
    void getAvailableProviders_ShouldReturnProvidersList() {
        // When & Then
        webTestClient.get()
                .uri("/api/v1/weather/providers")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(3)
                .jsonPath("$[0]").isEqualTo("OPENWEATHER_MAP")
                .jsonPath("$[1]").isEqualTo("WEATHER_API")
                .jsonPath("$[2]").isEqualTo("ACCUWEATHER");
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
