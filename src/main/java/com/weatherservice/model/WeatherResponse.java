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
public class WeatherResponse {
    private LocationInfo location;
    private CurrentWeather current;
    private List<WeatherForecast> forecast;
    private WeatherProvider source;
    private LocalDateTime cachedUntil;

}
