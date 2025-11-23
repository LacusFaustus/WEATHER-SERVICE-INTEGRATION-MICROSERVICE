package com.weatherservice.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.weatherservice.model.*;
import com.weatherservice.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class WeatherApiClient implements WeatherProviderClient {

    private final WebClient webClient;
    private final String apiKey;
    private final String baseUrl;

    @Override
    public Mono<WeatherResponse> getCurrentWeather(WeatherRequest request) {
        String locationParam = buildLocationParam(request);
        String url = String.format("%s/current.json?key=%s&q=%s&aqi=no",
                baseUrl, apiKey, locationParam);

        if (request.getLanguage() != null) {
            url += "&lang=" + request.getLanguage();
        }

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::parseWeatherResponse)
                .map(weather -> enhanceWithProvider(weather, request))
                .doOnSubscribe(s -> log.debug("Fetching weather from WeatherAPI for {}", request.getCity()))
                .onErrorResume(WebClientResponseException.class, e -> {
                    if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                        return Mono.error(new LocationNotFoundException("Location not found: " + request.getCity()));
                    } else if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                        log.warn("Rate limit exceeded for WeatherAPI");
                        return Mono.error(new ServiceUnavailableException("Rate limit exceeded"));
                    }
                    return Mono.error(new WeatherServiceException("WeatherAPI error", e));
                });
    }

    private String buildLocationParam(WeatherRequest request) {
        if (request.getCity() != null && request.getCountryCode() != null) {
            return request.getCity() + "," + request.getCountryCode();
        } else if (request.getLatitude() != null && request.getLongitude() != null) {
            return request.getLatitude() + "," + request.getLongitude();
        } else if (request.getCity() != null) {
            return request.getCity();
        }
        throw new IllegalArgumentException("Invalid location parameters");
    }

    private WeatherResponse parseWeatherResponse(JsonNode node) {
        JsonNode location = node.path("location");
        JsonNode current = node.path("current");

        LocationInfo locationInfo = LocationInfo.builder()
                .name(location.path("name").asText())
                .country(location.path("country").asText())
                .lat(location.path("lat").asDouble())
                .lon(location.path("lon").asDouble())
                .timezone(location.path("tz_id").asText())
                .build();

        CurrentWeather currentWeather = CurrentWeather.builder()
                .temperature(current.path("temp_c").asDouble())
                .feelsLike(current.path("feelslike_c").asDouble())
                .humidity(current.path("humidity").asInt())
                .pressure(current.path("pressure_mb").asInt())
                .windSpeed(current.path("wind_kph").asDouble() / 3.6)
                .windDirection(current.path("wind_dir").asText())
                .description(current.path("condition").path("text").asText())
                .icon(current.path("condition").path("icon").asText())
                .timestamp(LocalDateTime.parse(current.path("last_updated").asText().substring(0, 16),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .build();

        return WeatherResponse.builder()
                .location(locationInfo)
                .current(currentWeather)
                .source(WeatherProvider.WEATHER_API)
                .build();
    }

    private WeatherResponse enhanceWithProvider(WeatherResponse response, WeatherRequest request) {
        if ("imperial".equals(request.getUnits())) {
            CurrentWeather current = response.getCurrent();
            CurrentWeather converted = CurrentWeather.builder()
                    .temperature(celsiusToFahrenheit(current.getTemperature()))
                    .feelsLike(celsiusToFahrenheit(current.getFeelsLike()))
                    .humidity(current.getHumidity())
                    .pressure(current.getPressure())
                    .windSpeed(kmhToMph(current.getWindSpeed()))
                    .windDirection(current.getWindDirection())
                    .description(current.getDescription())
                    .icon(current.getIcon())
                    .timestamp(current.getTimestamp())
                    .build();

            return WeatherResponse.builder()
                    .location(response.getLocation())
                    .current(converted)
                    .forecast(response.getForecast())
                    .source(WeatherProvider.WEATHER_API)
                    .cachedUntil(LocalDateTime.now().plusMinutes(30))
                    .build();
        }

        return WeatherResponse.builder()
                .location(response.getLocation())
                .current(response.getCurrent())
                .forecast(response.getForecast())
                .source(WeatherProvider.WEATHER_API)
                .cachedUntil(LocalDateTime.now().plusMinutes(30))
                .build();
    }

    private Double celsiusToFahrenheit(Double celsius) {
        return celsius != null ? (celsius * 9/5) + 32 : null;
    }

    private Double kmhToMph(Double kmh) {
        return kmh != null ? kmh * 0.621371 : null;
    }

    @Override
    public Mono<List<LocationInfo>> searchLocations(String query, String language, Integer limit) {
        String url = String.format("%s/search.json?key=%s&q=%s", baseUrl, apiKey, query);

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToFlux(JsonNode.class)
                .map(this::parseLocationInfo)
                .take(limit != null ? limit : 5)
                .collectList()
                .onErrorResume(e -> {
                    log.error("Error searching locations in WeatherAPI", e);
                    return Mono.just(List.of());
                });
    }

    private LocationInfo parseLocationInfo(JsonNode node) {
        return LocationInfo.builder()
                .name(node.path("name").asText())
                .country(node.path("country").asText())
                .lat(node.path("lat").asDouble())
                .lon(node.path("lon").asDouble())
                .build();
    }

    @Override
    public boolean supportsProvider(String providerName) {
        return WeatherProvider.WEATHER_API.name().equalsIgnoreCase(providerName);
    }

    @Override
    public boolean isRealProvider() {
        return true;
    }
}
