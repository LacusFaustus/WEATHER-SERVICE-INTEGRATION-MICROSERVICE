package com.weatherservice.service;

import com.weatherservice.client.WeatherProviderClient;
import com.weatherservice.model.LocationInfo;
import com.weatherservice.model.LocationSearchRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationSearchService {

    private final List<WeatherProviderClient> clients;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    public Flux<LocationInfo> searchLocations(LocationSearchRequest request) {
        String cacheKey = "weather:search:" + generateSearchKey(request);

        return redisTemplate.opsForValue().get(cacheKey)
                .flatMapMany(cachedData -> {
                    if (cachedData instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<LocationInfo> locations = (List<LocationInfo>) cachedData;
                        log.debug("Cache hit for location search: {}", cacheKey);
                        return Flux.fromIterable(locations);
                    }
                    return Flux.empty();
                })
                .switchIfEmpty(Flux.defer(() -> fetchFromProviders(request)
                        .collectList()
                        .flatMap(locations -> cacheResults(cacheKey, locations)
                                .thenReturn(locations))
                        .flatMapMany(Flux::fromIterable)))
                .distinct(location -> location.getName() + ":" + location.getCountry())
                .take(request.getLimit() != null ? request.getLimit() : 5);
    }

    private Flux<LocationInfo> fetchFromProviders(LocationSearchRequest request) {
        return Flux.fromIterable(clients)
                .flatMap(client -> client.searchLocations(request.getQuery(),
                        request.getLanguage(), request.getLimit()))
                .onErrorResume(e -> {
                    log.warn("Error searching locations with provider: {}", e.getMessage());
                    return Flux.empty();
                })
                .flatMapIterable(list -> list);
    }

    private Mono<Boolean> cacheResults(String cacheKey, List<LocationInfo> locations) {
        if (locations.isEmpty()) {
            return Mono.just(false);
        }

        List<LocationInfo> sortedLocations = locations.stream()
                .sorted(Comparator.comparing(LocationInfo::getName))
                .collect(Collectors.toList());

        return redisTemplate.opsForValue()
                .set(cacheKey, sortedLocations, Duration.ofMinutes(30))
                .doOnSuccess(success -> {
                    if (success) {
                        log.debug("Cached location search results for key: {}", cacheKey);
                    }
                });
    }

    private String generateSearchKey(LocationSearchRequest request) {
        return request.getQuery().toLowerCase().replaceAll("\\s+", "_") +
                "_" + (request.getLanguage() != null ? request.getLanguage() : "en");
    }
}
