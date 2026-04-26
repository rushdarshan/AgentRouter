package dev.darshan.agentrouter.core;

import dev.darshan.agentrouter.monitoring.ExecutionMetrics;
import dev.darshan.agentrouter.tools.Tool;
import dev.darshan.agentrouter.tools.ToolResult;

import java.util.Collections;
import java.util.Map;

/**
 * Immutable-ish state container carrying a request through the 4+1 node pipeline.
 *
 * Uses a builder-style "withX()" pattern for state transitions between nodes.
 * Each node receives a context and returns a new/mutated context.
 *
 * Lifecycle:
 * <pre>
 *   new ExecutionContext(userRequest)
 *     → withIntent(intent)              [ResolveIntentNode]
 *     → withSelectedTool(tool)          [ResolveIntentNode]
 *     → withToolInput(params)           [ResolveIntentNode]
 *     → withResult(result)              [ExecuteToolNode]
 *     → withError(exception)            [any node on failure]
 * </pre>
 */
public class ExecutionContext {

    // --- Input (immutable) ---
    private final String userRequest;
    private final Map<String, Object> requestMetadata;

    // --- After ResolveIntent ---
    private String intent;
    private Tool selectedTool;
    private Map<String, Object> toolInput;

    // --- After ExecuteTool ---
    private ToolResult result;

    // --- Error tracking ---
    private Exception error;
    private String errorMessage;

    // --- Metrics ---
    private ExecutionMetrics metrics;

    public ExecutionContext(String userRequest) {
        this(userRequest, Collections.emptyMap());
    }

    public ExecutionContext(String userRequest, Map<String, Object> requestMetadata) {
        this.userRequest = userRequest;
        this.requestMetadata = requestMetadata != null ? requestMetadata : Collections.emptyMap();
        this.metrics = new ExecutionMetrics();
    }

    // --- Builder methods for state transitions ---

    public ExecutionContext withIntent(String intent) {
        this.intent = intent;
        return this;
    }

    public ExecutionContext withSelectedTool(Tool tool) {
        this.selectedTool = tool;
        return this;
    }

    public ExecutionContext withToolInput(Map<String, Object> toolInput) {
        this.toolInput = toolInput;
        return this;
    }

    public ExecutionContext withResult(ToolResult result) {
        this.result = result;
        return this;
    }

    public ExecutionContext withError(Exception error) {
        this.error = error;
        this.errorMessage = error.getMessage();
        return this;
    }

    public ExecutionContext withErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    // --- State checks ---

    public boolean hasError() {
        return error != null || errorMessage != null;
    }

    public boolean hasResult() {
        return result != null;
    }

    // --- Accessors ---

    public String getUserRequest() {
        return userRequest;
    }

    public Map<String, Object> getRequestMetadata() {
        return requestMetadata;
    }

    public String getIntent() {
        return intent;
    }

    public Tool getSelectedTool() {
        return selectedTool;
    }

    public Map<String, Object> getToolInput() {
        return toolInput;
    }

    public ToolResult getResult() {
        return result;
    }

    public Exception getError() {
        return error;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public ExecutionMetrics getMetrics() {
        return metrics;
    }

    public void setMetrics(ExecutionMetrics metrics) {
        this.metrics = metrics;
    }
}
