package com.weatherservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitingServiceExtendedTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private WeatherMetrics metrics;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private RateLimitingService rateLimitingService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimitingService = new RateLimitingService(redisTemplate, metrics);
    }

    @Test
    void isAllowed_WithCustomLimitAndWindow_ShouldWorkCorrectly() {
        // Given
        String clientId = "127.0.0.1";
        int customLimit = 50;
        Duration customWindow = Duration.ofMinutes(5);

        lenient().when(valueOperations.increment(anyString())).thenReturn(Mono.just(25L));
        lenient().when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        // When
        Mono<Boolean> result = rateLimitingService.isAllowed(clientId, customLimit, customWindow);

        // Then
        StepVerifier.create(result)
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void getRemainingRequests_WithCustomLimit_ShouldReturnCorrectCount() {
        // Given
        String clientId = "127.0.0.1";
        int customLimit = 50;
        Duration customWindow = Duration.ofMinutes(5);

        when(valueOperations.get(anyString())).thenReturn(Mono.just("30"));

        // When
        Mono<Long> result = rateLimitingService.getRemainingRequests(clientId, customLimit, customWindow);

        // Then
        StepVerifier.create(result)
                .expectNext(20L)
                .verifyComplete();
    }

    @Test
    void getRateLimitInfo_ShouldReturnCompleteInfo() {
        // Given
        String clientId = "127.0.0.1";
        when(valueOperations.get(anyString())).thenReturn(Mono.just("75"));

        // When
        Mono<RateLimitingService.RateLimitInfo> result = rateLimitingService.getRateLimitInfo(clientId);

        // Then
        StepVerifier.create(result)
                .expectNextMatches(info ->
                        info.getClientId().equals(clientId) &&
                                info.getCurrentRequests() == 75L &&
                                info.getRemainingRequests() == 25L &&
                                info.getLimit() == 100 &&
                                info.getResetTime() != null)
                .verifyComplete();
    }

    @Test
    void resetRateLimit_WhenKeyDoesNotExist_ShouldReturnFalse() {
        // Given
        String clientId = "127.0.0.1";
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(0L));

        // When
        Mono<Boolean> result = rateLimitingService.resetRateLimit(clientId);

        // Then
        StepVerifier.create(result)
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void getCurrentRequestCount_WhenRedisError_ShouldReturnZero() {
        // Given
        String clientId = "127.0.0.1";
        when(valueOperations.get(anyString())).thenReturn(Mono.empty());

        // When
        Mono<Long> result = rateLimitingService.getCurrentRequestCount(clientId);

        // Then
        StepVerifier.create(result)
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    void isAllowed_WhenExpireFails_ShouldStillWork() {
        // Given
        String clientId = "127.0.0.1";
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(1L));
        lenient().when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(false));

        // When
        Mono<Boolean> result = rateLimitingService.isAllowed(clientId);

        // Then
        StepVerifier.create(result)
                .expectNext(true)
                .verifyComplete();
    }
}
