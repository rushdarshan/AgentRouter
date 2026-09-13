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
        recordCall(toolName, latencyMs, "SUCCESS");
    }

    /**
     * Record a tool execution error.
     */
    public void recordError(String toolName, String errorType) {
        getOrCreate(toolName).recordError();
    }

    /** Record exactly one attempt, including its outcome and genuine duration. */
    public void recordCall(String toolName, long latencyMs, String outcome) {
        getOrCreate(toolName).recordCall(latencyMs, outcome);
    }

    /** Record one attempt with an optional genuine duration sample. */
    public void recordAttempt(String toolName, String outcome, Long latencyMs) {
        getOrCreate(toolName).recordAttempt(outcome, latencyMs);
    }

    /** Pre-execution circuit rejection: counted separately, never an attempt. */
    public void recordRejection(String toolName) {
        getOrCreate(toolName).recordRejection();
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
