package com.weatherservice.controller;

import com.weatherservice.model.*;
import com.weatherservice.service.RateLimitingService;
import com.weatherservice.service.WeatherServiceFacade;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherServiceFacade weatherService;
    private final RateLimitingService rateLimitingService;

    @GetMapping("/current")
    public Mono<WeatherResponse> getCurrentWeather(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestParam(defaultValue = "metric") String units,
            @RequestParam(defaultValue = "en") String lang,
            @RequestParam(required = false) String provider,
            ServerWebExchange exchange) {

        // Rate limiting по IP
        String clientId = getClientId(exchange);

        return rateLimitingService.isAllowed(clientId)
                .flatMap(allowed -> {
                    if (!allowed) {
                        return Mono.error(new com.weatherservice.exception.RateLimitExceededException("Rate limit exceeded"));
                    }

                    WeatherRequest request = WeatherRequest.builder()
                            .city(city)
                            .countryCode(country)
                            .latitude(lat)
                            .longitude(lon)
                            .units(units)
                            .language(lang)
                            .provider(provider != null ?
                                    WeatherProvider.valueOf(provider.toUpperCase()) : null)
                            .build();

                    return weatherService.getWeather(request);
                });
    }

    @GetMapping("/aggregated")
    public Mono<AggregatedWeatherResponse> getAggregatedWeather(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            @RequestParam(defaultValue = "metric") String units,
            @RequestParam(defaultValue = "en") String lang,
            ServerWebExchange exchange) {

        String clientId = getClientId(exchange);

        return rateLimitingService.isAllowed(clientId)
                .flatMap(allowed -> {
                    if (!allowed) {
                        return Mono.error(new com.weatherservice.exception.RateLimitExceededException("Rate limit exceeded"));
                    }

                    WeatherRequest request = WeatherRequest.builder()
                            .city(city)
                            .countryCode(country)
                            .latitude(lat)
                            .longitude(lon)
                            .units(units)
                            .language(lang)
                            .build();

                    return weatherService.getAggregatedWeather(request);
                });
    }

    @GetMapping("/rate-limit")
    public Mono<RateLimitInfo> getRateLimitInfo(ServerWebExchange exchange) {
        String clientId = getClientId(exchange);

        return rateLimitingService.getRemainingRequests(clientId)
                .map(remaining -> RateLimitInfo.builder()
                        .clientId(clientId)
                        .remainingRequests(remaining)
                        .limit(100)
                        .windowMinutes(1)
                        .build());
    }

    @GetMapping("/providers")
    public WeatherProvider[] getAvailableProviders() {
        return WeatherProvider.values();
    }

    private String getClientId(ServerWebExchange exchange) {
        // Используем IP адрес клиента для rate limiting
        return exchange.getRequest().getRemoteAddress() != null ?
                exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() :
                "unknown";
    }

    // DTO для информации о rate limit
    @Data
    @Builder
    public static class RateLimitInfo {
        private String clientId;
        private Long remainingRequests;
        private Integer limit;
        private Integer windowMinutes;
    }
}
