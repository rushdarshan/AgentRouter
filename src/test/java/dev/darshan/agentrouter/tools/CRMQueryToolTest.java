package dev.darshan.agentrouter.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CRMQueryTool execution.
 */
class CRMQueryToolTest {

    private final CRMQueryTool tool = new CRMQueryTool();

    @Test
    @DisplayName("Returns customer data for known ID")
    void testKnownCustomer() {
        ToolResult result = tool.execute(Map.of("customerId", "C001"));
        assertTrue(result.isSuccess());
        assertEquals("Acme Corp", result.getData().get("name"));
        assertEquals("Technology", result.getData().get("industry"));
    }

    @Test
    @DisplayName("Returns failure for unknown customer ID")
    void testUnknownCustomer() {
        ToolResult result = tool.execute(Map.of("customerId", "C999"));
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Customer not found"));
    }

    @Test
    @DisplayName("Schema defines required customerId field")
    void testSchema() {
        ToolSchema schema = tool.getSchema();
        assertNotNull(schema.getInputFields().get("customerId"));
        assertTrue(schema.getInputFields().get("customerId").isRequired());
    }

    @Test
    @DisplayName("Tool metadata is correct")
    void testMetadata() {
        assertEquals("CRMQueryTool", tool.getName());
        assertEquals(ToolCapability.CRM_QUERY, tool.getCapability());
    }
}
