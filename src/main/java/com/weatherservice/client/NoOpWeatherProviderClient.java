package com.weatherservice.client;

import com.weatherservice.model.WeatherRequest;
import com.weatherservice.model.WeatherResponse;
import com.weatherservice.model.LocationInfo;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import java.util.List;

@Slf4j
public class NoOpWeatherProviderClient implements WeatherProviderClient {

    @Override
    public Mono<WeatherResponse> getCurrentWeather(WeatherRequest request) {
        log.debug("No-op client: provider not configured");
        return Mono.empty();
    }

    @Override
    public Mono<List<LocationInfo>> searchLocations(String query, String language, Integer limit) {
        log.debug("No-op client: location search not available");
        return Mono.just(List.of());
    }

    @Override
    public boolean supportsProvider(String providerName) {
        return false;
    }

    @Override
    public boolean isRealProvider() {
        return false;
    }
}
