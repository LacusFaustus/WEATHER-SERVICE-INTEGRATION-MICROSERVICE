package com.weatherservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitingService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final WeatherMetrics metrics;

    private static final int DEFAULT_LIMIT = 100;
    private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(1);

    public Mono<Boolean> isAllowed(String clientId) {
        return isAllowed(clientId, DEFAULT_LIMIT, DEFAULT_WINDOW);
    }

    public Mono<Boolean> isAllowed(String clientId, int limit, Duration window) {
        String key = "rate_limit:" + clientId + ":" + getCurrentWindowKey();

        return redisTemplate.opsForValue()
                .increment(key)
                .flatMap(count -> {
                    if (count == 1) {
                        // Устанавливаем TTL для нового ключа
                        return redisTemplate.expire(key, window)
                                .thenReturn(count);
                    }
                    return Mono.just(count);
                })
                .map(count -> {
                    boolean allowed = count <= limit;
                    if (!allowed) {
                        metrics.recordRateLimitExceeded();
                        log.warn("Rate limit exceeded for client: {} ({} requests)", clientId, count);
                    }
                    return allowed;
                })
                .onErrorReturn(true); // В случае ошибки Redis разрешаем запрос
    }

    public Mono<Long> getRemainingRequests(String clientId) {
        return getRemainingRequests(clientId, DEFAULT_LIMIT, DEFAULT_WINDOW);
    }

    public Mono<Long> getRemainingRequests(String clientId, int limit, Duration window) {
        String key = "rate_limit:" + clientId + ":" + getCurrentWindowKey();

        return redisTemplate.opsForValue()
                .get(key)
                .map(Long::parseLong)
                .map(count -> Math.max(0, limit - count))
                .defaultIfEmpty((long) limit);
    }

    /**
     * Сброс rate limit для конкретного клиента
     */
    public Mono<Boolean> resetRateLimit(String clientId) {
        String key = "rate_limit:" + clientId + ":" + getCurrentWindowKey();
        return redisTemplate.delete(key)
                .map(count -> count > 0);
    }

    /**
     * Получение текущего количества запросов
     */
    public Mono<Long> getCurrentRequestCount(String clientId) {
        String key = "rate_limit:" + clientId + ":" + getCurrentWindowKey();
        return redisTemplate.opsForValue()
                .get(key)
                .map(Long::parseLong)
                .defaultIfEmpty(0L);
    }

    /**
     * Получение информации о rate limit
     */
    public Mono<RateLimitInfo> getRateLimitInfo(String clientId) {
        return getCurrentRequestCount(clientId)
                .map(currentCount -> {
                    Instant resetTime = Instant.now().plusSeconds(60 - (Instant.now().getEpochSecond() % 60));

                    return RateLimitInfo.builder()
                            .clientId(clientId)
                            .currentRequests(currentCount)
                            .remainingRequests(Math.max(0, DEFAULT_LIMIT - currentCount))
                            .limit(DEFAULT_LIMIT)
                            .windowMinutes((int) DEFAULT_WINDOW.toMinutes())
                            .resetTime(resetTime)
                            .build();
                });
    }

    private String getCurrentWindowKey() {
        // Используем текущую минуту как ключ окна
        return String.valueOf(Instant.now().getEpochSecond() / 60);
    }

    // DTO для информации о rate limit
    @lombok.Data
    @lombok.Builder
    public static class RateLimitInfo {
        private String clientId;
        private Long currentRequests;
        private Long remainingRequests;
        private Integer limit;
        private Integer windowMinutes;
        private Instant resetTime;
    }
}
