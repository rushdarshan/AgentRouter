package dev.darshan.agentrouter.validation;

import dev.darshan.agentrouter.core.ExecutionContext;
import dev.darshan.agentrouter.tools.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ValidateInputNode — validation gate.
 */
class ValidateInputNodeTest {

    private ValidateInputNode node;

    @BeforeEach
    void setUp() {
        node = new ValidateInputNode(new SchemaValidator());
    }

    @Test
    @DisplayName("Valid input passes through without error")
    void testValidInput() {
        WeatherTool tool = new WeatherTool();
        ExecutionContext ctx = new ExecutionContext("weather in SF")
                .withSelectedTool(tool)
                .withToolInput(Map.of("location", "SF"));

        ExecutionContext result = node.execute(ctx);
        assertFalse(result.hasError());
    }

    @Test
    @DisplayName("Invalid input populates error")
    void testInvalidInput() {
        WeatherTool tool = new WeatherTool();
        ExecutionContext ctx = new ExecutionContext("weather in TOKYO")
                .withSelectedTool(tool)
                .withToolInput(Map.of("location", "TOKYO"));

        ExecutionContext result = node.execute(ctx);
        assertTrue(result.hasError());
    }

    @Test
    @DisplayName("Missing required field populates error")
    void testMissingField() {
        WeatherTool tool = new WeatherTool();
        ExecutionContext ctx = new ExecutionContext("weather")
                .withSelectedTool(tool)
                .withToolInput(Map.of());

        ExecutionContext result = node.execute(ctx);
        assertTrue(result.hasError());
    }
}
