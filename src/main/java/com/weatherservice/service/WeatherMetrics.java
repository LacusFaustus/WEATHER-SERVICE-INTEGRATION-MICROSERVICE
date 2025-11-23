package com.weatherservice.service;

import com.weatherservice.model.WeatherProvider;
import io.micrometer.core.instrument.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherMetrics {

    private final MeterRegistry meterRegistry;

    // Убираем отдельные поля счетчиков, будем создавать их динамически
    private final AtomicInteger cacheHitCount = new AtomicInteger(0);
    private final AtomicInteger cacheMissCount = new AtomicInteger(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    private final AtomicInteger requestCount = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        // Создаем gauges для мониторинга в реальном времени
        Gauge.builder("weather.cache.hit.rate", this, WeatherMetrics::getCacheHitRate)
                .description("Weather cache hit rate percentage")
                .tag("component", "weather-service")
                .register(meterRegistry);

        Gauge.builder("weather.cache.hits", cacheHitCount, AtomicInteger::get)
                .description("Weather cache hits count")
                .register(meterRegistry);

        Gauge.builder("weather.cache.misses", cacheMissCount, AtomicInteger::get)
                .description("Weather cache misses count")
                .register(meterRegistry);

        Gauge.builder("weather.requests.active", requestCount, AtomicInteger::get)
                .description("Active weather requests count")
                .register(meterRegistry);

        Gauge.builder("weather.response.time.avg", this, WeatherMetrics::getAverageResponseTime)
                .description("Average response time in milliseconds")
                .register(meterRegistry);
    }

    public void recordWeatherRequest(WeatherProvider provider, boolean success, Duration duration) {
        String providerTag = provider != null ? provider.name().toLowerCase() : "unknown";
        String status = success ? "success" : "error";

        // Создаем счетчики динамически при каждом вызове
        Counter.builder("weather.requests")
                .tag("provider", providerTag)
                .tag("status", status)
                .register(meterRegistry)
                .increment();

        // Записываем время ответа
        totalResponseTime.addAndGet(duration.toMillis());
        requestCount.incrementAndGet();
    }

    public void recordCacheHit() {
        cacheHitCount.incrementAndGet();
    }

    public void recordCacheMiss() {
        cacheMissCount.incrementAndGet();
    }

    public void recordCircuitBreakerFallback() {
        Counter.builder("weather.circuitbreaker.fallbacks")
                .register(meterRegistry)
                .increment();
    }

    public void recordRateLimitExceeded() {
        Counter.builder("weather.ratelimit.exceeded")
                .register(meterRegistry)
                .increment();
    }

    public void recordProviderResponseTime(WeatherProvider provider, Duration duration) {
        Timer.builder("weather.provider.response.time")
                .tag("provider", provider.name().toLowerCase())
                .register(meterRegistry)
                .record(duration);
    }

    public void recordAggregatedRequest(List<WeatherProvider> providers) {
        Counter.builder("weather.aggregated.requests")
                .tag("providers_count", String.valueOf(providers != null ? providers.size() : 0))
                .register(meterRegistry)
                .increment();
    }

    public double getCacheHitRate() {
        int hits = cacheHitCount.get();
        int misses = cacheMissCount.get();
        int total = hits + misses;

        return total > 0 ? (double) hits / total * 100 : 0.0;
    }

    public double getAverageResponseTime() {
        int count = requestCount.get();
        return count > 0 ? (double) totalResponseTime.get() / count : 0.0;
    }

    // Метод для сброса метрик (можно вызывать периодически)
    public void resetMetrics() {
        cacheHitCount.set(0);
        cacheMissCount.set(0);
        totalResponseTime.set(0);
        requestCount.set(0);
    }
}
