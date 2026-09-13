package dev.darshan.agentrouter.monitoring;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-tool metrics snapshot.
 * Provides call count, latency percentiles, error tracking, and circuit state.
 */
public class ToolMetrics {

    private static final int WINDOW_SIZE = 1024;
    private final AtomicLong callCount = new AtomicLong();
    private final AtomicLong errorCount = new AtomicLong();
    private final AtomicLong rejectedCount = new AtomicLong();
    private volatile CircuitState circuitState;
    private final Deque<Long> latencies = new ArrayDeque<>();

    public ToolMetrics() {
        this.circuitState = CircuitState.CLOSED;
    }

    public void recordCall(long latencyMs) {
        recordCall(latencyMs, "SUCCESS");
    }

    public void recordCall(long latencyMs, String outcome) {
        recordAttempt(outcome, latencyMs);
    }

    /**
     * Records one genuine outbound attempt. A null duration means that no
     * timing sample exists; it is never converted into a fabricated zero.
     */
    public void recordAttempt(String outcome, Long latencyMs) {
        callCount.incrementAndGet();
        if (outcome != null && !"SUCCESS".equalsIgnoreCase(outcome)) {
            errorCount.incrementAndGet();
        }
        if (latencyMs != null && latencyMs >= 0) {
            synchronized (latencies) {
                if (latencies.size() == WINDOW_SIZE) latencies.removeFirst();
                latencies.addLast(latencyMs);
            }
        }
    }

    public void recordError() {
        errorCount.incrementAndGet();
    }

    /**
     * Pre-execution circuit rejection: never an attempt, so call/error counts
     * are untouched. Tracked separately for saturation forensics.
     */
    public void recordRejection() {
        rejectedCount.incrementAndGet();
    }

    public void setCircuitState(CircuitState state) {
        this.circuitState = state;
    }

    public long getCallCount() {
        return callCount.get();
    }

    public long getErrorCount() {
        return errorCount.get();
    }

    public long getRejectedCount() {
        return rejectedCount.get();
    }

    public double getErrorRate() {
        return callCount.get() == 0 ? 0.0 : (double) errorCount.get() / callCount.get();
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
        List<Long> sorted;
        synchronized (latencies) {
            if (latencies.isEmpty()) return 0.0;
            sorted = new ArrayList<>(latencies);
        }
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
        map.put("p99_latency_window", true);
        map.put("error_count", errorCount);
        map.put("rejected_count", rejectedCount);
        map.put("error_rate", Math.round(getErrorRate() * 1000.0) / 1000.0);
        map.put("circuit_state", circuitState.name());
        return map;
    }
}
