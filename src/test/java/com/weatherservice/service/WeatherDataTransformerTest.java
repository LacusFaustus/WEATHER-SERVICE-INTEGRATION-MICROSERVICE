package com.weatherservice.service;

import com.weatherservice.model.CurrentWeather;
import com.weatherservice.model.WeatherProvider;
import com.weatherservice.model.WeatherResponse;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class WeatherDataTransformerTest {

    private final WeatherDataTransformer transformer = new WeatherDataTransformer();

    @Test
    void normalizeWeatherData_WithValidData_ShouldReturnNormalizedResponse() {
        // Given
        CurrentWeather current = CurrentWeather.builder()
                .temperature(25.0)
                .feelsLike(26.0)
                .humidity(50)
                .pressure(1013)
                .windSpeed(10.0) // km/h for WeatherAPI
                .description(" sunny ") // with spaces
                .icon("test")
                .timestamp(null)
                .build();

        WeatherResponse response = WeatherResponse.builder()
                .current(current)
                .build();

        // When
        WeatherResponse result = transformer.normalizeWeatherData(response, WeatherProvider.WEATHER_API);

        // Then
        assertNotNull(result);
        assertEquals(25.0, result.getCurrent().getTemperature());
        assertEquals(26.0, result.getCurrent().getFeelsLike());
        assertEquals(50, result.getCurrent().getHumidity());
        assertEquals(1013, result.getCurrent().getPressure());
        assertEquals(10.0 / 3.6, result.getCurrent().getWindSpeed(), 0.01); // converted to m/s
        assertEquals("sunny", result.getCurrent().getDescription()); // trimmed
        assertEquals("test", result.getCurrent().getIcon());
        assertNotNull(result.getCurrent().getTimestamp());
        assertEquals(WeatherProvider.WEATHER_API, result.getSource());
        assertNotNull(result.getCachedUntil());
    }

    @Test
    void normalizeWeatherData_ForDifferentProviders_ShouldConvertWindSpeedCorrectly() {
        // Given
        CurrentWeather current = CurrentWeather.builder()
                .windSpeed(36.0) // km/h
                .timestamp(LocalDateTime.now())
                .build();

        WeatherResponse response = WeatherResponse.builder()
                .current(current)
                .build();

        // When & Then for OPENWEATHER_MAP
        WeatherResponse openWeatherResult = transformer.normalizeWeatherData(response, WeatherProvider.OPENWEATHER_MAP);
        assertEquals(36.0, openWeatherResult.getCurrent().getWindSpeed());

        // When & Then for WEATHER_API
        WeatherResponse weatherApiResult = transformer.normalizeWeatherData(response, WeatherProvider.WEATHER_API);
        assertEquals(10.0, weatherApiResult.getCurrent().getWindSpeed(), 0.01);

        // When & Then for ACCUWEATHER
        WeatherResponse accuWeatherResult = transformer.normalizeWeatherData(response, WeatherProvider.ACCUWEATHER);
        assertEquals(10.0, accuWeatherResult.getCurrent().getWindSpeed(), 0.01);
    }

    @Test
    void isValidTransformedData_WithValidData_ShouldReturnTrue() {
        // Given
        WeatherResponse response = WeatherResponse.builder()
                .current(CurrentWeather.builder()
                        .temperature(20.0)
                        .timestamp(LocalDateTime.now())
                        .build())
                .source(WeatherProvider.OPENWEATHER_MAP)
                .build();

        // When & Then
        assertTrue(transformer.isValidTransformedData(response));
    }

    @Test
    void isValidTransformedData_WithInvalidData_ShouldReturnFalse() {
        assertFalse(transformer.isValidTransformedData(null));
        assertFalse(transformer.isValidTransformedData(WeatherResponse.builder().build()));
        assertFalse(transformer.isValidTransformedData(WeatherResponse.builder()
                .current(CurrentWeather.builder().build())
                .build()));
    }

    @Test
    void normalizeWeatherData_WithNullCurrentWeather_ShouldThrowException() {
        // Given
        WeatherResponse response = WeatherResponse.builder()
                .current(null)
                .build();

        // When & Then
        assertThrows(WeatherDataTransformer.WeatherDataTransformationException.class,
                () -> transformer.normalizeWeatherData(response, WeatherProvider.OPENWEATHER_MAP));
    }

    @Test
    void normalizeWeatherData_ShouldHandleNullValuesGracefully() {
        // Given
        CurrentWeather current = CurrentWeather.builder()
                .temperature(null)
                .feelsLike(null)
                .humidity(null)
                .pressure(null)
                .windSpeed(null)
                .description(null)
                .icon(null)
                .timestamp(null)
                .build();

        WeatherResponse response = WeatherResponse.builder()
                .current(current)
                .build();

        // When
        WeatherResponse result = transformer.normalizeWeatherData(response, WeatherProvider.OPENWEATHER_MAP);

        // Then
        assertNotNull(result);
        assertNull(result.getCurrent().getTemperature());
        assertNull(result.getCurrent().getFeelsLike());
        assertNull(result.getCurrent().getHumidity());
        assertNull(result.getCurrent().getPressure());
        assertNull(result.getCurrent().getWindSpeed());
        assertEquals("Unknown", result.getCurrent().getDescription());
        assertEquals("01d", result.getCurrent().getIcon());
        assertNotNull(result.getCurrent().getTimestamp());
    }
}
