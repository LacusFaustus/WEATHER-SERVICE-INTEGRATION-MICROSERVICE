package com.weatherservice.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
public class JsonSerializerUtil {

    private static final ObjectMapper objectMapper = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * Сериализация объекта в JSON строку
     */
    public static String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("Error serializing object to JSON", e);
            throw new JsonSerializationException("Failed to serialize object to JSON", e);
        }
    }

    /**
     * Десериализация JSON строки в объект
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (IOException e) {
            log.error("Error deserializing JSON to object: {}", json, e);
            throw new JsonSerializationException("Failed to deserialize JSON to object", e);
        }
    }

    /**
     * Десериализация JSON строки в типизированный объект
     */
    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (IOException e) {
            log.error("Error deserializing JSON to typed object: {}", json, e);
            throw new JsonSerializationException("Failed to deserialize JSON to typed object", e);
        }
    }

    /**
     * Десериализация JSON строки в Map
     */
    public static Map<String, Object> fromJsonToMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            log.error("Error deserializing JSON to map: {}", json, e);
            throw new JsonSerializationException("Failed to deserialize JSON to map", e);
        }
    }

    /**
     * Десериализация JSON строки в List
     */
    public static <T> List<T> fromJsonToList(String json, Class<T> elementClass) {
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, elementClass));
        } catch (IOException e) {
            log.error("Error deserializing JSON to list: {}", json, e);
            throw new JsonSerializationException("Failed to deserialize JSON to list", e);
        }
    }

    /**
     * Pretty print JSON
     */
    public static String toPrettyJson(Object object) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("Error creating pretty JSON", e);
            return toJson(object); // fallback to compact JSON
        }
    }

    /**
     * Валидация JSON строки
     */
    public static boolean isValidJson(String json) {
        try {
            objectMapper.readTree(json);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static class JsonSerializationException extends RuntimeException {
        public JsonSerializationException(String message) {
            super(message);
        }

        public JsonSerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
