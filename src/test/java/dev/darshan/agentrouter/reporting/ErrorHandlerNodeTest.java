package dev.darshan.agentrouter.reporting;

import dev.darshan.agentrouter.core.ExecutionContext;
import dev.darshan.agentrouter.monitoring.MetricsCollector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ErrorHandlerNodeTest {

    private final MetricsCollector metricsCollector = new MetricsCollector();
    private final ErrorHandlerNode node = new ErrorHandlerNode(metricsCollector);

    @Test
    @DisplayName("Captures error and records metrics")
    void testErrorCapture() {
        ExecutionContext ctx = new ExecutionContext("bad request")
                .withError(new RuntimeException("Something broke"));

        ExecutionContext result = node.handle(ctx);

        assertTrue(result.hasError());
        assertNotNull(result.getMetrics().getErrorType());
    }

    @Test
    @DisplayName("Records error in MetricsCollector")
    void testMetricsRecorded() {
        ExecutionContext ctx = new ExecutionContext("bad request")
                .withError(new RuntimeException("fail"));

        node.handle(ctx);

        assertTrue(metricsCollector.getMetrics("unknown").getErrorCount() > 0);
    }
}
