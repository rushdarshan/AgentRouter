package dev.darshan.agentrouter.reporting;

import dev.darshan.agentrouter.core.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Pipeline Node 4: ReportResult
 *
 * Formats the successful execution result into a RouterResponse.
 * Attaches execution metrics (latency, tool name, timestamp).
 */
@Component
public class ReportResultNode {

    private static final Logger log = LoggerFactory.getLogger(ReportResultNode.class);

    /**
     * Format successful result into response structure.
     * The actual HTTP response construction happens in RouterController.
     */
    public ExecutionContext execute(ExecutionContext context) {
        log.info("ReportResult: formatting response for tool '{}' — success={}",
                context.getSelectedTool() != null ? context.getSelectedTool().getName() : "unknown",
                context.hasResult());

        // Metrics are already populated by ExecuteToolNode
        // Context carries everything needed for response construction
        return context;
    }
}
