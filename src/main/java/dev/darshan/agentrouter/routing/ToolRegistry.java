package dev.darshan.agentrouter.routing;

import dev.darshan.agentrouter.tools.Tool;
import dev.darshan.agentrouter.tools.ToolCapability;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tool discovery and lookup registry.
 * Supports lookup by name, keyword (intent), and capability.
 *
 * Intent-to-capability mapping:
 * - "weather_query"    → WEATHER
 * - "crm_query"        → CRM_QUERY
 * - "calculator_query" → CALCULATOR
 */
@Component
public class ToolRegistry {

    private final Map<String, Tool> toolsByName = new ConcurrentHashMap<>();
    private final Map<ToolCapability, Tool> toolsByCapability = new ConcurrentHashMap<>();

    /** Intent-to-capability mapping for keyword-based routing. */
    private static final Map<String, ToolCapability> INTENT_CAPABILITY_MAP = Map.of(
            "weather_query", ToolCapability.WEATHER,
            "crm_query", ToolCapability.CRM_QUERY,
            "calculator_query", ToolCapability.CALCULATOR
    );

    /**
     * Register a tool in the registry.
     * Automatically indexes by name and capability.
     */
    public void register(Tool tool) {
        toolsByName.put(tool.getName(), tool);
        toolsByCapability.put(tool.getCapability(), tool);
    }

    /**
     * Find a tool by intent keyword (e.g., "weather_query" → WeatherTool).
     */
    public Optional<Tool> findByIntent(String intent) {
        ToolCapability capability = INTENT_CAPABILITY_MAP.get(intent);
        if (capability == null) return Optional.empty();
        return findByCapability(capability);
    }

    /**
     * Find a tool by its domain capability.
     */
    public Optional<Tool> findByCapability(ToolCapability capability) {
        return Optional.ofNullable(toolsByCapability.get(capability));
    }

    /**
     * Find a tool by its registered name.
     */
    public Optional<Tool> findByName(String name) {
        return Optional.ofNullable(toolsByName.get(name));
    }

    /**
     * List all registered tools.
     */
    public List<Tool> listAll() {
        return List.copyOf(toolsByName.values());
    }

    /**
     * Get count of registered tools.
     */
    public int size() {
        return toolsByName.size();
    }
}
