package com.weatherservice.config;

import com.weatherservice.client.*;
import com.weatherservice.resilience.WeatherErrorHandler;
import com.weatherservice.service.WeatherCacheService;
import com.weatherservice.service.WeatherMetrics;
import com.weatherservice.service.WeatherServiceFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@EnableConfigurationProperties(WeatherClientsConfig.WeatherProperties.class)
@RequiredArgsConstructor
public class WeatherClientsConfig {

    private final WeatherProperties properties;
    private final WebClient.Builder webClientBuilder;

    @Bean
    public List<WeatherProviderClient> weatherClients() {
        return List.of(
                openWeatherClient(),
                weatherApiClient(),
                accuWeatherClient()
        );
    }

    @Bean
    public WeatherProviderClient openWeatherClient() {
        if (properties.openweather() == null || properties.openweather().apiKey() == null) {
            log.warn("OpenWeather provider is not configured");
            return new NoOpWeatherProviderClient();
        }

        WebClient webClient = webClientBuilder
                .baseUrl(properties.openweather().baseUrl())
                .build();

        return new OpenWeatherClient(
                webClient,
                properties.openweather().apiKey(),
                properties.openweather().baseUrl()
        );
    }

    @Bean
    public WeatherProviderClient weatherApiClient() {
        if (properties.weatherapi() == null || properties.weatherapi().apiKey() == null) {
            log.warn("WeatherAPI provider is not configured");
            return new NoOpWeatherProviderClient();
        }

        WebClient webClient = webClientBuilder
                .baseUrl(properties.weatherapi().baseUrl())
                .build();

        return new WeatherApiClient(
                webClient,
                properties.weatherapi().apiKey(),
                properties.weatherapi().baseUrl()
        );
    }

    @Bean
    public WeatherProviderClient accuWeatherClient() {
        if (properties.accuweather() == null || properties.accuweather().apiKey() == null) {
            log.warn("AccuWeather provider is not configured");
            return new NoOpWeatherProviderClient();
        }

        WebClient webClient = webClientBuilder
                .baseUrl(properties.accuweather().baseUrl())
                .build();

        return new AccuWeatherClient(
                webClient,
                properties.accuweather().apiKey(),
                properties.accuweather().baseUrl()
        );
    }

    @Bean
    public WeatherServiceFacade weatherServiceFacade(
            List<WeatherProviderClient> clients,
            WeatherCacheService cacheService,
            WeatherErrorHandler errorHandler,
            WeatherMetrics metrics) {

        Map<String, WeatherProviderClient> clientMap = clients.stream()
                .collect(Collectors.toMap(
                        client -> client.getClass().getSimpleName(),
                        Function.identity()
                ));

        return new WeatherServiceFacade(clientMap, cacheService, errorHandler, metrics);
    }

    @ConfigurationProperties(prefix = "weather.providers")
    public record WeatherProperties(
            ProviderConfig openweather,
            ProviderConfig weatherapi,
            ProviderConfig accuweather
    ) {}

    public record ProviderConfig(
            String baseUrl,
            String apiKey,
            Duration timeout,
            Duration cacheTtl
    ) {}
}
