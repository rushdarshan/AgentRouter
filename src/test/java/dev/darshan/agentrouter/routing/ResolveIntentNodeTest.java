package dev.darshan.agentrouter.routing;

import dev.darshan.agentrouter.core.ExecutionContext;
import dev.darshan.agentrouter.tools.Tool;
import dev.darshan.agentrouter.tools.ToolCapability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests for ResolveIntentNode — classification, tool selection, parameter extraction.
 */
@ExtendWith(MockitoExtension.class)
class ResolveIntentNodeTest {

    @Mock private IntentClassifier classifier;
    @Mock private ToolRegistry registry;
    @Mock private Tool mockTool;

    private ResolveIntentNode node;

    @BeforeEach
    void setUp() {
        node = new ResolveIntentNode(classifier, registry);
    }

    @Test
    @DisplayName("Successfully resolves intent, tool, and parameters")
    void testSuccessfulResolution() {
        when(classifier.classify(anyString())).thenReturn(Optional.of("weather_query"));
        when(registry.findByIntent("weather_query")).thenReturn(Optional.of(mockTool));
        when(mockTool.getName()).thenReturn("WeatherTool");

        ExecutionContext ctx = new ExecutionContext("weather in SF");
        ExecutionContext result = node.execute(ctx);

        assertFalse(result.hasError());
        assertEquals("weather_query", result.getIntent());
        assertEquals(mockTool, result.getSelectedTool());
    }

    @Test
    @DisplayName("Returns error when intent cannot be classified")
    void testClassificationFailure() {
        when(classifier.classify(anyString())).thenReturn(Optional.empty());

        ExecutionContext ctx = new ExecutionContext("xyzabc gibberish");
        ExecutionContext result = node.execute(ctx);

        assertTrue(result.hasError());
        assertTrue(result.getErrorMessage().contains("Could not classify"));
    }

    @Test
    @DisplayName("Returns error when no tool found for intent")
    void testToolNotFound() {
        when(classifier.classify(anyString())).thenReturn(Optional.of("unknown_intent"));
        when(registry.findByIntent("unknown_intent")).thenReturn(Optional.empty());

        ExecutionContext ctx = new ExecutionContext("something unknown");
        ExecutionContext result = node.execute(ctx);

        assertTrue(result.hasError());
        assertTrue(result.getErrorMessage().contains("No tool registered"));
    }

    @Test
    @DisplayName("Extracts weather parameters correctly")
    void testWeatherParameterExtraction() {
        var realClassifier = new KeywordClassifier();
        var realRegistry = new ToolRegistry();
        realRegistry.register(new dev.darshan.agentrouter.tools.WeatherTool());

        var realNode = new ResolveIntentNode(realClassifier, realRegistry);
        ExecutionContext ctx = new ExecutionContext("What's the weather in San Francisco?");
        ExecutionContext result = realNode.execute(ctx);

        assertFalse(result.hasError());
        assertEquals("SF", result.getToolInput().get("location"));
    }
}
