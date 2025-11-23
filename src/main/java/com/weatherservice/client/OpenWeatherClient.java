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
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class OpenWeatherClient implements WeatherProviderClient {

    private final WebClient webClient;
    private final String apiKey;
    private final String baseUrl;

    @Override
    public Mono<WeatherResponse> getCurrentWeather(WeatherRequest request) {
        String url = buildCurrentWeatherUrl(request);

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::parseWeatherResponse)
                .map(weather -> enhanceWithProvider(weather, request))
                .doOnSubscribe(s -> log.debug("Fetching weather from OpenWeatherMap for {}", request.getCity()))
                .onErrorResume(WebClientResponseException.class, e -> {
                    if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                        return Mono.error(new LocationNotFoundException("Location not found: " + request.getCity()));
                    } else if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                        log.warn("Rate limit exceeded for OpenWeatherMap");
                        return Mono.error(new ServiceUnavailableException("Rate limit exceeded"));
                    }
                    return Mono.error(new WeatherServiceException("OpenWeatherMap API error", e));
                });
    }

    private String buildCurrentWeatherUrl(WeatherRequest request) {
        StringBuilder url = new StringBuilder(baseUrl + "/weather?appid=" + apiKey);

        if (request.getCity() != null && request.getCountryCode() != null) {
            url.append("&q=").append(request.getCity()).append(",").append(request.getCountryCode());
        } else if (request.getLatitude() != null && request.getLongitude() != null) {
            url.append("&lat=").append(request.getLatitude())
                    .append("&lon=").append(request.getLongitude());
        }

        if (request.getUnits() != null) {
            url.append("&units=").append(request.getUnits());
        }

        if (request.getLanguage() != null) {
            url.append("&lang=").append(request.getLanguage());
        }

        return url.toString();
    }

    private WeatherResponse parseWeatherResponse(JsonNode node) {
        LocationInfo location = LocationInfo.builder()
                .name(node.path("name").asText())
                .country(node.path("sys").path("country").asText())
                .lat(node.path("coord").path("lat").asDouble())
                .lon(node.path("coord").path("lon").asDouble())
                .build();

        JsonNode main = node.path("main");
        JsonNode weather = node.path("weather").get(0);
        JsonNode wind = node.path("wind");

        CurrentWeather current = CurrentWeather.builder()
                .temperature(main.path("temp").asDouble())
                .feelsLike(main.path("feels_like").asDouble())
                .humidity(main.path("humidity").asInt())
                .pressure(main.path("pressure").asInt())
                .windSpeed(wind.path("speed").asDouble())
                .windDirection(getWindDirection(wind.path("deg").asDouble()))
                .description(weather.path("description").asText())
                .icon(weather.path("icon").asText())
                .timestamp(LocalDateTime.now())
                .build();

        return WeatherResponse.builder()
                .location(location)
                .current(current)
                .source(WeatherProvider.OPENWEATHER_MAP)
                .build();
    }

    private String getWindDirection(Double degrees) {
        if (degrees == null) return "N/A";

        String[] directions = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        int index = (int) ((degrees + 11.25) / 22.5) % 16;
        return directions[index];
    }

    private WeatherResponse enhanceWithProvider(WeatherResponse response, WeatherRequest request) {
        return WeatherResponse.builder()
                .location(response.getLocation())
                .current(response.getCurrent())
                .forecast(response.getForecast())
                .source(WeatherProvider.OPENWEATHER_MAP)
                .cachedUntil(LocalDateTime.now().plusMinutes(10))
                .build();
    }

    @Override
    public Mono<List<LocationInfo>> searchLocations(String query, String language, Integer limit) {
        String url = String.format("%s/geo/1.0/direct?q=%s&limit=%d&appid=%s",
                baseUrl, query, limit != null ? limit : 5, apiKey);

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToFlux(JsonNode.class)
                .map(this::parseLocationInfo)
                .collectList()
                .onErrorResume(e -> {
                    log.error("Error searching locations in OpenWeatherMap", e);
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
        return WeatherProvider.OPENWEATHER_MAP.name().equalsIgnoreCase(providerName);
    }

    @Override
    public boolean isRealProvider() {
        return true;
    }
}
