package com.weatherservice.service;

import com.weatherservice.model.CurrentWeather;
import com.weatherservice.model.WeatherProvider;
import com.weatherservice.model.WeatherResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class WeatherDataTransformer {

    /**
     * Нормализация данных от разных провайдеров к единому формату
     */
    public WeatherResponse normalizeWeatherData(WeatherResponse response, WeatherProvider source) {
        try {
            CurrentWeather normalizedCurrent = normalizeCurrentWeather(response.getCurrent(), source);

            return WeatherResponse.builder()
                    .location(response.getLocation())
                    .current(normalizedCurrent)
                    .forecast(response.getForecast())
                    .source(source)
                    .cachedUntil(calculateCacheExpiry(source))
                    .build();

        } catch (Exception e) {
            log.error("Error normalizing weather data from provider: {}", source, e);
            throw new WeatherDataTransformationException("Failed to normalize weather data", e);
        }
    }

    /**
     * Нормализация текущей погоды
     */
    private CurrentWeather normalizeCurrentWeather(CurrentWeather current, WeatherProvider source) {
        if (current == null) {
            throw new WeatherDataTransformationException("Current weather data is null");
        }

        return CurrentWeather.builder()
                .temperature(normalizeTemperature(current.getTemperature(), source))
                .feelsLike(normalizeTemperature(current.getFeelsLike(), source))
                .humidity(current.getHumidity())
                .pressure(normalizePressure(current.getPressure(), source))
                .windSpeed(normalizeWindSpeed(current.getWindSpeed(), source))
                .windDirection(current.getWindDirection())
                .description(normalizeDescription(current.getDescription()))
                .icon(normalizeIcon(current.getIcon(), source))
                .timestamp(ensureTimestamp(current.getTimestamp()))
                .build();
    }

    /**
     * Нормализация температуры к Celsius
     */
    private Double normalizeTemperature(Double temperature, WeatherProvider source) {
        if (temperature == null) return null;
        return temperature; // Все провайдеры используют Celsius в наших настройках
    }

    /**
     * Нормализация скорости ветра к m/s
     */
    private Double normalizeWindSpeed(Double windSpeed, WeatherProvider source) {
        if (windSpeed == null) return null;

        switch (source) {
            case WEATHER_API:
                // WeatherAPI возвращает в km/h, конвертируем в m/s
                return windSpeed / 3.6;
            case ACCUWEATHER:
                // AccuWeather возвращает в km/h
                return windSpeed / 3.6;
            case OPENWEATHER_MAP:
            default:
                // OpenWeatherMap возвращает в m/s
                return windSpeed;
        }
    }

    /**
     * Нормализация давления к hPa
     */
    private Integer normalizePressure(Integer pressure, WeatherProvider source) {
        if (pressure == null) return null;
        return pressure; // Все провайдеры возвращают в hPa/mb
    }

    /**
     * Нормализация описания погоды
     */
    private String normalizeDescription(String description) {
        if (description == null) return "Unknown";
        return description.trim();
    }

    /**
     * Нормализация иконок
     */
    private String normalizeIcon(String icon, WeatherProvider source) {
        if (icon == null) return "01d"; // default icon
        return icon;
    }

    /**
     * Гарантия наличия timestamp
     */
    private LocalDateTime ensureTimestamp(LocalDateTime timestamp) {
        return timestamp != null ? timestamp : LocalDateTime.now();
    }

    /**
     * Расчет времени expiry кэша в зависимости от провайдера
     */
    private LocalDateTime calculateCacheExpiry(WeatherProvider source) {
        switch (source) {
            case OPENWEATHER_MAP:
                return LocalDateTime.now().plusMinutes(10);
            case WEATHER_API:
                return LocalDateTime.now().plusMinutes(30);
            case ACCUWEATHER:
                return LocalDateTime.now().plusMinutes(30);
            default:
                return LocalDateTime.now().plusMinutes(15);
        }
    }

    /**
     * Валидация преобразованных данных
     */
    public boolean isValidTransformedData(WeatherResponse response) {
        return response != null &&
                response.getCurrent() != null &&
                response.getCurrent().getTemperature() != null &&
                response.getCurrent().getTimestamp() != null &&
                response.getSource() != null;
    }

    public static class WeatherDataTransformationException extends RuntimeException {
        public WeatherDataTransformationException(String message) {
            super(message);
        }

        public WeatherDataTransformationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
