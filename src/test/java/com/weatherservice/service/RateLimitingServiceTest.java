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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitingServiceTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private WeatherMetrics metrics;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private RateLimitingService rateLimitingService;

    @BeforeEach
    void setUp() {
        // Используем lenient чтобы избежать UnnecessaryStubbing
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimitingService = new RateLimitingService(redisTemplate, metrics);
    }

    @Test
    void isAllowed_WhenUnderLimit_ShouldReturnTrue() {
        // Given
        String clientId = "127.0.0.1";
        when(valueOperations.increment(any())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(any(), any(Duration.class))).thenReturn(Mono.just(true));

        // When
        Mono<Boolean> result = rateLimitingService.isAllowed(clientId);

        // Then
        StepVerifier.create(result)
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void isAllowed_WhenOverLimit_ShouldReturnFalse() {
        // Given
        String clientId = "127.0.0.1";
        when(valueOperations.increment(any())).thenReturn(Mono.just(101L));
        // Убираем ненужный стаб для expire

        // When
        Mono<Boolean> result = rateLimitingService.isAllowed(clientId);

        // Then
        StepVerifier.create(result)
                .expectNext(false)
                .verifyComplete();

        verify(metrics).recordRateLimitExceeded();
    }

    @Test
    void isAllowed_WhenRedisError_ShouldReturnTrue() {
        // Given
        String clientId = "127.0.0.1";
        when(valueOperations.increment(any())).thenReturn(Mono.error(new RuntimeException("Redis error")));

        // When
        Mono<Boolean> result = rateLimitingService.isAllowed(clientId);

        // Then
        StepVerifier.create(result)
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void getRemainingRequests_ShouldReturnCorrectCount() {
        // Given
        String clientId = "127.0.0.1";
        when(valueOperations.get(any())).thenReturn(Mono.just("50"));

        // When
        Mono<Long> result = rateLimitingService.getRemainingRequests(clientId);

        // Then
        StepVerifier.create(result)
                .expectNext(50L)
                .verifyComplete();
    }

    @Test
    void getRemainingRequests_WhenNoData_ShouldReturnLimit() {
        // Given
        String clientId = "127.0.0.1";
        when(valueOperations.get(any())).thenReturn(Mono.empty());

        // When
        Mono<Long> result = rateLimitingService.getRemainingRequests(clientId);

        // Then
        StepVerifier.create(result)
                .expectNext(100L)
                .verifyComplete();
    }

    @Test
    void getCurrentRequestCount_ShouldReturnCount() {
        // Given
        String clientId = "127.0.0.1";
        when(valueOperations.get(any())).thenReturn(Mono.just("75"));

        // When
        Mono<Long> result = rateLimitingService.getCurrentRequestCount(clientId);

        // Then
        StepVerifier.create(result)
                .expectNext(75L)
                .verifyComplete();
    }

    @Test
    void getCurrentRequestCount_WhenNoData_ShouldReturnZero() {
        // Given
        String clientId = "127.0.0.1";
        when(valueOperations.get(any())).thenReturn(Mono.empty());

        // When
        Mono<Long> result = rateLimitingService.getCurrentRequestCount(clientId);

        // Then
        StepVerifier.create(result)
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    void resetRateLimit_ShouldDeleteKey() {
        // Given
        String clientId = "127.0.0.1";
        // Используем any() чтобы избежать неоднозначности
        when(redisTemplate.delete(any(String.class))).thenReturn(Mono.just(1L));

        // When
        Mono<Boolean> result = rateLimitingService.resetRateLimit(clientId);

        // Then
        StepVerifier.create(result)
                .expectNext(true)
                .verifyComplete();
    }
}
