package com.weatherservice.health;

import com.weatherservice.service.WeatherCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherServiceHealthIndicator implements ReactiveHealthIndicator {

    private final ReactiveRedisConnectionFactory redisConnectionFactory;
    private final WeatherCacheService cacheService;

    @Override
    public Mono<Health> health() {
        return checkRedisConnection()
                .flatMap(redisConnected -> {
                    Health.Builder status = redisConnected ?
                            Health.up() : Health.down();

                    return Mono.just(status
                            .withDetail("redis", redisConnected ? "connected" : "disconnected")
                            .withDetail("service", "weather-service")
                            .build());
                })
                .onErrorResume(error -> {
                    log.error("Health check failed", error);
                    return Mono.just(Health.down()
                            .withDetail("redis", "disconnected")
                            .withDetail("service", "weather-service")
                            .withDetail("error", error.getMessage())  // Добавляем информацию об ошибке
                            .build());
                });
    }

    private Mono<Boolean> checkRedisConnection() {
        return Mono.fromCallable(() -> redisConnectionFactory.getReactiveConnection())
                .flatMap(connection -> connection.ping()
                        .map("PONG"::equals)
                        .doFinally(signalType -> connection.close())
                );
        // Убрали .onErrorReturn(false) - теперь ошибки будут пробрасываться в health()
    }
}
