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
public class CurrentWeather {
    private Double temperature;
    private Double feelsLike;
    private Integer humidity;
    private Integer pressure;
    private Double windSpeed;
    private String windDirection;
    private String description;
    private String icon;
    private LocalDateTime timestamp;
}
