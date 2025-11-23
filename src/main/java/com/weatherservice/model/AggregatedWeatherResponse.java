package com.weatherservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggregatedWeatherResponse {
    private LocationInfo location;
    private Double temperature;
    private Integer humidity;
    private Integer pressure;
    private Double windSpeed;
    private String description;
    private List<WeatherProvider> providersUsed;
    private Integer sourcesCount;
    private LocalDateTime timestamp;
    private String recommendation;

    // Метод для генерации рекомендаций на основе погоды
    public String generateRecommendation() {
        if (temperature == null) return "Check weather conditions";

        if (temperature > 30) {
            return "Hot day! Stay hydrated and avoid direct sun";
        } else if (temperature > 20) {
            return "Pleasant weather! Great for outdoor activities";
        } else if (temperature > 10) {
            return "Cool day! Wear a light jacket";
        } else if (temperature > 0) {
            return "Cold! Dress warmly";
        } else {
            return "Very cold! Wear heavy winter clothes";
        }
    }
}
