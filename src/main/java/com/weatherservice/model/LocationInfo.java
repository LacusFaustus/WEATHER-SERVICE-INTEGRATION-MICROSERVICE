package com.weatherservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationInfo {
    private String name;
    private String country;
    private Double lat;
    private Double lon;
    private String timezone;
}
