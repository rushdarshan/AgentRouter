package dev.darshan.agentrouter.tools;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Simulated CRM query tool returning customer data.
 * Mirrors Salesforce-style SOQL queries on Account/Contact objects.
 */
@Component
public class CRMQueryTool implements Tool {

    private static final Map<String, Map<String, Object>> CUSTOMER_DB = new HashMap<>();

    static {
        CUSTOMER_DB.put("C001", Map.of(
                "id", "C001", "name", "Acme Corp",
                "industry", "Technology", "revenue", 5_000_000,
                "contact", "John Smith", "email", "john@acme.com"));
        CUSTOMER_DB.put("C002", Map.of(
                "id", "C002", "name", "GlobalTech Inc",
                "industry", "Manufacturing", "revenue", 12_000_000,
                "contact", "Jane Doe", "email", "jane@globaltech.com"));
        CUSTOMER_DB.put("C003", Map.of(
                "id", "C003", "name", "StartupXYZ",
                "industry", "SaaS", "revenue", 800_000,
                "contact", "Alex Chen", "email", "alex@startupxyz.com"));
        CUSTOMER_DB.put("C004", Map.of(
                "id", "C004", "name", "Enterprise Solutions Ltd",
                "industry", "Consulting", "revenue", 25_000_000,
                "contact", "Sarah Wilson", "email", "sarah@enterprise.com"));
        CUSTOMER_DB.put("C005", Map.of(
                "id", "C005", "name", "DataFlow Systems",
                "industry", "Data Analytics", "revenue", 3_500_000,
                "contact", "Mike Johnson", "email", "mike@dataflow.com"));
    }

    @Override
    public String getName() {
        return "CRMQueryTool";
    }

    @Override
    public String getDescription() {
        return "Queries customer records from simulated CRM database (mirrors Salesforce SOQL)";
    }

    @Override
    public ToolSchema getSchema() {
        return new ToolSchema()
                .addInputField("customerId", FieldType.STRING, true)
                .addOutputField("id", FieldType.STRING, true)
                .addOutputField("name", FieldType.STRING, true)
                .addOutputField("industry", FieldType.STRING, true)
                .addOutputField("revenue", FieldType.NUMBER, true)
                .addOutputField("contact", FieldType.STRING, true)
                .addOutputField("email", FieldType.STRING, true);
    }

    @Override
    public ToolCapability getCapability() {
        return ToolCapability.CRM_QUERY;
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        long start = System.currentTimeMillis();
        String customerId = ((String) params.get("customerId")).toUpperCase();

        // Simulate database query latency (30-120ms)
        simulateLatency();

        Map<String, Object> customer = CUSTOMER_DB.get(customerId);
        long elapsed = System.currentTimeMillis() - start;

        if (customer == null) {
            return ToolResult.failure("Customer not found: " + customerId, elapsed);
        }

        return ToolResult.success(new HashMap<>(customer), elapsed);
    }

    private void simulateLatency() {
        try {
            Thread.sleep(30 + (long) (Math.random() * 90));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
