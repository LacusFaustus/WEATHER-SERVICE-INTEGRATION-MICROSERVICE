package com.weatherservice.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExceptionTest {

    @Test
    void weatherServiceException_Constructors() {
        // Test constructor with message
        WeatherServiceException exception1 = new WeatherServiceException("Test message");
        assertEquals("Test message", exception1.getMessage());
        assertNull(exception1.getCause());

        // Test constructor with message and cause
        Throwable cause = new RuntimeException("Root cause");
        WeatherServiceException exception2 = new WeatherServiceException("Test message", cause);
        assertEquals("Test message", exception2.getMessage());
        assertEquals(cause, exception2.getCause());
    }

    @Test
    void locationNotFoundException_Constructors() {
        LocationNotFoundException exception1 = new LocationNotFoundException("Location not found");
        assertEquals("Location not found", exception1.getMessage());

        Throwable cause = new RuntimeException("Cause");
        LocationNotFoundException exception2 = new LocationNotFoundException("Location not found", cause);
        assertEquals("Location not found", exception2.getMessage());
        assertEquals(cause, exception2.getCause());
    }

    @Test
    void serviceUnavailableException_Constructors() {
        ServiceUnavailableException exception1 = new ServiceUnavailableException("Service unavailable");
        assertEquals("Service unavailable", exception1.getMessage());

        Throwable cause = new RuntimeException("Cause");
        ServiceUnavailableException exception2 = new ServiceUnavailableException("Service unavailable", cause);
        assertEquals("Service unavailable", exception2.getMessage());
        assertEquals(cause, exception2.getCause());
    }

    @Test
    void rateLimitExceededException_Constructors() {
        RateLimitExceededException exception1 = new RateLimitExceededException("Rate limit exceeded");
        assertEquals("Rate limit exceeded", exception1.getMessage());

        Throwable cause = new RuntimeException("Cause");
        RateLimitExceededException exception2 = new RateLimitExceededException("Rate limit exceeded", cause);
        assertEquals("Rate limit exceeded", exception2.getMessage());
        assertEquals(cause, exception2.getCause());
    }
}
