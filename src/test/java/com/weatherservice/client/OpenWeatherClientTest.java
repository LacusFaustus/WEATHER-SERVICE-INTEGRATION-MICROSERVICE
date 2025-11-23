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
class OpenWeatherClientTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private OpenWeatherClient openWeatherClient;
    private final String apiKey = "test-key";
    private final String baseUrl = "http://test-url";

    @BeforeEach
    void setUp() {
        openWeatherClient = new OpenWeatherClient(webClient, apiKey, baseUrl);
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
                "weather": [{"id": 800, "main": "Clear", "description": "clear sky", "icon": "01d"}],
                "main": {"temp": 15.5, "feels_like": 14.8, "pressure": 1013, "humidity": 65},
                "wind": {"speed": 3.6, "deg": 180},
                "name": "London",
                "sys": {"country": "GB"},
                "coord": {"lat": 51.5074, "lon": -0.1278}
            }
            """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(createJsonNode(jsonResponse)));

        // When
        Mono<WeatherResponse> result = openWeatherClient.getCurrentWeather(request);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(response ->
                        response.getLocation().getName().equals("London") &&
                                response.getCurrent().getTemperature().equals(15.5) &&
                                response.getSource().name().equals("OPENWEATHER_MAP"))
                .verifyComplete();
    }

    @Test
    void getCurrentWeather_WithCoordinates_ShouldReturnWeather() {
        // Given
        WeatherRequest request = WeatherRequest.builder()
                .latitude(51.5074)
                .longitude(-0.1278)
                .units("metric")
                .language("en")
                .build();

        String jsonResponse = """
            {
                "weather": [{"id": 801, "main": "Clouds", "description": "few clouds", "icon": "02d"}],
                "main": {"temp": 16.0, "feels_like": 15.5, "pressure": 1015, "humidity": 70},
                "wind": {"speed": 4.2, "deg": 200},
                "name": "London",
                "sys": {"country": "GB"},
                "coord": {"lat": 51.5074, "lon": -0.1278}
            }
            """;

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(createJsonNode(jsonResponse)));

        // When
        Mono<WeatherResponse> result = openWeatherClient.getCurrentWeather(request);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(response ->
                        response.getLocation().getName().equals("London") &&
                                response.getCurrent().getTemperature().equals(16.0))
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
                openWeatherClient.searchLocations("London", "en", 5);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(locations ->
                        locations.size() == 2 &&
                                locations.get(0).getName().equals("London") &&
                                locations.get(1).getName().equals("London"))
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
                openWeatherClient.searchLocations("UnknownCity", "en", 5);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(List::isEmpty)
                .verifyComplete();
    }

    @Test
    void supportsProvider_WithOpenWeather_ShouldReturnTrue() {
        // When & Then
        assertTrue(openWeatherClient.supportsProvider("OPENWEATHER_MAP"));
        assertTrue(openWeatherClient.supportsProvider("openweather_map"));
    }

    @Test
    void supportsProvider_WithOtherProvider_ShouldReturnFalse() {
        // When & Then
        assertFalse(openWeatherClient.supportsProvider("WEATHER_API"));
        assertFalse(openWeatherClient.supportsProvider("ACCUWEATHER"));
    }

    @Test
    void isRealProvider_ShouldReturnTrue() {
        // When & Then
        assertTrue(openWeatherClient.isRealProvider());
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
