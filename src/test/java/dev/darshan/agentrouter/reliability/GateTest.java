package dev.darshan.agentrouter.reliability;

import dev.darshan.agentrouter.core.ExecutionContext;
import dev.darshan.agentrouter.core.StateGraph;
import dev.darshan.agentrouter.execution.ExecuteToolNode;
import dev.darshan.agentrouter.monitoring.MetricsCollector;
import dev.darshan.agentrouter.monitoring.ToolMetrics;
import dev.darshan.agentrouter.reporting.ErrorHandlerNode;
import dev.darshan.agentrouter.reporting.ReportResultNode;
import dev.darshan.agentrouter.routing.CircuitBreaker;
import dev.darshan.agentrouter.routing.KeywordClassifier;
import dev.darshan.agentrouter.routing.ResolveIntentNode;
import dev.darshan.agentrouter.routing.ToolRegistry;
import dev.darshan.agentrouter.tools.CalculatorTool;
import dev.darshan.agentrouter.tools.Tool;
import dev.darshan.agentrouter.tools.ToolResult;
import dev.darshan.agentrouter.validation.SchemaValidator;
import dev.darshan.agentrouter.validation.ValidateInputNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** sci-gate: full-expression legacy parse, exact accounting, window P99 without fabricated zeros. */
class GateTest {
    private static StateGraph legacyGraph(MetricsCollector metrics) {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new CalculatorTool());
        ResolveIntentNode resolve = new ResolveIntentNode(new KeywordClassifier(), registry);
        return new StateGraph(resolve, new ValidateInputNode(new SchemaValidator()),
                new ExecuteToolNode(new CircuitBreaker(metrics), metrics),
                new ReportResultNode(), new ErrorHandlerNode(metrics));
    }

    @Test
    void legacyRouteSubstring() {
        MetricsCollector metrics = new MetricsCollector();
        ExecutionContext full = legacyGraph(metrics).execute("calculate 2 + 3 * 4");
        assertFalse(full.hasError());
        assertEquals(14.0, (Double) full.getResult().getData().get("result"), 1e-9);

        ExecutionContext truncated = legacyGraph(new MetricsCollector()).execute("calculate 2 + 3 xyz");
        assertTrue(truncated.hasError());
        if (truncated.getErrorMessage() != null) {
            assertFalse(truncated.getErrorMessage().contains("xyz"));
        }
    }

    @Test
    void exactAccounting() throws Exception {
        MetricsCollector metrics = new MetricsCollector();
        ExecuteToolNode node = new ExecuteToolNode(new CircuitBreaker(metrics), metrics);
        Tool failing = mock(Tool.class);
        when(failing.getName()).thenReturn("AccountedTool");
        when(failing.execute(any())).thenReturn(ToolResult.failure("nope", 3));
        ExecutionContext failed = new ExecutionContext("test")
                .withSelectedTool(failing).withToolInput(Map.of());
        assertTrue(node.execute(failed).hasError());
        assertEquals(1, metrics.getMetrics("AccountedTool").getCallCount());
        assertEquals(1, metrics.getMetrics("AccountedTool").getErrorCount());

        Tool passing = mock(Tool.class);
        when(passing.getName()).thenReturn("CleanTool");
        when(passing.execute(any())).thenReturn(ToolResult.success(Map.of("v", 1), 3));
        ExecutionContext ok = new ExecutionContext("test")
                .withSelectedTool(passing).withToolInput(Map.of());
        assertFalse(node.execute(ok).hasError());
        assertEquals(1, metrics.getMetrics("CleanTool").getCallCount());
        assertEquals(0, metrics.getMetrics("CleanTool").getErrorCount());
    }

    @Test
    void windowP99NoFabricatedZero() {
        ToolMetrics window = new ToolMetrics();
        for (int i = 0; i < 50; i++) window.recordCall(0, "FAILURE");
        for (int i = 0; i < 100; i++) window.recordCall(5, "SUCCESS");
        assertEquals(5.0, window.getP99Latency(), 1e-9);
        window.recordCall(7, "FAILURE");
        assertTrue(window.getP99Latency() >= 5.0);
        assertEquals(Boolean.TRUE, window.toMap().get("p99_latency_window"));
    }
}
