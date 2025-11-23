package com.weatherservice.controller;

import com.weatherservice.model.LocationInfo;
import com.weatherservice.model.LocationSearchRequest;
import com.weatherservice.service.LocationSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(LocationController.class)
class LocationControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private LocationSearchService locationSearchService;

    @Test
    void searchLocations_WithQuery_ShouldReturnLocations() {
        // Given
        LocationInfo location1 = LocationInfo.builder()
                .name("London")
                .country("GB")
                .lat(51.5074)
                .lon(-0.1278)
                .build();

        LocationInfo location2 = LocationInfo.builder()
                .name("London")
                .country("CA")
                .lat(42.9849)
                .lon(-81.2453)
                .build();

        when(locationSearchService.searchLocations(any()))
                .thenReturn(Flux.fromIterable(Arrays.asList(location1, location2)));

        // When & Then
        webTestClient.get()
                .uri("/api/v1/weather/locations?query=London&limit=5&language=en")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(LocationInfo.class)
                .hasSize(2)
                .contains(location1, location2);
    }

    @Test
    void searchLocationsPost_WithValidRequest_ShouldReturnLocations() {
        // Given
        LocationSearchRequest request = LocationSearchRequest.builder()
                .query("Paris")
                .limit(3)
                .language("fr")
                .build();

        LocationInfo location = LocationInfo.builder()
                .name("Paris")
                .country("FR")
                .lat(48.8566)
                .lon(2.3522)
                .build();

        when(locationSearchService.searchLocations(any()))
                .thenReturn(Flux.just(location));

        // When & Then
        webTestClient.post()
                .uri("/api/v1/weather/locations/search")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(LocationInfo.class)
                .hasSize(1)
                .value(locations -> {
                    assertEquals("Paris", locations.get(0).getName());
                    assertEquals("FR", locations.get(0).getCountry());
                });
    }
}
