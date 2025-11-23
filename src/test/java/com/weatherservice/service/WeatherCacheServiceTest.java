package com.weatherservice.service;

import com.weatherservice.model.WeatherRequest;
import com.weatherservice.model.WeatherResponse;
import com.weatherservice.model.CurrentWeather;
import com.weatherservice.model.LocationInfo;
import com.weatherservice.model.WeatherProvider;
import com.weatherservice.util.WeatherKeyGenerator;
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
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeatherCacheServiceTest {

    @Mock
    private ReactiveRedisTemplate<String, WeatherResponse> redisTemplate;

    @Mock
    private WeatherKeyGenerator keyGenerator;

    @Mock
    private ReactiveValueOperations<String, WeatherResponse> valueOperations;

    private WeatherCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new WeatherCacheService(redisTemplate, keyGenerator);
    }

    @Test
    void getCachedWeather_WhenKeyExists_ShouldReturnData() {
        // Given
        WeatherRequest request = createTestRequest();
        WeatherResponse response = createTestResponse();
        String cacheKey = "test:key";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(keyGenerator.generateCacheKey(request)).thenReturn(cacheKey);
        when(valueOperations.get(cacheKey)).thenReturn(Mono.just(response));

        // When
        Mono<WeatherResponse> result = cacheService.getCachedWeather(request);

        // Then
        StepVerifier.create(result)
                .expectNext(response)
                .verifyComplete();

        verify(valueOperations).get(cacheKey);
    }

    @Test
    void getCachedWeather_WhenKeyNotExists_ShouldReturnEmpty() {
        // Given
        WeatherRequest request = createTestRequest();
        String cacheKey = "test:key";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(keyGenerator.generateCacheKey(request)).thenReturn(cacheKey);
        when(valueOperations.get(cacheKey)).thenReturn(Mono.empty());

        // When
        Mono<WeatherResponse> result = cacheService.getCachedWeather(request);

        // Then
        StepVerifier.create(result)
                .verifyComplete();

        verify(valueOperations).get(cacheKey);
    }

    @Test
    void cacheWeatherData_ShouldStoreDataSuccessfully() {
        // Given
        WeatherRequest request = createTestRequest();
        WeatherResponse response = createTestResponse();
        String cacheKey = "test:key";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(keyGenerator.generateCacheKey(request)).thenReturn(cacheKey);
        when(valueOperations.set(eq(cacheKey), any(WeatherResponse.class), any(Duration.class)))
                .thenReturn(Mono.just(true));

        // When
        Mono<Boolean> result = cacheService.cacheWeatherData(request, response);

        // Then
        StepVerifier.create(result)
                .expectNext(true)
                .verifyComplete();

        verify(valueOperations).set(eq(cacheKey), any(WeatherResponse.class), any(Duration.class));
    }

    @Test
    void evictWeatherData_ShouldDeleteKey() {
        // Given
        WeatherRequest request = createTestRequest();
        String cacheKey = "test:key";

        when(keyGenerator.generateCacheKey(request)).thenReturn(cacheKey);
        when(redisTemplate.delete(cacheKey)).thenReturn(Mono.just(1L));

        // When
        Mono<Boolean> result = cacheService.evictWeatherData(request);

        // Then
        StepVerifier.create(result)
                .expectNext(true)
                .verifyComplete();

        verify(redisTemplate).delete(cacheKey);
    }

    @Test
    void cacheWeatherData_WithCustomTtl_ShouldStoreDataSuccessfully() {
        // Given
        WeatherRequest request = createTestRequest();
        WeatherResponse response = createTestResponse();
        String cacheKey = "test:key";
        Duration customTtl = Duration.ofMinutes(30);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(keyGenerator.generateCacheKey(request)).thenReturn(cacheKey);
        when(valueOperations.set(eq(cacheKey), any(WeatherResponse.class), eq(customTtl)))
                .thenReturn(Mono.just(true));

        // When
        Mono<Boolean> result = cacheService.cacheWeatherData(request, response, customTtl);

        // Then
        StepVerifier.create(result)
                .expectNext(true)
                .verifyComplete();

        verify(valueOperations).set(eq(cacheKey), any(WeatherResponse.class), eq(customTtl));
    }

    private WeatherRequest createTestRequest() {
        return WeatherRequest.builder()
                .city("London")
                .countryCode("GB")
                .units("metric")
                .build();
    }

    private WeatherResponse createTestResponse() {
        return WeatherResponse.builder()
                .location(LocationInfo.builder()
                        .name("London")
                        .country("GB")
                        .lat(51.5074)
                        .lon(-0.1278)
                        .build())
                .current(CurrentWeather.builder()
                        .temperature(15.5)
                        .feelsLike(14.8)
                        .humidity(65)
                        .pressure(1013)
                        .windSpeed(3.6)
                        .description("cloudy")
                        .timestamp(LocalDateTime.now())
                        .build())
                .source(WeatherProvider.OPENWEATHER_MAP)
                .cachedUntil(LocalDateTime.now().plusMinutes(10))
                .build();
    }
}
