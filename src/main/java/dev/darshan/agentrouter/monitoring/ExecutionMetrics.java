package dev.darshan.agentrouter.monitoring;

import java.time.Instant;
import java.util.*;

/**
 * Per-execution metrics captured during a single pipeline run.
 * Attached to ExecutionContext and included in HTTP responses.
 */
public class ExecutionMetrics {

    private String toolName;
    private long latencyMs;
    private String errorType;
    private Instant timestamp;

    public ExecutionMetrics() {
        this(Clock.system());
    }

    public ExecutionMetrics(Clock clock) {
        this.timestamp = clock.wallTime();
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    /** Convert to API-friendly map. */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (toolName != null) map.put("tool_name", toolName);
        if (latencyMs > 0) map.put("latency_ms", latencyMs);
        if (errorType != null) map.put("error_type", errorType);
        map.put("timestamp", timestamp.toString());
        return map;
    }
}
