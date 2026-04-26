package dev.darshan.agentrouter.tools;

import dev.darshan.agentrouter.routing.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ToolRegistry — registration, lookup, discovery.
 */
class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    @Test
    @DisplayName("Register and retrieve tool by name")
    void testRegisterAndFindByName() {
        WeatherTool tool = new WeatherTool();
        registry.register(tool);

        Optional<Tool> found = registry.findByName("WeatherTool");
        assertTrue(found.isPresent());
        assertEquals("WeatherTool", found.get().getName());
    }

    @Test
    @DisplayName("Find tool by capability")
    void testFindByCapability() {
        registry.register(new WeatherTool());
        registry.register(new CRMQueryTool());

        Optional<Tool> weather = registry.findByCapability(ToolCapability.WEATHER);
        assertTrue(weather.isPresent());
        assertEquals("WeatherTool", weather.get().getName());

        Optional<Tool> crm = registry.findByCapability(ToolCapability.CRM_QUERY);
        assertTrue(crm.isPresent());
        assertEquals("CRMQueryTool", crm.get().getName());
    }

    @Test
    @DisplayName("Find tool by intent keyword")
    void testFindByIntent() {
        registry.register(new WeatherTool());
        registry.register(new CalculatorTool());

        Optional<Tool> weather = registry.findByIntent("weather_query");
        assertTrue(weather.isPresent());
        assertEquals("WeatherTool", weather.get().getName());

        Optional<Tool> calc = registry.findByIntent("calculator_query");
        assertTrue(calc.isPresent());
        assertEquals("CalculatorTool", calc.get().getName());
    }

    @Test
    @DisplayName("Unknown intent returns empty")
    void testUnknownIntent() {
        registry.register(new WeatherTool());
        Optional<Tool> result = registry.findByIntent("unknown_intent");
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("List all registered tools")
    void testListAll() {
        registry.register(new WeatherTool());
        registry.register(new CRMQueryTool());
        registry.register(new CalculatorTool());

        assertEquals(3, registry.listAll().size());
        assertEquals(3, registry.size());
    }
}
