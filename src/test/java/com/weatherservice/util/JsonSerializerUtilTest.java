package com.weatherservice.util;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class JsonSerializerUtilTest {

    @Test
    void toJson_WithObject_ShouldReturnJsonString() {
        // Given
        TestObject obj = new TestObject("test", 123);

        // When
        String json = JsonSerializerUtil.toJson(obj);

        // Then
        assertNotNull(json);
        assertTrue(json.contains("test"));
        assertTrue(json.contains("123"));
    }

    @Test
    void fromJson_WithValidJson_ShouldReturnObject() {
        // Given
        String json = "{\"name\":\"test\",\"value\":123}";

        // When
        TestObject obj = JsonSerializerUtil.fromJson(json, TestObject.class);

        // Then
        assertNotNull(obj);
        assertEquals("test", obj.getName());
        assertEquals(123, obj.getValue());
    }

    @Test
    void fromJsonToMap_ShouldReturnMap() {
        // Given
        String json = "{\"key1\":\"value1\",\"key2\":123}";

        // When
        Map<String, Object> map = JsonSerializerUtil.fromJsonToMap(json);

        // Then
        assertNotNull(map);
        assertEquals("value1", map.get("key1"));
        assertEquals(123, map.get("key2"));
    }

    @Test
    void fromJsonToList_ShouldReturnList() {
        // Given
        String json = "[\"item1\",\"item2\",\"item3\"]";

        // When
        List<String> list = JsonSerializerUtil.fromJsonToList(json, String.class);

        // Then
        assertNotNull(list);
        assertEquals(3, list.size());
        assertEquals("item1", list.get(0));
    }

    @Test
    void isValidJson_WithValidJson_ShouldReturnTrue() {
        // Given
        String validJson = "{\"name\":\"test\"}";

        // When & Then
        assertTrue(JsonSerializerUtil.isValidJson(validJson));
    }

    @Test
    void isValidJson_WithInvalidJson_ShouldReturnFalse() {
        // Given
        String invalidJson = "{invalid json}";

        // When & Then
        assertFalse(JsonSerializerUtil.isValidJson(invalidJson));
    }

    @Test
    void toPrettyJson_ShouldReturnFormattedJson() {
        // Given
        TestObject obj = new TestObject("test", 123);

        // When
        String prettyJson = JsonSerializerUtil.toPrettyJson(obj);

        // Then
        assertNotNull(prettyJson);
        assertTrue(prettyJson.contains("test"));
    }

    // Test class for serialization
    static class TestObject {
        private String name;
        private int value;

        public TestObject() {}

        public TestObject(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
    }
}
