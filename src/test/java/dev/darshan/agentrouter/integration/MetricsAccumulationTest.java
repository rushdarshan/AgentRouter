package dev.darshan.agentrouter.integration;

import dev.darshan.agentrouter.core.AgentRouter;
import dev.darshan.agentrouter.core.StateGraph;
import dev.darshan.agentrouter.execution.ExecuteToolNode;
import dev.darshan.agentrouter.monitoring.MetricsCollector;
import dev.darshan.agentrouter.monitoring.ToolMetrics;
import dev.darshan.agentrouter.reporting.ErrorHandlerNode;
import dev.darshan.agentrouter.reporting.ReportResultNode;
import dev.darshan.agentrouter.routing.*;
import dev.darshan.agentrouter.tools.*;
import dev.darshan.agentrouter.validation.SchemaValidator;
import dev.darshan.agentrouter.validation.ValidateInputNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Metrics accumulation integration test — verifies metrics are collected across pipeline.
 */
class MetricsAccumulationTest {

    private AgentRouter router;
    private MetricsCollector metricsCollector;

    @BeforeEach
    void setUp() {
        metricsCollector = new MetricsCollector();
        ToolRegistry registry = new ToolRegistry();
        registry.register(new WeatherTool());
        registry.register(new CRMQueryTool());
        registry.register(new CalculatorTool());

        StateGraph graph = new StateGraph(
                new ResolveIntentNode(new KeywordClassifier(), registry),
                new ValidateInputNode(new SchemaValidator()),
                new ExecuteToolNode(new CircuitBreaker(metricsCollector), metricsCollector),
                new ReportResultNode(),
                new ErrorHandlerNode(metricsCollector));

        router = new AgentRouter(graph);
    }

    @Test
    @DisplayName("Metrics accumulate across multiple requests")
    void testMetricsAccumulation() {
        router.route("What's the weather in SF?");
        router.route("What's the weather in NYC?");
        router.route("get customer C001");

        ToolMetrics weatherMetrics = metricsCollector.getMetrics("WeatherTool");
        assertTrue(weatherMetrics.getCallCount() >= 2);
        assertTrue(weatherMetrics.getP50Latency() > 0);

        ToolMetrics crmMetrics = metricsCollector.getMetrics("CRMQueryTool");
        assertTrue(crmMetrics.getCallCount() >= 1);
    }

    @Test
    @DisplayName("Error metrics are tracked for failed requests")
    void testErrorMetricsTracked() {
        router.route("xyzabc gibberish");

        // The "unknown" tool should have an error recorded
        ToolMetrics unknownMetrics = metricsCollector.getMetrics("unknown");
        assertTrue(unknownMetrics.getErrorCount() > 0);
    }
}
