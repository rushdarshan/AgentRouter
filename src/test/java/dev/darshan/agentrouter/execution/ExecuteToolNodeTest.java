package dev.darshan.agentrouter.execution;

import dev.darshan.agentrouter.core.ExecutionContext;
import dev.darshan.agentrouter.monitoring.MetricsCollector;
import dev.darshan.agentrouter.routing.CircuitBreaker;
import dev.darshan.agentrouter.tools.Tool;
import dev.darshan.agentrouter.tools.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for ExecuteToolNode — execution + circuit breaker integration.
 */
@ExtendWith(MockitoExtension.class)
class ExecuteToolNodeTest {

    @Mock private Tool mockTool;
    private MetricsCollector metricsCollector;
    private ExecuteToolNode node;

    @BeforeEach
    void setUp() {
        metricsCollector = new MetricsCollector();
        CircuitBreaker circuitBreaker = new CircuitBreaker(metricsCollector);
        node = new ExecuteToolNode(circuitBreaker, metricsCollector);
    }

    @Test
    @DisplayName("Successful execution populates result")
    void testSuccessfulExecution() throws Exception {
        when(mockTool.getName()).thenReturn("TestTool");
        when(mockTool.execute(any())).thenReturn(
                ToolResult.success(Map.of("data", "value"), 10));

        ExecutionContext ctx = new ExecutionContext("test")
                .withSelectedTool(mockTool)
                .withToolInput(Map.of("key", "value"));

        ExecutionContext result = node.execute(ctx);

        assertFalse(result.hasError());
        assertTrue(result.hasResult());
        assertEquals("value", result.getResult().getData().get("data"));
    }

    @Test
    @DisplayName("Failed tool execution populates error")
    void testFailedExecution() throws Exception {
        when(mockTool.getName()).thenReturn("TestTool");
        when(mockTool.execute(any())).thenReturn(
                ToolResult.failure("Something went wrong", 10));

        ExecutionContext ctx = new ExecutionContext("test")
                .withSelectedTool(mockTool)
                .withToolInput(Map.of("key", "value"));

        ExecutionContext result = node.execute(ctx);

        assertTrue(result.hasError());
        assertTrue(result.getErrorMessage().contains("Something went wrong"));
    }

    @Test
    @DisplayName("Exception during execution populates error")
    void testExceptionDuringExecution() throws Exception {
        when(mockTool.getName()).thenReturn("TestTool");
        when(mockTool.execute(any())).thenThrow(new RuntimeException("Boom"));

        ExecutionContext ctx = new ExecutionContext("test")
                .withSelectedTool(mockTool)
                .withToolInput(Map.of("key", "value"));

        ExecutionContext result = node.execute(ctx);

        assertTrue(result.hasError());
    }

    @Test
    @DisplayName("Metrics are recorded after execution")
    void testMetricsRecorded() throws Exception {
        when(mockTool.getName()).thenReturn("TestTool");
        when(mockTool.execute(any())).thenReturn(
                ToolResult.success(Map.of("data", "value"), 10));

        ExecutionContext ctx = new ExecutionContext("test")
                .withSelectedTool(mockTool)
                .withToolInput(Map.of());

        node.execute(ctx);

        assertTrue(metricsCollector.getMetrics("TestTool").getCallCount() > 0);
    }
}
