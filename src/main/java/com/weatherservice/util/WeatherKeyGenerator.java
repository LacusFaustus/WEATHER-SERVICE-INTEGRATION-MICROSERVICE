package com.weatherservice.util;

import com.weatherservice.model.WeatherRequest;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;

@Component
public class WeatherKeyGenerator {

    public String generateCacheKey(WeatherRequest request) {
        if (request == null) {
            return "weather:null";
        }

        StringBuilder keyBuilder = new StringBuilder("weather:");

        if (request.getProvider() != null) {
            keyBuilder.append(request.getProvider().name().toLowerCase()).append(":");
        } else {
            keyBuilder.append("default:");
        }

        if (request.getCity() != null && request.getCountryCode() != null) {
            keyBuilder.append(request.getCity().toLowerCase())
                    .append(":")
                    .append(request.getCountryCode().toLowerCase());
        } else if (request.getLatitude() != null && request.getLongitude() != null) {
            keyBuilder.append(String.format("%.4f:%.4f",
                    request.getLatitude(), request.getLongitude()));
        } else {
            // Fallback - использовать хэш от всех полей
            return generateHashKey(request);
        }

        if (request.getUnits() != null) {
            keyBuilder.append(":").append(request.getUnits());
        }

        if (request.getLanguage() != null) {
            keyBuilder.append(":").append(request.getLanguage());
        }

        return keyBuilder.toString();
    }

    private String generateHashKey(WeatherRequest request) {
        try {
            String data = Objects.toString(request.getCity(), "") +
                    Objects.toString(request.getCountryCode(), "") +
                    Objects.toString(request.getLatitude(), "") +
                    Objects.toString(request.getLongitude(), "") +
                    Objects.toString(request.getUnits(), "") +
                    Objects.toString(request.getLanguage(), "");

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return "weather:hash:" + Base64.getEncoder().encodeToString(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return "weather:fallback:" + System.currentTimeMillis();
        }
    }

    // Метод для совместимости с Spring Cache KeyGenerator (опционально)
    public Object generate(Object target, java.lang.reflect.Method method, Object... params) {
        if (params.length > 0 && params[0] instanceof WeatherRequest) {
            return generateCacheKey((WeatherRequest) params[0]);
        }
        // Fallback для других типов параметров
        return method.getName() + ":" + java.util.Arrays.deepHashCode(params);
    }
}
