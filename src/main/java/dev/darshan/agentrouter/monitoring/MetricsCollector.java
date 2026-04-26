package dev.darshan.agentrouter.monitoring;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central observability collector for all tool executions.
 * Thread-safe — supports concurrent pipeline invocations.
 *
 * Records:
 * - Per-tool call latencies (for P50/P99 percentiles)
 * - Per-tool error counts and types
 * - Per-tool circuit breaker state transitions
 *
 * Consumed by:
 * - ReportResultNode (attach metrics to HTTP response)
 * - HealthController (expose on GET /health)
 * - CircuitBreaker (monitor degradation)
 */
@Component
public class MetricsCollector {

    private final Map<String, ToolMetrics> metricsStore = new ConcurrentHashMap<>();

    /**
     * Record a successful tool call with its latency.
     */
    public void recordLatency(String toolName, long latencyMs) {
        getOrCreate(toolName).recordCall(latencyMs);
    }

    /**
     * Record a tool execution error.
     */
    public void recordError(String toolName, String errorType) {
        ToolMetrics metrics = getOrCreate(toolName);
        metrics.recordCall(0);
        metrics.recordError();
    }

    /**
     * Record a circuit breaker state transition.
     */
    public void recordCircuitState(String toolName, CircuitState state) {
        getOrCreate(toolName).setCircuitState(state);
    }

    /**
     * Get metrics for a specific tool.
     */
    public ToolMetrics getMetrics(String toolName) {
        return metricsStore.getOrDefault(toolName, new ToolMetrics());
    }

    /**
     * Get metrics for all registered tools.
     */
    public Map<String, ToolMetrics> getAllMetrics() {
        return Collections.unmodifiableMap(metricsStore);
    }

    /**
     * Reset all metrics (for testing).
     */
    public void reset() {
        metricsStore.clear();
    }

    private ToolMetrics getOrCreate(String toolName) {
        return metricsStore.computeIfAbsent(toolName, k -> new ToolMetrics());
    }
}
