package com.weatherservice.integration;

import com.weatherservice.model.WeatherRequest;
import com.weatherservice.model.WeatherResponse;
import com.weatherservice.model.CurrentWeather;
import com.weatherservice.model.LocationInfo;
import com.weatherservice.model.WeatherProvider;
import com.weatherservice.service.WeatherCacheService;
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
class WeatherCacheServiceIntegrationTest {

    @Mock
    private ReactiveRedisTemplate<String, WeatherResponse> redisTemplate;

    @Mock
    private WeatherKeyGenerator keyGenerator;

    @Mock
    private ReactiveValueOperations<String, WeatherResponse> valueOperations;

    private WeatherCacheService cacheService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cacheService = new WeatherCacheService(redisTemplate, keyGenerator);
    }

    @Test
    void cacheAndRetrieveWeatherData_ShouldWorkCorrectly() {
        // Given
        WeatherRequest request = createTestRequest();
        WeatherResponse response = createTestResponse();
        String cacheKey = "weather:default:testcity:tc:metric";

        when(keyGenerator.generateCacheKey(request)).thenReturn(cacheKey);
        when(valueOperations.set(eq(cacheKey), any(WeatherResponse.class), any(Duration.class)))
                .thenReturn(Mono.just(true));
        when(valueOperations.get(cacheKey)).thenReturn(Mono.just(response));

        // When - cache data
        StepVerifier.create(cacheService.cacheWeatherData(request, response))
                .expectNext(true)
                .verifyComplete();

        // Then - retrieve cached data
        StepVerifier.create(cacheService.getCachedWeather(request))
                .expectNextMatches(cached ->
                        cached.getLocation().getName().equals("TestCity") &&
                                cached.getCurrent().getTemperature().equals(20.0))
                .verifyComplete();

        verify(valueOperations).set(eq(cacheKey), any(WeatherResponse.class), any(Duration.class));
        verify(valueOperations).get(cacheKey);
    }

    @Test
    void evictWeatherData_ShouldRemoveFromCache() {
        // Given
        WeatherRequest request = createTestRequest();
        String cacheKey = "weather:default:testcity:tc:metric";

        when(keyGenerator.generateCacheKey(request)).thenReturn(cacheKey);
        when(redisTemplate.delete(cacheKey)).thenReturn(Mono.just(1L));

        // When - evict data
        StepVerifier.create(cacheService.evictWeatherData(request))
                .expectNext(true)
                .verifyComplete();

        // Then - verify deletion was called
        verify(redisTemplate).delete(cacheKey);
    }

    @Test
    void cacheWeatherData_WithCustomTtl_ShouldUseCorrectTtl() {
        // Given
        WeatherRequest request = createTestRequest();
        WeatherResponse response = createTestResponse();
        String cacheKey = "weather:default:testcity:tc:metric";
        Duration customTtl = Duration.ofMinutes(30);

        when(keyGenerator.generateCacheKey(request)).thenReturn(cacheKey);
        when(valueOperations.set(eq(cacheKey), any(WeatherResponse.class), eq(customTtl)))
                .thenReturn(Mono.just(true));

        // When - cache with custom TTL
        StepVerifier.create(cacheService.cacheWeatherData(request, response, customTtl))
                .expectNext(true)
                .verifyComplete();

        // Then - verify correct TTL was used
        verify(valueOperations).set(eq(cacheKey), any(WeatherResponse.class), eq(customTtl));
    }

    private WeatherRequest createTestRequest() {
        return WeatherRequest.builder()
                .city("TestCity")
                .countryCode("TC")
                .units("metric")
                .build();
    }

    private WeatherResponse createTestResponse() {
        return WeatherResponse.builder()
                .location(LocationInfo.builder()
                        .name("TestCity")
                        .country("TC")
                        .lat(0.0)
                        .lon(0.0)
                        .build())
                .current(CurrentWeather.builder()
                        .temperature(20.0)
                        .feelsLike(19.0)
                        .humidity(50)
                        .pressure(1000)
                        .windSpeed(5.0)
                        .description("sunny")
                        .timestamp(LocalDateTime.now())
                        .build())
                .source(WeatherProvider.OPENWEATHER_MAP)
                .cachedUntil(LocalDateTime.now().plusMinutes(10))
                .build();
    }
}
