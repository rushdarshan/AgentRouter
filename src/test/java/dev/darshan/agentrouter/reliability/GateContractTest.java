package dev.darshan.agentrouter.reliability;

import dev.darshan.agentrouter.monitoring.MetricsCollector;
import dev.darshan.agentrouter.monitoring.ToolMetrics;
import dev.darshan.agentrouter.routing.ToolRegistry;
import dev.darshan.agentrouter.tools.CalculatorTool;
import dev.darshan.agentrouter.tools.Tool;
import dev.darshan.agentrouter.tools.ToolCapability;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class GateContractTest {
    @Test
    void calculatorConsumesFullExpression() {
        var result = new CalculatorTool().execute(Map.of("expression", "2 + 3 * 4"));
        assertTrue(result.isSuccess());
        assertEquals(14.0, (double) result.getData().get("result"));
    }

    @Test
    void duplicateCapabilityRegistrationIsRejected() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new CalculatorTool());
        Tool duplicate = mock(Tool.class);
        org.mockito.Mockito.when(duplicate.getName()).thenReturn("Other");
        org.mockito.Mockito.when(duplicate.getCapability()).thenReturn(ToolCapability.CALCULATOR);
        assertThrows(IllegalArgumentException.class, () -> registry.register(duplicate));
    }

    @Test
    void concurrentMetricsAreExactAndP99UsesRealDurations() throws Exception {
        MetricsCollector metrics = new MetricsCollector();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 100; i++) {
            executor.submit(() -> metrics.recordCall("tool", 5, "SUCCESS"));
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        ToolMetrics snapshot = metrics.getMetrics("tool");
        assertEquals(100, snapshot.getCallCount());
        assertEquals(5.0, snapshot.getP99Latency());
    }
}
