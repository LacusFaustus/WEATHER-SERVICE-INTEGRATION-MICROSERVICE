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
class WeatherControllerTest {

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
        when(weatherService.getWeather(any(WeatherRequest.class))).thenReturn(Mono.just(response));
        when(rateLimitingService.isAllowed(any())).thenReturn(Mono.just(true));

        // When & Then
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/weather/current")
                        .queryParam("city", "London")
                        .queryParam("country", "GB")
                        .queryParam("units", "metric")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.location.name").isEqualTo("London")
                .jsonPath("$.current.temperature").isEqualTo(15.5)
                .jsonPath("$.source").isEqualTo("OPENWEATHER_MAP");
    }

    @Test
    void getCurrentWeather_WithCoordinates_ShouldReturnWeather() {
        // Given
        WeatherResponse response = createTestResponse();
        when(weatherService.getWeather(any(WeatherRequest.class))).thenReturn(Mono.just(response));
        when(rateLimitingService.isAllowed(any())).thenReturn(Mono.just(true));

        // When & Then
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/weather/current")
                        .queryParam("lat", "51.5074")
                        .queryParam("lon", "-0.1278")
                        .queryParam("units", "metric")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.location.name").isEqualTo("London");
    }

    @Test
    void getCurrentWeather_WhenRateLimitExceeded_ShouldReturnError() {
        // Given
        when(rateLimitingService.isAllowed(any())).thenReturn(Mono.just(false));

        // When & Then
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/weather/current")
                        .queryParam("city", "London")
                        .build())
                .exchange()
                .expectStatus().isEqualTo(429); // TOO_MANY_REQUESTS
    }

    @Test
    void getCurrentWeather_WhenServiceError_ShouldReturnError() {
        // Given
        when(weatherService.getWeather(any(WeatherRequest.class)))
                .thenReturn(Mono.error(new RuntimeException("Service unavailable")));
        when(rateLimitingService.isAllowed(any())).thenReturn(Mono.just(true));

        // When & Then
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/weather/current")
                        .queryParam("city", "UnknownCity")
                        .build())
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    void getAggregatedWeather_ShouldReturnOk() {
        // Given
        AggregatedWeatherResponse aggregatedResponse = AggregatedWeatherResponse.builder()
                .location(LocationInfo.builder().name("London").country("GB").build())
                .temperature(20.0)
                .humidity(65)
                .description("Partly cloudy")
                .providersUsed(List.of(WeatherProvider.OPENWEATHER_MAP))
                .sourcesCount(1)
                .timestamp(LocalDateTime.now())
                .recommendation("Pleasant weather! Great for outdoor activities")
                .build();

        when(weatherService.getAggregatedWeather(any(WeatherRequest.class))).thenReturn(Mono.just(aggregatedResponse));
        when(rateLimitingService.isAllowed(any())).thenReturn(Mono.just(true));

        // When & Then
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/weather/aggregated")
                        .queryParam("city", "London")
                        .build())
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getAvailableProviders_ShouldReturnProvidersList() {
        // When & Then
        webTestClient.get()
                .uri("/api/v1/weather/providers")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
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
