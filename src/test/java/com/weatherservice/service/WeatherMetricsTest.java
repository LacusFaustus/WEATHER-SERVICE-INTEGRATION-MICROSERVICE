package com.weatherservice.service;

import com.weatherservice.model.WeatherProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WeatherMetricsTest {

    private WeatherMetrics weatherMetrics;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        weatherMetrics = new WeatherMetrics(meterRegistry);
        weatherMetrics.init();
    }

    @Test
    void recordWeatherRequest_WithSuccess_ShouldIncrementCounters() {
        // When
        weatherMetrics.recordWeatherRequest(WeatherProvider.OPENWEATHER_MAP, true, Duration.ofMillis(100));

        // Then - проверяем общий счетчик запросов с тегами
        double count = meterRegistry.counter("weather.requests",
                "provider", "openweather_map", "status", "success").count();
        assertEquals(1.0, count, 0.01);
    }

    @Test
    void recordWeatherRequest_WithFailure_ShouldIncrementFailedCounter() {
        // When
        weatherMetrics.recordWeatherRequest(WeatherProvider.OPENWEATHER_MAP, false, Duration.ofMillis(100));

        // Then
        double count = meterRegistry.counter("weather.requests",
                "provider", "openweather_map", "status", "error").count();
        assertEquals(1.0, count, 0.01);
    }

    @Test
    void recordCacheHit_ShouldIncrementCounter() {
        // When
        weatherMetrics.recordCacheHit();
        weatherMetrics.recordCacheHit();

        // Then - проверяем что счетчик увеличился (hit rate должен быть > 0 после двух hits)
        double hitRate = weatherMetrics.getCacheHitRate();
        assertEquals(100.0, hitRate, 0.01); // 2 hits, 0 misses = 100%
    }

    @Test
    void recordCacheMiss_ShouldIncrementCounter() {
        // When
        weatherMetrics.recordCacheMiss();

        // Then - проверяем что счетчик увеличился
        double hitRate = weatherMetrics.getCacheHitRate();
        assertEquals(0.0, hitRate, 0.01); // 0 hits, 1 miss = 0%
    }

    @Test
    void getCacheHitRate_WithNoRequests_ShouldReturnZero() {
        // When & Then
        assertEquals(0.0, weatherMetrics.getCacheHitRate());
    }

    @Test
    void getCacheHitRate_WithHitsAndMisses_ShouldCalculateCorrectly() {
        // Given
        weatherMetrics.recordCacheHit();
        weatherMetrics.recordCacheHit();
        weatherMetrics.recordCacheMiss();

        // When
        double hitRate = weatherMetrics.getCacheHitRate();

        // Then - 2 hits / 3 total = 66.66%
        assertEquals(66.66, hitRate, 0.01);
    }

    @Test
    void getAverageResponseTime_WithNoRequests_ShouldReturnZero() {
        // When & Then
        assertEquals(0.0, weatherMetrics.getAverageResponseTime());
    }

    @Test
    void recordCircuitBreakerFallback_ShouldIncrementCounter() {
        // When
        weatherMetrics.recordCircuitBreakerFallback();

        // Then - проверяем что счетчик увеличился
        assertEquals(1.0, meterRegistry.counter("weather.circuitbreaker.fallbacks").count(), 0.01);
    }

    @Test
    void recordRateLimitExceeded_ShouldIncrementCounter() {
        // When
        weatherMetrics.recordRateLimitExceeded();

        // Then - проверяем что счетчик увеличился
        assertEquals(1.0, meterRegistry.counter("weather.ratelimit.exceeded").count(), 0.01);
    }

    @Test
    void recordProviderResponseTime_ShouldRecordTimer() {
        // When
        weatherMetrics.recordProviderResponseTime(WeatherProvider.OPENWEATHER_MAP, Duration.ofMillis(150));

        // Then - проверяем что таймер существует и имеет записи
        assertTrue(meterRegistry.find("weather.provider.response.time").timer().count() > 0);
    }

    @Test
    void recordAggregatedRequest_ShouldIncrementCounter() {
        // When
        weatherMetrics.recordAggregatedRequest(List.of(WeatherProvider.OPENWEATHER_MAP, WeatherProvider.WEATHER_API));

        // Then - проверяем что счетчик увеличился
        // Ищем счетчик с правильным тегом
        double count = meterRegistry.counter("weather.aggregated.requests",
                "providers_count", "2").count();
        assertEquals(1.0, count, 0.01);
    }

    @Test
    void recordAggregatedRequest_WithEmptyList_ShouldIncrementCounter() {
        // When
        weatherMetrics.recordAggregatedRequest(List.of());

        // Then
        double count = meterRegistry.counter("weather.aggregated.requests",
                "providers_count", "0").count();
        assertEquals(1.0, count, 0.01);
    }

    @Test
    void recordAggregatedRequest_WithNullList_ShouldIncrementCounter() {
        // When
        weatherMetrics.recordAggregatedRequest(null);

        // Then
        double count = meterRegistry.counter("weather.aggregated.requests",
                "providers_count", "0").count();
        assertEquals(1.0, count, 0.01);
    }

    @Test
    void resetMetrics_ShouldResetAllCounters() {
        // Given
        weatherMetrics.recordCacheHit();
        weatherMetrics.recordCacheMiss();
        weatherMetrics.recordWeatherRequest(WeatherProvider.OPENWEATHER_MAP, true, Duration.ofMillis(100));

        // When
        weatherMetrics.resetMetrics();

        // Then
        assertEquals(0.0, weatherMetrics.getCacheHitRate());
        assertEquals(0.0, weatherMetrics.getAverageResponseTime());
    }
}
