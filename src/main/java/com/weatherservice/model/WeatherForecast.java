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
public class WeatherForecast {
    private LocalDateTime date;
    private Double maxTemperature;
    private Double minTemperature;
    private Double avgTemperature;
    private Integer humidity;
    private Integer pressure;
    private Double windSpeed;
    private String description;
    private String icon;
    private Double precipitationProbability;
}
