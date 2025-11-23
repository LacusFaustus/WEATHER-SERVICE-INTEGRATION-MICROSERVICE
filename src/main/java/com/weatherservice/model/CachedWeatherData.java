package com.weatherservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CachedWeatherData {
    private String cacheKey;
    private WeatherResponse data;
    private LocalDateTime cachedAt;
    private LocalDateTime expiresAt;
    private Integer hitCount;
}
