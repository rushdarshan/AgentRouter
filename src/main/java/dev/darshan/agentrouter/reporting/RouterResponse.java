package dev.darshan.agentrouter.reporting;

import java.util.Map;

/**
 * Unified HTTP response DTO for all router operations.
 * Used by both success and error paths.
 */
public class RouterResponse {

    private boolean success;
    private String intent;
    private String tool;
    private Map<String, Object> result;
    private String error;
    private Map<String, Object> metrics;

    // --- Builder pattern ---

    public static RouterResponse success(String intent, String tool,
                                          Map<String, Object> result,
                                          Map<String, Object> metrics) {
        RouterResponse response = new RouterResponse();
        response.success = true;
        response.intent = intent;
        response.tool = tool;
        response.result = result;
        response.metrics = metrics;
        return response;
    }

    public static RouterResponse failure(String error, Map<String, Object> metrics) {
        RouterResponse response = new RouterResponse();
        response.success = false;
        response.error = error;
        response.metrics = metrics;
        return response;
    }

    // --- Accessors (Jackson needs these) ---

    public boolean isSuccess() { return success; }
    public String getIntent() { return intent; }
    public String getTool() { return tool; }
    public Map<String, Object> getResult() { return result; }
    public String getError() { return error; }
    public Map<String, Object> getMetrics() { return metrics; }
}
