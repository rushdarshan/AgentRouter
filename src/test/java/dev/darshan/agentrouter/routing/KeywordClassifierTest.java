package dev.darshan.agentrouter.routing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for KeywordClassifier — intent classification.
 */
class KeywordClassifierTest {

    private final KeywordClassifier classifier = new KeywordClassifier();

    @Test
    @DisplayName("Classifies weather queries correctly")
    void testWeatherClassification() {
        assertEquals("weather_query", classifier.classify("What's the weather in SF?").orElse(null));
        assertEquals("weather_query", classifier.classify("forecast for NYC").orElse(null));
        assertEquals("weather_query", classifier.classify("temperature in LA").orElse(null));
    }

    @Test
    @DisplayName("Classifies CRM queries correctly")
    void testCRMClassification() {
        assertEquals("crm_query", classifier.classify("get customer C001").orElse(null));
        assertEquals("crm_query", classifier.classify("lookup account details").orElse(null));
        assertEquals("crm_query", classifier.classify("find contact information").orElse(null));
    }

    @Test
    @DisplayName("Classifies calculator queries correctly")
    void testCalculatorClassification() {
        assertEquals("calculator_query", classifier.classify("calculate 5 + 3").orElse(null));
        assertEquals("calculator_query", classifier.classify("compute the result").orElse(null));
        assertEquals("calculator_query", classifier.classify("what is 10 * 2").orElse(null));
    }

    @Test
    @DisplayName("Returns empty for unrecognized input")
    void testUnrecognizedInput() {
        Optional<String> result = classifier.classify("xyzabc random gibberish");
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Handles null and blank input gracefully")
    void testNullAndBlankInput() {
        assertFalse(classifier.classify(null).isPresent());
        assertFalse(classifier.classify("").isPresent());
        assertFalse(classifier.classify("   ").isPresent());
    }

    @Test
    @DisplayName("Classification is case-insensitive")
    void testCaseInsensitive() {
        assertEquals("weather_query", classifier.classify("WEATHER IN SF").orElse(null));
        assertEquals("crm_query", classifier.classify("GET CUSTOMER C001").orElse(null));
        assertEquals("calculator_query", classifier.classify("CALCULATE 5 + 3").orElse(null));
    }
}
