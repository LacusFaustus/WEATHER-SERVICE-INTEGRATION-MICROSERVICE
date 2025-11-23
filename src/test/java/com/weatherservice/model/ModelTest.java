package com.weatherservice.model;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ModelTest {

    @Test
    void weatherRequest_CopyWithProvider_ShouldCreateCopy() {
        // Given
        WeatherRequest original = WeatherRequest.builder()
                .city("London")
                .countryCode("GB")
                .units("metric")
                .language("en")
                .build();

        // When
        WeatherRequest copy = WeatherRequest.copyWithProvider(original, WeatherProvider.OPENWEATHER_MAP);

        // Then
        assertNotNull(copy);
        assertEquals("London", copy.getCity());
        assertEquals("GB", copy.getCountryCode());
        assertEquals("metric", copy.getUnits());
        assertEquals("en", copy.getLanguage());
        assertEquals(WeatherProvider.OPENWEATHER_MAP, copy.getProvider());
    }

    @Test
    void aggregatedWeatherResponse_GenerateRecommendation_ShouldReturnCorrectRecommendation() {
        // Test different temperature scenarios
        AggregatedWeatherResponse response1 = AggregatedWeatherResponse.builder()
                .temperature(35.0)
                .build();
        assertEquals("Hot day! Stay hydrated and avoid direct sun", response1.generateRecommendation());

        AggregatedWeatherResponse response2 = AggregatedWeatherResponse.builder()
                .temperature(25.0)
                .build();
        assertEquals("Pleasant weather! Great for outdoor activities", response2.generateRecommendation());

        AggregatedWeatherResponse response3 = AggregatedWeatherResponse.builder()
                .temperature(15.0)
                .build();
        assertEquals("Cool day! Wear a light jacket", response3.generateRecommendation());

        AggregatedWeatherResponse response4 = AggregatedWeatherResponse.builder()
                .temperature(5.0)
                .build();
        assertEquals("Cold! Dress warmly", response4.generateRecommendation());

        AggregatedWeatherResponse response5 = AggregatedWeatherResponse.builder()
                .temperature(-5.0)
                .build();
        assertEquals("Very cold! Wear heavy winter clothes", response5.generateRecommendation());

        AggregatedWeatherResponse response6 = AggregatedWeatherResponse.builder()
                .temperature(null)
                .build();
        assertEquals("Check weather conditions", response6.generateRecommendation());
    }

    @Test
    void currentWeather_Builder_ShouldCreateObject() {
        // Given & When
        CurrentWeather current = CurrentWeather.builder()
                .temperature(20.0)
                .feelsLike(19.0)
                .humidity(50)
                .pressure(1000)
                .windSpeed(5.0)
                .windDirection("N")
                .description("sunny")
                .icon("01d")
                .timestamp(LocalDateTime.now())
                .build();

        // Then
        assertNotNull(current);
        assertEquals(20.0, current.getTemperature());
        assertEquals(19.0, current.getFeelsLike());
        assertEquals(50, current.getHumidity());
        assertEquals(1000, current.getPressure());
        assertEquals(5.0, current.getWindSpeed());
        assertEquals("N", current.getWindDirection());
        assertEquals("sunny", current.getDescription());
        assertEquals("01d", current.getIcon());
        assertNotNull(current.getTimestamp());
    }

    @Test
    void locationInfo_Builder_ShouldCreateObject() {
        // Given & When
        LocationInfo location = LocationInfo.builder()
                .name("London")
                .country("GB")
                .lat(51.5074)
                .lon(-0.1278)
                .timezone("Europe/London")
                .build();

        // Then
        assertNotNull(location);
        assertEquals("London", location.getName());
        assertEquals("GB", location.getCountry());
        assertEquals(51.5074, location.getLat());
        assertEquals(-0.1278, location.getLon());
        assertEquals("Europe/London", location.getTimezone());
    }
}
