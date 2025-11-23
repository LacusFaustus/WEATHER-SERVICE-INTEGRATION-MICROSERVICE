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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeatherApiClientTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private WeatherApiClient weatherApiClient;
    private final String apiKey = "test-key";
    private final String baseUrl = "http://test-url";

    @BeforeEach
    void setUp() {
        weatherApiClient = new WeatherApiClient(webClient, apiKey, baseUrl);
    }

    @Test
    void getCurrentWeather_WithCityAndCountry_ShouldReturnWeather() {
        // Given
        WeatherRequest request = WeatherRequest.builder()
                .city("London")
                .countryCode("GB")
                .units("metric")
                .language("en")
                .build();

        String jsonResponse = """
            {
                "location": {
                    "name": "London",
                    "country": "GB",
                    "lat": 51.5074,
                    "lon": -0.1278,
                    "tz_id": "Europe/London"
                },
                "current": {
                    "temp_c": 15.5,
                    "feelslike_c": 14.8,
                    "humidity": 65,
                    "pressure_mb": 1013,
                    "wind_kph": 12.96,
                    "wind_dir": "SW",
                    "condition": {"text": "Partly cloudy", "icon": "//cdn.weatherapi.com/weather/64x64/day/116.png"},
                    "last_updated": "2024-01-15 14:45"
                }
            }
            """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(createJsonNode(jsonResponse)));

        // When
        Mono<WeatherResponse> result = weatherApiClient.getCurrentWeather(request);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(response ->
                        response.getLocation().getName().equals("London") &&
                                response.getCurrent().getTemperature().equals(15.5) &&
                                response.getSource().name().equals("WEATHER_API"))
                .verifyComplete();
    }

    @Test
    void searchLocations_ShouldReturnLocations() {
        // Given
        String jsonResponse = """
            [
                {"name": "London", "country": "GB", "lat": 51.5074, "lon": -0.1278},
                {"name": "London", "country": "CA", "lat": 42.9849, "lon": -81.2453}
            ]
            """;

        JsonNode[] nodes = new JsonNode[2];
        nodes[0] = createJsonNode("{\"name\": \"London\", \"country\": \"GB\", \"lat\": 51.5074, \"lon\": -0.1278}");
        nodes[1] = createJsonNode("{\"name\": \"London\", \"country\": \"CA\", \"lat\": 42.9849, \"lon\": -81.2453}");

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(JsonNode.class)).thenReturn(reactor.core.publisher.Flux.just(nodes));

        // When
        @SuppressWarnings("unchecked")
        Mono<List<com.weatherservice.model.LocationInfo>> result =
                weatherApiClient.searchLocations("London", "en", 5);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(locations -> locations.size() == 2)
                .verifyComplete();
    }

    @Test
    void searchLocations_WithEmptyResult_ShouldReturnEmptyList() {
        // Given
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToFlux(JsonNode.class)).thenReturn(reactor.core.publisher.Flux.empty());

        // When
        @SuppressWarnings("unchecked")
        Mono<List<com.weatherservice.model.LocationInfo>> result =
                weatherApiClient.searchLocations("UnknownCity", "en", 5);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(List::isEmpty)
                .verifyComplete();
    }

    @Test
    void supportsProvider_WithWeatherApi_ShouldReturnTrue() {
        // When & Then
        assertTrue(weatherApiClient.supportsProvider("WEATHER_API"));
        assertTrue(weatherApiClient.supportsProvider("weather_api"));
    }

    @Test
    void supportsProvider_WithOtherProvider_ShouldReturnFalse() {
        // When & Then
        assertFalse(weatherApiClient.supportsProvider("OPENWEATHER_MAP"));
        assertFalse(weatherApiClient.supportsProvider("ACCUWEATHER"));
    }

    @Test
    void isRealProvider_ShouldReturnTrue() {
        // When & Then
        assertTrue(weatherApiClient.isRealProvider());
    }

    private JsonNode createJsonNode(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
