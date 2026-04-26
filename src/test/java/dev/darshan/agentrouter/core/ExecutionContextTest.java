package dev.darshan.agentrouter.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ExecutionContext — builder pattern and state management.
 */
class ExecutionContextTest {

    @Test
    @DisplayName("Builder pattern chains state transitions correctly")
    void testBuilderChaining() {
        ExecutionContext ctx = new ExecutionContext("test request")
                .withIntent("weather_query")
                .withToolInput(Map.of("location", "SF"));

        assertEquals("test request", ctx.getUserRequest());
        assertEquals("weather_query", ctx.getIntent());
        assertEquals("SF", ctx.getToolInput().get("location"));
        assertFalse(ctx.hasError());
        assertFalse(ctx.hasResult());
    }

    @Test
    @DisplayName("Error state is tracked correctly")
    void testErrorTracking() {
        ExecutionContext ctx = new ExecutionContext("bad request");
        assertFalse(ctx.hasError());

        ctx.withError(new RuntimeException("Something broke"));
        assertTrue(ctx.hasError());
        assertEquals("Something broke", ctx.getErrorMessage());
        assertNotNull(ctx.getError());
    }

    @Test
    @DisplayName("Request metadata is preserved")
    void testMetadata() {
        Map<String, Object> meta = Map.of("user_id", "user123");
        ExecutionContext ctx = new ExecutionContext("test", meta);

        assertEquals("user123", ctx.getRequestMetadata().get("user_id"));
    }

    @Test
    @DisplayName("Null metadata defaults to empty map")
    void testNullMetadata() {
        ExecutionContext ctx = new ExecutionContext("test", null);
        assertNotNull(ctx.getRequestMetadata());
        assertTrue(ctx.getRequestMetadata().isEmpty());
    }

    @Test
    @DisplayName("Metrics are initialized on creation")
    void testMetricsInitialized() {
        ExecutionContext ctx = new ExecutionContext("test");
        assertNotNull(ctx.getMetrics());
        assertNotNull(ctx.getMetrics().getTimestamp());
    }
}
