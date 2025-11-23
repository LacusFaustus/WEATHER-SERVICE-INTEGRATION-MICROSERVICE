package com.weatherservice.model;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ModelComprehensiveTest {

    @Test
    void weatherRequest_AllScenarios() {
        // Test builder pattern
        WeatherRequest request = WeatherRequest.builder()
                .city("TestCity")
                .countryCode("TC")
                .latitude(40.7128)
                .longitude(-74.0060)
                .units("metric")
                .language("en")
                .provider(WeatherProvider.OPENWEATHER_MAP)
                .build();

        assertNotNull(request);
        assertEquals("TestCity", request.getCity());
        assertEquals("TC", request.getCountryCode());
        assertEquals(40.7128, request.getLatitude());
        assertEquals(-74.0060, request.getLongitude());
        assertEquals("metric", request.getUnits());
        assertEquals("en", request.getLanguage());
        assertEquals(WeatherProvider.OPENWEATHER_MAP, request.getProvider());

        // Test toString
        assertNotNull(request.toString());

        // Test equals and hashCode
        WeatherRequest sameRequest = WeatherRequest.builder()
                .city("TestCity")
                .countryCode("TC")
                .build();
        WeatherRequest differentRequest = WeatherRequest.builder()
                .city("DifferentCity")
                .countryCode("DC")
                .build();

        assertEquals(request, request);
        assertNotEquals(request, differentRequest);
        assertNotEquals(request, null);
        assertNotEquals(request, "string");

        // Test copyWithProvider
        WeatherRequest copy = WeatherRequest.copyWithProvider(request, WeatherProvider.WEATHER_API);
        assertEquals("TestCity", copy.getCity());
        assertEquals(WeatherProvider.WEATHER_API, copy.getProvider());
    }

    @Test
    void weatherResponse_AllScenarios() {
        LocationInfo location = LocationInfo.builder()
                .name("TestCity")
                .country("TC")
                .lat(40.7128)
                .lon(-74.0060)
                .timezone("UTC")
                .build();

        CurrentWeather current = CurrentWeather.builder()
                .temperature(25.0)
                .feelsLike(26.0)
                .humidity(50)
                .pressure(1013)
                .windSpeed(5.0)
                .windDirection("N")
                .description("sunny")
                .icon("01d")
                .timestamp(LocalDateTime.now())
                .build();

        WeatherForecast forecast = WeatherForecast.builder()
                .date(LocalDateTime.now().plusDays(1))
                .maxTemperature(28.0)
                .minTemperature(22.0)
                .avgTemperature(25.0)
                .humidity(60)
                .pressure(1010)
                .windSpeed(4.5)
                .description("partly cloudy")
                .icon("02d")
                .precipitationProbability(30.0)
                .build();

        WeatherResponse response = WeatherResponse.builder()
                .location(location)
                .current(current)
                .forecast(Arrays.asList(forecast))
                .source(WeatherProvider.OPENWEATHER_MAP)
                .cachedUntil(LocalDateTime.now().plusHours(1))
                .build();

        assertNotNull(response);
        assertEquals(location, response.getLocation());
        assertEquals(current, response.getCurrent());
        assertEquals(1, response.getForecast().size());
        assertEquals(WeatherProvider.OPENWEATHER_MAP, response.getSource());
        assertNotNull(response.getCachedUntil());
    }

    @Test
    void aggregatedWeatherResponse_AllScenarios() {
        AggregatedWeatherResponse aggregated = AggregatedWeatherResponse.builder()
                .location(LocationInfo.builder().name("City").country("CC").build())
                .temperature(20.0)
                .humidity(65)
                .pressure(1013)
                .windSpeed(5.0)
                .description("cloudy")
                .providersUsed(Arrays.asList(WeatherProvider.OPENWEATHER_MAP, WeatherProvider.WEATHER_API))
                .sourcesCount(2)
                .timestamp(LocalDateTime.now())
                .build();

        // Генерируем рекомендацию перед проверками
        aggregated.setRecommendation(aggregated.generateRecommendation());

        assertNotNull(aggregated);
        assertEquals(20.0, aggregated.getTemperature());
        assertEquals(65, aggregated.getHumidity());
        assertEquals(1013, aggregated.getPressure());
        assertEquals(5.0, aggregated.getWindSpeed());
        assertEquals("cloudy", aggregated.getDescription());
        assertEquals(2, aggregated.getProvidersUsed().size());
        assertEquals(2, aggregated.getSourcesCount());
        assertNotNull(aggregated.getTimestamp());
        assertNotNull(aggregated.getRecommendation());

        // Test recommendation generation for different temperatures
        assertEquals("Hot day! Stay hydrated and avoid direct sun",
                AggregatedWeatherResponse.builder().temperature(35.0).build().generateRecommendation());
        assertEquals("Pleasant weather! Great for outdoor activities",
                AggregatedWeatherResponse.builder().temperature(25.0).build().generateRecommendation());
        assertEquals("Cool day! Wear a light jacket",
                AggregatedWeatherResponse.builder().temperature(15.0).build().generateRecommendation());
        assertEquals("Cold! Dress warmly",
                AggregatedWeatherResponse.builder().temperature(5.0).build().generateRecommendation());
        assertEquals("Very cold! Wear heavy winter clothes",
                AggregatedWeatherResponse.builder().temperature(-5.0).build().generateRecommendation());
        assertEquals("Check weather conditions",
                AggregatedWeatherResponse.builder().temperature(null).build().generateRecommendation());
    }

    @Test
    void locationSearchRequest_AllScenarios() {
        LocationSearchRequest request = LocationSearchRequest.builder()
                .query("London")
                .limit(10)
                .language("en")
                .build();

        assertNotNull(request);
        assertEquals("London", request.getQuery());
        assertEquals(10, request.getLimit());
        assertEquals("en", request.getLanguage());
    }

    @Test
    void cachedWeatherData_AllScenarios() {
        WeatherResponse data = WeatherResponse.builder()
                .location(LocationInfo.builder().name("Test").country("TC").build())
                .current(CurrentWeather.builder().temperature(20.0).build())
                .build();

        CachedWeatherData cached = CachedWeatherData.builder()
                .cacheKey("test:key")
                .data(data)
                .cachedAt(LocalDateTime.now().minusMinutes(5))
                .expiresAt(LocalDateTime.now().plusMinutes(25))
                .hitCount(5)
                .build();

        assertNotNull(cached);
        assertEquals("test:key", cached.getCacheKey());
        assertEquals(data, cached.getData());
        assertEquals(5, cached.getHitCount());
        assertNotNull(cached.getCachedAt());
        assertNotNull(cached.getExpiresAt());
    }

    @Test
    void weatherProvider_EnumValues() {
        assertEquals(3, WeatherProvider.values().length);
        assertTrue(Arrays.asList(WeatherProvider.values()).contains(WeatherProvider.OPENWEATHER_MAP));
        assertTrue(Arrays.asList(WeatherProvider.values()).contains(WeatherProvider.WEATHER_API));
        assertTrue(Arrays.asList(WeatherProvider.values()).contains(WeatherProvider.ACCUWEATHER));
    }
}
