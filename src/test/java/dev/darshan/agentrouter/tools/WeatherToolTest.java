package dev.darshan.agentrouter.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WeatherTool execution.
 */
class WeatherToolTest {

    private final WeatherTool tool = new WeatherTool();

    @Test
    @DisplayName("Returns weather for known city")
    void testKnownCity() {
        ToolResult result = tool.execute(Map.of("location", "SF"));
        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals("San Francisco, CA", result.getData().get("location"));
        assertNotNull(result.getData().get("temp"));
        assertNotNull(result.getData().get("forecast"));
    }

    @Test
    @DisplayName("Returns failure for unknown city")
    void testUnknownCity() {
        ToolResult result = tool.execute(Map.of("location", "UNKNOWN"));
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Unknown location"));
    }

    @Test
    @DisplayName("Schema defines required location field")
    void testSchema() {
        ToolSchema schema = tool.getSchema();
        assertNotNull(schema.getInputFields().get("location"));
        assertTrue(schema.getInputFields().get("location").isRequired());
        assertEquals(FieldType.STRING, schema.getInputFields().get("location").getType());
    }

    @Test
    @DisplayName("Tool metadata is correct")
    void testMetadata() {
        assertEquals("WeatherTool", tool.getName());
        assertEquals(ToolCapability.WEATHER, tool.getCapability());
        assertNotNull(tool.getDescription());
    }
}
