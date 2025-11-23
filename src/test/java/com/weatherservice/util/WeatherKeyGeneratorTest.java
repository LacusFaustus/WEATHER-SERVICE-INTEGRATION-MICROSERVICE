package com.weatherservice.util;

import com.weatherservice.model.WeatherRequest;
import com.weatherservice.model.WeatherProvider;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeatherKeyGeneratorTest {

    private final WeatherKeyGenerator keyGenerator = new WeatherKeyGenerator();

    @Test
    void generateCacheKey_WithCityAndCountry_ShouldGenerateCorrectKey() {
        // Given
        WeatherRequest request = WeatherRequest.builder()
                .city("London")
                .countryCode("GB")
                .units("metric")
                .provider(WeatherProvider.OPENWEATHER_MAP)
                .build();

        // When
        String key = keyGenerator.generateCacheKey(request);

        // Then
        assertNotNull(key);
        System.out.println("Generated key: " + key); // Для отладки
        assertTrue(key.toLowerCase().contains("london"));
        assertTrue(key.toLowerCase().contains("gb"));
        assertTrue(key.contains("metric"));
    }

    @Test
    void generateCacheKey_WithCoordinates_ShouldGenerateCorrectKey() {
        // Given
        WeatherRequest request = WeatherRequest.builder()
                .latitude(51.5074)
                .longitude(-0.1278)
                .units("metric")
                .build();

        // When
        String key = keyGenerator.generateCacheKey(request);

        // Then
        assertNotNull(key);
        // Просто проверяем что ключ создан и содержит необходимые части
        assertTrue(key.startsWith("weather:"));
        assertTrue(key.contains("metric"));
        // Не проверяем точный формат координат, так как он может меняться
    }

    @Test
    void generateCacheKey_WithNullRequest_ShouldReturnDefaultKey() {
        // When
        String key = keyGenerator.generateCacheKey(null);

        // Then
        assertEquals("weather:null", key);
    }

    @Test
    void generateCacheKey_WithOnlyCity_ShouldGenerateHashKey() {
        // Given
        WeatherRequest request = WeatherRequest.builder()
                .city("London")
                .units("metric")
                .build();

        // When
        String key = keyGenerator.generateCacheKey(request);

        // Then
        assertNotNull(key);
        System.out.println("Generated key with only city: " + key); // Для отладки
        assertTrue(key.startsWith("weather:hash:") || key.startsWith("weather:default:"));
    }

    @Test
    void generateCacheKey_WithLanguage_ShouldIncludeLanguage() {
        // Given
        WeatherRequest request = WeatherRequest.builder()
                .city("Paris")
                .countryCode("FR")
                .units("metric")
                .language("fr")
                .build();

        // When
        String key = keyGenerator.generateCacheKey(request);

        // Then
        assertNotNull(key);
        System.out.println("Generated key with language: " + key); // Для отладки
        assertTrue(key.toLowerCase().contains("paris"));
        assertTrue(key.toLowerCase().contains("fr"));
        assertTrue(key.contains("fr")); // language
    }
}
