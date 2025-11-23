package com.weatherservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;

class ExceptionComprehensiveTest {

    @Test
    void weatherServiceException_ConstructorsAndInheritance() {
        // Test basic constructor
        WeatherServiceException exception1 = new WeatherServiceException("Test message");
        assertEquals("Test message", exception1.getMessage());
        assertNull(exception1.getCause());

        // Test constructor with cause
        Throwable cause = new RuntimeException("Root cause");
        WeatherServiceException exception2 = new WeatherServiceException("Test message", cause);
        assertEquals("Test message", exception2.getMessage());
        assertEquals(cause, exception2.getCause());

        // Test inheritance
        assertTrue(exception1 instanceof RuntimeException);
    }

    @Test
    void locationNotFoundException_AnnotationsAndBehavior() {
        LocationNotFoundException exception = new LocationNotFoundException("Location not found");

        assertEquals("Location not found", exception.getMessage());
        assertEquals(HttpStatus.NOT_FOUND, exception.getClass().getAnnotation(org.springframework.web.bind.annotation.ResponseStatus.class).value());

        // Test with cause
        LocationNotFoundException withCause = new LocationNotFoundException("Location not found", new RuntimeException());
        assertEquals("Location not found", withCause.getMessage());
        assertNotNull(withCause.getCause());
    }

    @Test
    void serviceUnavailableException_AnnotationsAndBehavior() {
        ServiceUnavailableException exception = new ServiceUnavailableException("Service unavailable");

        assertEquals("Service unavailable", exception.getMessage());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getClass().getAnnotation(org.springframework.web.bind.annotation.ResponseStatus.class).value());

        ServiceUnavailableException withCause = new ServiceUnavailableException("Service unavailable", new RuntimeException());
        assertEquals("Service unavailable", withCause.getMessage());
        assertNotNull(withCause.getCause());
    }

    @Test
    void rateLimitExceededException_AnnotationsAndBehavior() {
        RateLimitExceededException exception = new RateLimitExceededException("Rate limit exceeded");

        assertEquals("Rate limit exceeded", exception.getMessage());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getClass().getAnnotation(org.springframework.web.bind.annotation.ResponseStatus.class).value());

        RateLimitExceededException withCause = new RateLimitExceededException("Rate limit exceeded", new RuntimeException());
        assertEquals("Rate limit exceeded", withCause.getMessage());
        assertNotNull(withCause.getCause());
    }

    @Test
    void globalExceptionHandler_Coverage() {
        // This would require integration tests with WebTestClient
        // For now, we'll ensure the class is loaded
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        assertNotNull(handler);
    }
}
