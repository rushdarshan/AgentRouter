package dev.darshan.agentrouter.integration;

import dev.darshan.agentrouter.core.AgentRouter;
import dev.darshan.agentrouter.core.StateGraph;
import dev.darshan.agentrouter.execution.ExecuteToolNode;
import dev.darshan.agentrouter.monitoring.MetricsCollector;
import dev.darshan.agentrouter.reporting.ErrorHandlerNode;
import dev.darshan.agentrouter.reporting.ReportResultNode;
import dev.darshan.agentrouter.reporting.RouterResponse;
import dev.darshan.agentrouter.routing.*;
import dev.darshan.agentrouter.tools.*;
import dev.darshan.agentrouter.validation.SchemaValidator;
import dev.darshan.agentrouter.validation.ValidateInputNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test — full pipeline with real components.
 */
class EndToEndRouterTest {

    private AgentRouter router;

    @BeforeEach
    void setUp() {
        MetricsCollector metrics = new MetricsCollector();
        ToolRegistry registry = new ToolRegistry();
        registry.register(new WeatherTool());
        registry.register(new CRMQueryTool());
        registry.register(new CalculatorTool());

        KeywordClassifier classifier = new KeywordClassifier();
        CircuitBreaker circuitBreaker = new CircuitBreaker(metrics);
        SchemaValidator validator = new SchemaValidator();

        StateGraph graph = new StateGraph(
                new ResolveIntentNode(classifier, registry),
                new ValidateInputNode(validator),
                new ExecuteToolNode(circuitBreaker, metrics),
                new ReportResultNode(),
                new ErrorHandlerNode(metrics));

        router = new AgentRouter(graph);
    }

    @Test
    @DisplayName("Weather query routes correctly end-to-end")
    void testWeatherHappyPath() {
        RouterResponse response = router.route("What's the weather in SF?");

        assertTrue(response.isSuccess());
        assertEquals("weather_query", response.getIntent());
        assertEquals("WeatherTool", response.getTool());
        assertNotNull(response.getResult());
        assertEquals("San Francisco, CA", response.getResult().get("location"));
    }

    @Test
    @DisplayName("CRM query routes correctly end-to-end")
    void testCRMHappyPath() {
        RouterResponse response = router.route("get customer C001");

        assertTrue(response.isSuccess());
        assertEquals("crm_query", response.getIntent());
        assertEquals("CRMQueryTool", response.getTool());
        assertEquals("Acme Corp", response.getResult().get("name"));
    }

    @Test
    @DisplayName("Calculator query routes correctly end-to-end")
    void testCalculatorHappyPath() {
        RouterResponse response = router.route("calculate 5 + 3");

        assertTrue(response.isSuccess());
        assertEquals("calculator_query", response.getIntent());
        assertEquals("CalculatorTool", response.getTool());
        assertEquals(8.0, (double) response.getResult().get("result"), 0.001);
    }
}
