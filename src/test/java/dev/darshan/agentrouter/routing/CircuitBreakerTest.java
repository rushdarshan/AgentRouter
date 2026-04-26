package dev.darshan.agentrouter.routing;

import dev.darshan.agentrouter.monitoring.CircuitState;
import dev.darshan.agentrouter.monitoring.MetricsCollector;
import dev.darshan.agentrouter.tools.Tool;
import dev.darshan.agentrouter.tools.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for CircuitBreaker state transitions.
 */
class CircuitBreakerTest {

    private MetricsCollector metricsCollector;
    private CircuitBreaker circuitBreaker;
    private Tool mockTool;

    @BeforeEach
    void setUp() {
        metricsCollector = new MetricsCollector();
        // Low thresholds for easy testing: 3 failures to open, 2 successes to close, 1s timeout
        circuitBreaker = new CircuitBreaker(metricsCollector, 3, 2, 1);

        mockTool = mock(Tool.class);
        when(mockTool.getName()).thenReturn("TestTool");
    }

    @Test
    @DisplayName("CLOSED state passes requests through")
    void testClosedState() throws Exception {
        when(mockTool.execute(any())).thenReturn(
                ToolResult.success(Map.of("data", "value"), 10));

        ToolResult result = circuitBreaker.execute(mockTool, Map.of());
        assertTrue(result.isSuccess());
        assertEquals(CircuitState.CLOSED, circuitBreaker.getState("TestTool"));
    }

    @Test
    @DisplayName("CLOSED → OPEN after N consecutive failures")
    void testClosedToOpen() throws Exception {
        when(mockTool.execute(any())).thenReturn(
                ToolResult.failure("error", 10));

        // 3 failures should trip the circuit
        for (int i = 0; i < 3; i++) {
            circuitBreaker.execute(mockTool, Map.of());
        }

        assertEquals(CircuitState.OPEN, circuitBreaker.getState("TestTool"));
    }

    @Test
    @DisplayName("OPEN state rejects requests with exception")
    void testOpenRejects() throws Exception {
        // Trip the circuit first
        when(mockTool.execute(any())).thenReturn(ToolResult.failure("error", 10));
        for (int i = 0; i < 3; i++) {
            circuitBreaker.execute(mockTool, Map.of());
        }

        // Next call should throw
        assertThrows(CircuitBreaker.CircuitBreakerOpenException.class,
                () -> circuitBreaker.execute(mockTool, Map.of()));
    }

    @Test
    @DisplayName("OPEN → HALF_OPEN after timeout")
    void testOpenToHalfOpen() throws Exception {
        when(mockTool.execute(any())).thenReturn(ToolResult.failure("error", 10));
        for (int i = 0; i < 3; i++) {
            circuitBreaker.execute(mockTool, Map.of());
        }
        assertEquals(CircuitState.OPEN, circuitBreaker.getState("TestTool"));

        // Wait for timeout
        Thread.sleep(1100);

        // Next call should go through (HALF_OPEN)
        when(mockTool.execute(any())).thenReturn(ToolResult.success(Map.of(), 10));
        circuitBreaker.execute(mockTool, Map.of());

        // Should be in HALF_OPEN or transitioning to CLOSED
        CircuitState state = circuitBreaker.getState("TestTool");
        assertTrue(state == CircuitState.HALF_OPEN || state == CircuitState.CLOSED);
    }

    @Test
    @DisplayName("HALF_OPEN → CLOSED after M consecutive successes")
    void testHalfOpenToClosed() throws Exception {
        // Trip the circuit
        when(mockTool.execute(any())).thenReturn(ToolResult.failure("error", 10));
        for (int i = 0; i < 3; i++) {
            circuitBreaker.execute(mockTool, Map.of());
        }

        // Wait for timeout
        Thread.sleep(1100);

        // Succeed enough times to close
        when(mockTool.execute(any())).thenReturn(ToolResult.success(Map.of(), 10));
        circuitBreaker.execute(mockTool, Map.of()); // HALF_OPEN, success 1
        circuitBreaker.execute(mockTool, Map.of()); // HALF_OPEN, success 2 → CLOSED

        assertEquals(CircuitState.CLOSED, circuitBreaker.getState("TestTool"));
    }

    @Test
    @DisplayName("HALF_OPEN → OPEN on failure during recovery")
    void testHalfOpenBackToOpen() throws Exception {
        // Trip the circuit
        when(mockTool.execute(any())).thenReturn(ToolResult.failure("error", 10));
        for (int i = 0; i < 3; i++) {
            circuitBreaker.execute(mockTool, Map.of());
        }

        // Wait for timeout
        Thread.sleep(1100);

        // Fail during HALF_OPEN
        circuitBreaker.execute(mockTool, Map.of()); // still failing

        assertEquals(CircuitState.OPEN, circuitBreaker.getState("TestTool"));
    }

    @Test
    @DisplayName("Success resets failure counter in CLOSED state")
    void testSuccessResetsFailureCount() throws Exception {
        when(mockTool.execute(any())).thenReturn(ToolResult.failure("error", 10));
        circuitBreaker.execute(mockTool, Map.of()); // failure 1
        circuitBreaker.execute(mockTool, Map.of()); // failure 2

        // One success should reset
        when(mockTool.execute(any())).thenReturn(ToolResult.success(Map.of(), 10));
        circuitBreaker.execute(mockTool, Map.of());

        // Two more failures shouldn't trip (counter reset)
        when(mockTool.execute(any())).thenReturn(ToolResult.failure("error", 10));
        circuitBreaker.execute(mockTool, Map.of()); // failure 1
        circuitBreaker.execute(mockTool, Map.of()); // failure 2

        assertEquals(CircuitState.CLOSED, circuitBreaker.getState("TestTool"));
    }
}
