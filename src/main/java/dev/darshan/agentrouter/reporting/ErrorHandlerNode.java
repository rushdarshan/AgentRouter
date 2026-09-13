package dev.darshan.agentrouter.reporting;

import dev.darshan.agentrouter.core.ExecutionContext;
import dev.darshan.agentrouter.monitoring.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Error Handler Node: Processes errors from any pipeline stage.
 *
 * Not part of the happy path — invoked when any node populates context.error.
 * Mirrors Salesforce Flow's error branches for observable, consistent error handling.
 *
 * Responsibilities:
 * 1. Capture context.error from failed node
 * 2. Format human-readable error message
 * 3. Record error in MetricsCollector
 * 4. Ensure context is ready for error response construction
 */
@Component
public class ErrorHandlerNode {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandlerNode.class);

    private final MetricsCollector metricsCollector;

    public ErrorHandlerNode(MetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    /**
     * Handle an error from any pipeline stage.
     */
    public ExecutionContext handle(ExecutionContext context) {
        String toolName = context.getSelectedTool() != null
                ? context.getSelectedTool().getName()
                : "unknown";

        String errorType = context.getError() != null
                ? context.getError().getClass().getSimpleName()
                : "UnknownError";

        log.error("ErrorHandler: {} for tool '{}' — {}",
                errorType, toolName, context.getErrorMessage());

        // Validation and routing failures have no tool attempt to count. Tool
        // execution paths already record exactly one attempt before branching.
        if (!context.isAttemptRecorded()) {
            metricsCollector.recordError(toolName, errorType);
        }
        context.getMetrics().setErrorType(errorType);

        if (context.getMetrics().getToolName() == null) {
            context.getMetrics().setToolName(toolName);
        }

        return context;
    }
}
