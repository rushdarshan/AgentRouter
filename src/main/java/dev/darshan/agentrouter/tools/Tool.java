package dev.darshan.agentrouter.tools;

import java.util.Map;

/**
 * Contract every tool must implement.
 * Tools are the atomic units of work in the AgentRouter pipeline.
 *
 * Each tool declares:
 * - A name (unique identifier)
 * - A description (human-readable purpose)
 * - A schema (input/output contract enforced before execution)
 * - A capability (domain grouping for routing)
 * - An execute method (the actual work)
 */
public interface Tool {

    /** Unique tool identifier (e.g., "WeatherTool"). */
    String getName();

    /** Human-readable description of what this tool does. */
    String getDescription();

    /** Input/output schema for validation. */
    ToolSchema getSchema();

    /** Domain capability for intent-based routing. */
    ToolCapability getCapability();

    /**
     * Execute the tool with validated parameters.
     *
     * @param params validated input parameters matching this tool's schema
     * @return result containing output data or error details
     */
    ToolResult execute(Map<String, Object> params);
}
