package com.weatherservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherRequest {
    private String city;
    private String countryCode;
    private Double latitude;
    private Double longitude;
    private String units;
    private String language;
    private WeatherProvider provider;

    public static WeatherRequest copyWithProvider(WeatherRequest original, WeatherProvider provider) {
        return WeatherRequest.builder()
                .city(original.getCity())
                .countryCode(original.getCountryCode())
                .latitude(original.getLatitude())
                .longitude(original.getLongitude())
                .units(original.getUnits())
                .language(original.getLanguage())
                .provider(provider)
                .build();
    }
}
