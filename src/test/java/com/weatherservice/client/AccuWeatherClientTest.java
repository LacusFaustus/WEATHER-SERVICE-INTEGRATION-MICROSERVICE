package com.weatherservice.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weatherservice.model.WeatherRequest;
import com.weatherservice.model.WeatherResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccuWeatherClientTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private AccuWeatherClient accuWeatherClient;
    private final String apiKey = "test-key";
    private final String baseUrl = "http://test-url";

    @BeforeEach
    void setUp() {
        accuWeatherClient = new AccuWeatherClient(webClient, apiKey, baseUrl);
    }

    @Test
    void getCurrentWeather_WithCity_ShouldReturnWeather() {
        // Given
        WeatherRequest request = WeatherRequest.builder()
                .city("London")
                .language("en")
                .build();

        // Mock location key response
        String locationJson = "[{\"Key\": \"12345\"}]";
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(JsonNode.class))
                .thenReturn(reactor.core.publisher.Flux.just(createJsonNode(locationJson)))
                .thenReturn(reactor.core.publisher.Flux.just(createJsonNode(createWeatherJson())));

        // When
        Mono<WeatherResponse> result = accuWeatherClient.getCurrentWeather(request);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(response ->
                        response.getLocation().getName().equals("London") &&
                                response.getCurrent().getTemperature().equals(15.5) &&
                                response.getSource().name().equals("ACCUWEATHER"))
                .verifyComplete();
    }

    @Test
    void getCurrentWeather_WhenLocationNotFound_ShouldReturnError() {
        // Given
        WeatherRequest request = WeatherRequest.builder()
                .city("UnknownCity")
                .build();

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(JsonNode.class))
                .thenReturn(reactor.core.publisher.Flux.empty());

        // When & Then
        StepVerifier.create(accuWeatherClient.getCurrentWeather(request))
                .expectError(com.weatherservice.exception.LocationNotFoundException.class)
                .verify();
    }

    @Test
    void getCurrentWeather_WhenInvalidLocationParameters_ShouldReturnError() {
        // Given
        WeatherRequest request = WeatherRequest.builder().build(); // No location parameters

        // When & Then
        StepVerifier.create(accuWeatherClient.getCurrentWeather(request))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void searchLocations_ShouldReturnLocations() {
        // Given
        String locationJson = "{\"LocalizedName\": \"London\", \"Country\": {\"LocalizedName\": \"United Kingdom\"}}";

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(JsonNode.class))
                .thenReturn(reactor.core.publisher.Flux.just(createJsonNode(locationJson)));

        // When
        Mono<java.util.List<com.weatherservice.model.LocationInfo>> result =
                accuWeatherClient.searchLocations("London", "en", 5);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(locations ->
                        !locations.isEmpty() &&
                                locations.get(0).getName().equals("London") &&
                                locations.get(0).getCountry().equals("United Kingdom"))
                .verifyComplete();
    }

    @Test
    void searchLocations_WithEmptyResult_ShouldReturnEmptyList() {
        // Given
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(JsonNode.class))
                .thenReturn(reactor.core.publisher.Flux.empty());

        // When
        Mono<java.util.List<com.weatherservice.model.LocationInfo>> result =
                accuWeatherClient.searchLocations("UnknownCity", "en", 5);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(locations -> locations.isEmpty())
                .verifyComplete();
    }

    @Test
    void supportsProvider_WithAccuWeather_ShouldReturnTrue() {
        // When & Then
        assertTrue(accuWeatherClient.supportsProvider("ACCUWEATHER"));
        assertTrue(accuWeatherClient.supportsProvider("accuweather"));
    }

    @Test
    void supportsProvider_WithOtherProvider_ShouldReturnFalse() {
        // When & Then
        assertFalse(accuWeatherClient.supportsProvider("OPENWEATHER_MAP"));
        assertFalse(accuWeatherClient.supportsProvider("WEATHER_API"));
        assertFalse(accuWeatherClient.supportsProvider("UNKNOWN"));
    }

    @Test
    void isRealProvider_ShouldReturnTrue() {
        // When & Then
        assertTrue(accuWeatherClient.isRealProvider());
    }

    private JsonNode createJsonNode(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String createWeatherJson() {
        return """
            {
                "Temperature": {"Metric": {"Value": 15.5}},
                "RealFeelTemperature": {"Metric": {"Value": 14.8}},
                "RelativeHumidity": 65,
                "Pressure": {"Metric": {"Value": 1013}},
                "Wind": {"Speed": {"Metric": {"Value": 12.0}}, "Direction": {"Localized": "SW"}},
                "WeatherText": "Partly cloudy",
                "WeatherIcon": 2,
                "LocalObservationDateTime": "2024-01-15T14:45:00+00:00"
            }
            """;
    }
}
