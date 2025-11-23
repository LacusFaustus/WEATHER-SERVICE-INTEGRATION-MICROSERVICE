package com.weatherservice.health;

import com.weatherservice.service.WeatherCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeatherServiceHealthIndicatorTest {

    @Mock
    private ReactiveRedisConnectionFactory redisConnectionFactory;

    @Mock
    private ReactiveRedisConnection redisConnection;

    @Mock
    private WeatherCacheService cacheService;

    @Test
    void health_WhenRedisConnected_ShouldReturnUp() {
        // Given
        WeatherServiceHealthIndicator healthIndicator =
                new WeatherServiceHealthIndicator(redisConnectionFactory, cacheService);

        when(redisConnectionFactory.getReactiveConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn(Mono.just("PONG"));
        doNothing().when(redisConnection).close();

        // When & Then
        StepVerifier.create(healthIndicator.health())
                .expectNextMatches(health ->
                        "UP".equals(health.getStatus().getCode()) &&
                                health.getDetails().containsKey("redis") &&
                                "connected".equals(health.getDetails().get("redis")))
                .verifyComplete();
    }

    @Test
    void health_WhenRedisDisconnected_ShouldReturnDown() {
        // Given
        WeatherServiceHealthIndicator healthIndicator =
                new WeatherServiceHealthIndicator(redisConnectionFactory, cacheService);

        when(redisConnectionFactory.getReactiveConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn(Mono.just("NOT_PONG"));
        doNothing().when(redisConnection).close();

        // When & Then
        StepVerifier.create(healthIndicator.health())
                .expectNextMatches(health ->
                        "DOWN".equals(health.getStatus().getCode()) &&
                                health.getDetails().containsKey("redis") &&
                                "disconnected".equals(health.getDetails().get("redis")))
                .verifyComplete();
    }

    @Test
    void health_WhenRedisError_ShouldReturnDown() {
        // Given
        WeatherServiceHealthIndicator healthIndicator =
                new WeatherServiceHealthIndicator(redisConnectionFactory, cacheService);

        when(redisConnectionFactory.getReactiveConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn(Mono.error(new RuntimeException("Connection failed")));
        doNothing().when(redisConnection).close();

        // When & Then
        StepVerifier.create(healthIndicator.health())
                .expectNextMatches(health ->
                        "DOWN".equals(health.getStatus().getCode()) &&
                                health.getDetails().containsKey("redis") &&
                                "disconnected".equals(health.getDetails().get("redis")) &&
                                health.getDetails().containsKey("service"))
                .verifyComplete();
    }

    @Test
    void health_WhenRedisConnectionFails_ShouldReturnDown() {
        // Given
        WeatherServiceHealthIndicator healthIndicator =
                new WeatherServiceHealthIndicator(redisConnectionFactory, cacheService);

        RuntimeException connectionException = new RuntimeException("Cannot connect to Redis");
        when(redisConnectionFactory.getReactiveConnection()).thenThrow(connectionException);

        // When & Then
        StepVerifier.create(healthIndicator.health())
                .expectNextMatches(health ->
                        "DOWN".equals(health.getStatus().getCode()) &&
                                health.getDetails().containsKey("redis") &&
                                "disconnected".equals(health.getDetails().get("redis")) &&
                                health.getDetails().containsKey("service") &&
                                health.getDetails().containsKey("error") &&  // Проверяем наличие error
                                connectionException.getMessage().equals(health.getDetails().get("error")))
                .verifyComplete();
    }
}
