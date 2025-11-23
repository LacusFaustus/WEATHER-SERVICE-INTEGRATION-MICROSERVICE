package com.weatherservice.client;

import com.weatherservice.model.WeatherRequest;
import com.weatherservice.model.WeatherResponse;
import com.weatherservice.model.LocationInfo;
import reactor.core.publisher.Mono;
import java.util.List;

public interface WeatherProviderClient {
    Mono<WeatherResponse> getCurrentWeather(WeatherRequest request);
    Mono<List<LocationInfo>> searchLocations(String query, String language, Integer limit);
    boolean supportsProvider(String providerName);

    // Добавляем метод для проверки, является ли клиент реальным провайдером
    default boolean isRealProvider() {
        return true;
    }
}
