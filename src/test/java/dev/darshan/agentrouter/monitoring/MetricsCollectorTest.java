package dev.darshan.agentrouter.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MetricsCollectorTest {

    private MetricsCollector collector;

    @BeforeEach
    void setUp() {
        collector = new MetricsCollector();
    }

    @Test
    @DisplayName("Records latency and increments call count")
    void testRecordLatency() {
        collector.recordLatency("TestTool", 50);
        collector.recordLatency("TestTool", 100);

        ToolMetrics metrics = collector.getMetrics("TestTool");
        assertEquals(2, metrics.getCallCount());
        assertTrue(metrics.getP50Latency() > 0);
    }

    @Test
    @DisplayName("Records errors and calculates error rate")
    void testRecordError() {
        collector.recordLatency("TestTool", 50);
        collector.recordError("TestTool", "RuntimeException");

        ToolMetrics metrics = collector.getMetrics("TestTool");
        assertEquals(1, metrics.getErrorCount());
        assertTrue(metrics.getErrorRate() > 0);
    }

    @Test
    @DisplayName("Records circuit state transitions")
    void testRecordCircuitState() {
        collector.recordCircuitState("TestTool", CircuitState.OPEN);

        ToolMetrics metrics = collector.getMetrics("TestTool");
        assertEquals(CircuitState.OPEN, metrics.getCircuitState());
    }

    @Test
    @DisplayName("getAllMetrics returns all tracked tools")
    void testGetAllMetrics() {
        collector.recordLatency("Tool1", 10);
        collector.recordLatency("Tool2", 20);

        Map<String, ToolMetrics> all = collector.getAllMetrics();
        assertEquals(2, all.size());
        assertTrue(all.containsKey("Tool1"));
        assertTrue(all.containsKey("Tool2"));
    }

    @Test
    @DisplayName("Reset clears all metrics")
    void testReset() {
        collector.recordLatency("TestTool", 50);
        collector.reset();

        assertTrue(collector.getAllMetrics().isEmpty());
    }
}
