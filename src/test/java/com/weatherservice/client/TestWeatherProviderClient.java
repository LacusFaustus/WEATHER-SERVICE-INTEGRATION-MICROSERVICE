package com.weatherservice.client;

import com.weatherservice.model.WeatherRequest;
import com.weatherservice.model.WeatherResponse;
import com.weatherservice.model.WeatherProvider;
import com.weatherservice.model.LocationInfo;
import com.weatherservice.model.CurrentWeather;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

public class TestWeatherProviderClient implements WeatherProviderClient {

    private final WeatherProvider provider;
    private final WeatherResponse testResponse;

    public TestWeatherProviderClient(WeatherProvider provider) {
        this.provider = provider;
        this.testResponse = createTestResponse(provider);
    }

    @Override
    public Mono<WeatherResponse> getCurrentWeather(WeatherRequest request) {
        return Mono.just(testResponse);
    }

    @Override
    public Mono<List<LocationInfo>> searchLocations(String query, String language, Integer limit) {
        return Mono.just(List.of());
    }

    @Override
    public boolean supportsProvider(String providerName) {
        return provider.name().equalsIgnoreCase(providerName);
    }

    @Override
    public boolean isRealProvider() {
        return true;
    }

    private WeatherResponse createTestResponse(WeatherProvider provider) {
        return WeatherResponse.builder()
                .location(LocationInfo.builder()
                        .name("Test City")
                        .country("TC")
                        .lat(0.0)
                        .lon(0.0)
                        .build())
                .current(CurrentWeather.builder()
                        .temperature(20.0)
                        .feelsLike(19.0)
                        .humidity(50)
                        .pressure(1000)
                        .windSpeed(5.0)
                        .description("sunny")
                        .timestamp(LocalDateTime.now())
                        .build())
                .source(provider)
                .cachedUntil(LocalDateTime.now().plusMinutes(10))
                .build();
    }
}
