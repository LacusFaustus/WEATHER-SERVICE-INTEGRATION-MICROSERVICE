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
public class AccuWeatherClient implements WeatherProviderClient {

    private final WebClient webClient;
    private final String apiKey;
    private final String baseUrl;

    @Override
    public Mono<WeatherResponse> getCurrentWeather(WeatherRequest request) {
        return getLocationKey(request)
                .flatMap(locationKey -> getWeatherByLocationKey(locationKey, request))
                .doOnSubscribe(s -> log.debug("Fetching weather from AccuWeather for {}", request.getCity()));
    }

    private Mono<String> getLocationKey(WeatherRequest request) {
        String locationUrl;

        if (request.getCity() != null) {
            locationUrl = String.format("%s/locations/v1/cities/search?apikey=%s&q=%s&language=%s",
                    baseUrl, apiKey, request.getCity(),
                    request.getLanguage() != null ? request.getLanguage() : "en");
        } else if (request.getLatitude() != null && request.getLongitude() != null) {
            locationUrl = String.format("%s/locations/v1/cities/geoposition/search?apikey=%s&q=%s,%s",
                    baseUrl, apiKey, request.getLatitude(), request.getLongitude());
        } else {
            return Mono.error(new IllegalArgumentException("Invalid location parameters"));
        }

        return webClient.get()
                .uri(locationUrl)
                .retrieve()
                .bodyToFlux(JsonNode.class)
                .next()
                .map(node -> node.path("Key").asText())
                .switchIfEmpty(Mono.error(new LocationNotFoundException("Location not found: " + request.getCity())));
    }

    private Mono<WeatherResponse> getWeatherByLocationKey(String locationKey, WeatherRequest request) {
        String weatherUrl = String.format("%s/currentconditions/v1/%s?apikey=%s&details=true&language=%s",
                baseUrl, locationKey, apiKey,
                request.getLanguage() != null ? request.getLanguage() : "en");

        return webClient.get()
                .uri(weatherUrl)
                .retrieve()
                .bodyToFlux(JsonNode.class)
                .next()
                .map(weatherNode -> parseWeatherResponse(weatherNode, request))
                .onErrorResume(WebClientResponseException.class, e -> {
                    if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                        log.warn("Rate limit exceeded for AccuWeather");
                        return Mono.error(new ServiceUnavailableException("Rate limit exceeded"));
                    }
                    return Mono.error(new WeatherServiceException("AccuWeather API error", e));
                });
    }

    private WeatherResponse parseWeatherResponse(JsonNode weatherNode, WeatherRequest request) {
        LocationInfo location = LocationInfo.builder()
                .name(request.getCity())
                .country(request.getCountryCode())
                .build();

        CurrentWeather current = CurrentWeather.builder()
                .temperature(weatherNode.path("Temperature").path("Metric").path("Value").asDouble())
                .feelsLike(weatherNode.path("RealFeelTemperature").path("Metric").path("Value").asDouble())
                .humidity(weatherNode.path("RelativeHumidity").asInt())
                .pressure(weatherNode.path("Pressure").path("Metric").path("Value").asInt())
                .windSpeed(weatherNode.path("Wind").path("Speed").path("Metric").path("Value").asDouble())
                .windDirection(weatherNode.path("Wind").path("Direction").path("Localized").asText())
                .description(weatherNode.path("WeatherText").asText())
                .icon(String.valueOf(weatherNode.path("WeatherIcon").asInt()))
                .timestamp(LocalDateTime.parse(weatherNode.path("LocalObservationDateTime").asText().substring(0, 19),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")))
                .build();

        return WeatherResponse.builder()
                .location(location)
                .current(current)
                .source(WeatherProvider.ACCUWEATHER)
                .cachedUntil(LocalDateTime.now().plusMinutes(30))
                .build();
    }

    @Override
    public Mono<List<LocationInfo>> searchLocations(String query, String language, Integer limit) {
        String url = String.format("%s/locations/v1/cities/autocomplete?apikey=%s&q=%s&language=%s",
                baseUrl, apiKey, query, language != null ? language : "en");

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToFlux(JsonNode.class)
                .map(this::parseLocationInfo)
                .take(limit != null ? limit : 5)
                .collectList()
                .onErrorResume(e -> {
                    log.error("Error searching locations in AccuWeather", e);
                    return Mono.just(List.of());
                });
    }

    private LocationInfo parseLocationInfo(JsonNode node) {
        return LocationInfo.builder()
                .name(node.path("LocalizedName").asText())
                .country(node.path("Country").path("LocalizedName").asText())
                // AccuWeather не всегда предоставляет координаты в ответе autocomplete
                .build();
    }

    @Override
    public boolean supportsProvider(String providerName) {
        return WeatherProvider.ACCUWEATHER.name().equalsIgnoreCase(providerName);
    }

    @Override
    public boolean isRealProvider() {
        return true;
    }
}
