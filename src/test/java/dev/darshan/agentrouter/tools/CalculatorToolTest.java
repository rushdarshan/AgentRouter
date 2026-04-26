package dev.darshan.agentrouter.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CalculatorTool execution.
 */
class CalculatorToolTest {

    private final CalculatorTool tool = new CalculatorTool();

    @Test
    @DisplayName("Addition works correctly")
    void testAddition() {
        ToolResult result = tool.execute(Map.of("expression", "5 + 3"));
        assertTrue(result.isSuccess());
        assertEquals(8.0, (double) result.getData().get("result"), 0.001);
    }

    @Test
    @DisplayName("Subtraction works correctly")
    void testSubtraction() {
        ToolResult result = tool.execute(Map.of("expression", "10 - 4"));
        assertTrue(result.isSuccess());
        assertEquals(6.0, (double) result.getData().get("result"), 0.001);
    }

    @Test
    @DisplayName("Multiplication works correctly")
    void testMultiplication() {
        ToolResult result = tool.execute(Map.of("expression", "7 * 3"));
        assertTrue(result.isSuccess());
        assertEquals(21.0, (double) result.getData().get("result"), 0.001);
    }

    @Test
    @DisplayName("Division works correctly")
    void testDivision() {
        ToolResult result = tool.execute(Map.of("expression", "100 / 4"));
        assertTrue(result.isSuccess());
        assertEquals(25.0, (double) result.getData().get("result"), 0.001);
    }

    @Test
    @DisplayName("Division by zero returns error")
    void testDivisionByZero() {
        ToolResult result = tool.execute(Map.of("expression", "10 / 0"));
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Division by zero"));
    }

    @Test
    @DisplayName("Invalid expression returns error")
    void testInvalidExpression() {
        ToolResult result = tool.execute(Map.of("expression", "not a math expression"));
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }
}
