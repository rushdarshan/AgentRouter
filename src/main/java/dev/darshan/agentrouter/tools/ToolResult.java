package dev.darshan.agentrouter.tools;

import java.util.Map;

/**
 * Encapsulates the result of a tool execution.
 * Carries success/failure status, output data, and execution latency.
 */
public class ToolResult {

    private final boolean success;
    private final Map<String, Object> data;
    private final String errorMessage;
    private final long latencyMs;

    private ToolResult(boolean success, Map<String, Object> data,
                       String errorMessage, long latencyMs) {
        this.success = success;
        this.data = data;
        this.errorMessage = errorMessage;
        this.latencyMs = latencyMs;
    }

    /** Create a successful result with data. */
    public static ToolResult success(Map<String, Object> data, long latencyMs) {
        return new ToolResult(true, data, null, latencyMs);
    }

    /** Create a failed result with error message. */
    public static ToolResult failure(String errorMessage, long latencyMs) {
        return new ToolResult(false, null, errorMessage, latencyMs);
    }

    public boolean isSuccess() {
        return success;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public long getLatencyMs() {
        return latencyMs;
    }
}
