package com.weatherservice.service;

import com.weatherservice.model.WeatherRequest;
import com.weatherservice.model.WeatherResponse;
import com.weatherservice.util.WeatherKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherCacheService {

    private final ReactiveRedisTemplate<String, WeatherResponse> redisTemplate;
    private final WeatherKeyGenerator keyGenerator;

    // In-memory fallback cache если Redis недоступен
    private final ConcurrentHashMap<String, WeatherResponse> memoryCache = new ConcurrentHashMap<>();

    public Mono<WeatherResponse> getCachedWeather(WeatherRequest request) {
        if (request == null) {
            return Mono.empty();
        }

        String cacheKey = keyGenerator.generateCacheKey(request);

        return redisTemplate.opsForValue().get(cacheKey)
                .doOnNext(data -> log.debug("Redis cache hit for key: {}", cacheKey))
                .onErrorResume(e -> {
                    log.warn("Redis error, trying memory cache for key: {}", cacheKey, e);
                    // Fallback to memory cache
                    WeatherResponse cached = memoryCache.get(cacheKey);
                    if (cached != null && isCacheValid(cached)) {
                        log.debug("Memory cache hit for key: {}", cacheKey);
                        return Mono.just(cached);
                    }
                    return Mono.empty();
                });
    }

    public Mono<Boolean> cacheWeatherData(WeatherRequest request, WeatherResponse response) {
        return cacheWeatherData(request, response, getCacheTtl(request));
    }

    public Mono<Boolean> cacheWeatherData(WeatherRequest request, WeatherResponse response, Duration ttl) {
        if (request == null || response == null) {
            return Mono.just(false);
        }

        String cacheKey = keyGenerator.generateCacheKey(request);

        WeatherResponse updatedResponse = WeatherResponse.builder()
                .location(response.getLocation())
                .current(response.getCurrent())
                .forecast(response.getForecast())
                .source(response.getSource())
                .cachedUntil(LocalDateTime.now().plus(ttl))
                .build();

        return redisTemplate.opsForValue()
                .set(cacheKey, updatedResponse, ttl)
                .doOnSuccess(success -> {
                    if (success) {
                        log.debug("Cached weather data in Redis for key: {}", cacheKey);
                        // Также сохраняем в memory cache как fallback
                        memoryCache.put(cacheKey, updatedResponse);
                    }
                })
                .onErrorResume(e -> {
                    log.warn("Redis caching failed, using memory cache for key: {}", cacheKey, e);
                    // Fallback to memory cache
                    memoryCache.put(cacheKey, updatedResponse);
                    return Mono.just(true);
                });
    }

    public Mono<Boolean> evictWeatherData(WeatherRequest request) {
        if (request == null) {
            return Mono.just(false);
        }

        String cacheKey = keyGenerator.generateCacheKey(request);

        return redisTemplate.delete(cacheKey)
                .map(count -> count > 0)
                .doOnSuccess(deleted -> {
                    if (deleted) {
                        log.debug("Evicted cache from Redis for key: {}", cacheKey);
                    }
                    // Также удаляем из memory cache
                    memoryCache.remove(cacheKey);
                })
                .onErrorResume(e -> {
                    log.warn("Redis eviction failed, clearing memory cache for key: {}", cacheKey, e);
                    memoryCache.remove(cacheKey);
                    return Mono.just(true);
                });
    }

    private boolean isCacheValid(WeatherResponse response) {
        return response != null &&
                response.getCachedUntil() != null &&
                response.getCachedUntil().isAfter(LocalDateTime.now());
    }

    private Duration getCacheTtl(WeatherRequest request) {
        if (request.getProvider() != null) {
            switch (request.getProvider()) {
                case OPENWEATHER_MAP:
                    return Duration.ofMinutes(10);
                case WEATHER_API:
                    return Duration.ofMinutes(30);
                case ACCUWEATHER:
                    return Duration.ofMinutes(30);
                default:
                    return Duration.ofMinutes(15);
            }
        }
        return Duration.ofMinutes(15);
    }
}
