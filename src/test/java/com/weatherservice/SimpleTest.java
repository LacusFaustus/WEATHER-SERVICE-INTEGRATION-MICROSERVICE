package com.weatherservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SimpleTest {

    @Test
    void contextLoads() {
        assertTrue(true);
    }

    @Test
    void simpleAssertion() {
        assertTrue(1 + 1 == 2);
    }
}
