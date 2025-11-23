package com.weatherservice.controller;

import com.weatherservice.model.LocationInfo;
import com.weatherservice.model.LocationSearchRequest;
import com.weatherservice.service.LocationSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/weather")
@RequiredArgsConstructor
public class LocationController {

    private final LocationSearchService locationSearchService;

    @GetMapping("/locations")
    public Flux<LocationInfo> searchLocations(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") Integer limit,
            @RequestParam(defaultValue = "en") String language) {

        LocationSearchRequest request = LocationSearchRequest.builder()
                .query(query)
                .limit(limit)
                .language(language)
                .build();

        return locationSearchService.searchLocations(request);
    }

    @PostMapping("/locations/search")
    public Flux<LocationInfo> searchLocationsPost(@Valid @RequestBody LocationSearchRequest request) {
        return locationSearchService.searchLocations(request);
    }
}
