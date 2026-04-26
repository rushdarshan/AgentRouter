package dev.darshan.agentrouter.monitoring;

import java.time.Instant;
import java.util.*;

/**
 * Per-tool metrics snapshot.
 * Provides call count, latency percentiles, error tracking, and circuit state.
 */
public class ToolMetrics {

    private long callCount;
    private long errorCount;
    private CircuitState circuitState;
    private final List<Long> latencies;

    public ToolMetrics() {
        this.callCount = 0;
        this.errorCount = 0;
        this.circuitState = CircuitState.CLOSED;
        this.latencies = new ArrayList<>();
    }

    public void recordCall(long latencyMs) {
        callCount++;
        latencies.add(latencyMs);
    }

    public void recordError() {
        errorCount++;
    }

    public void setCircuitState(CircuitState state) {
        this.circuitState = state;
    }

    public long getCallCount() {
        return callCount;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public double getErrorRate() {
        return callCount == 0 ? 0.0 : (double) errorCount / callCount;
    }

    public CircuitState getCircuitState() {
        return circuitState;
    }

    /** P50 (median) latency in milliseconds. */
    public double getP50Latency() {
        return getPercentile(50);
    }

    /** P99 latency in milliseconds. */
    public double getP99Latency() {
        return getPercentile(99);
    }

    private double getPercentile(int percentile) {
        if (latencies.isEmpty()) return 0.0;

        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);

        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    /** Convert to API-friendly map. */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", circuitState == CircuitState.OPEN ? "degraded" : "healthy");
        map.put("call_count", callCount);
        map.put("p50_latency_ms", getP50Latency());
        map.put("p99_latency_ms", getP99Latency());
        map.put("error_count", errorCount);
        map.put("error_rate", Math.round(getErrorRate() * 1000.0) / 1000.0);
        map.put("circuit_state", circuitState.name());
        return map;
    }
}
