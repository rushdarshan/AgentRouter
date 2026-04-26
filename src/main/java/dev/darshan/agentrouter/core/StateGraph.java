package dev.darshan.agentrouter.core;

import dev.darshan.agentrouter.execution.ExecuteToolNode;
import dev.darshan.agentrouter.reporting.ErrorHandlerNode;
import dev.darshan.agentrouter.reporting.ReportResultNode;
import dev.darshan.agentrouter.routing.ResolveIntentNode;
import dev.darshan.agentrouter.validation.ValidateInputNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Pipeline orchestrator executing the deterministic 4+1 node graph.
 *
 * Inspired by LangGraph's StateGraph pattern (from HaloAudit reference):
 * each node is a pure function: ExecutionContext → ExecutionContext.
 *
 * <pre>
 *   ResolveIntent → ValidateInput → ExecuteTool → ReportResult
 *                                         ↓
 *                                    ErrorHandler (on any failure)
 * </pre>
 *
 * Error handling mirrors Salesforce Flow's error branches — not exception propagation.
 * On ANY error at ANY stage, execution branches to ErrorHandler.
 */
@Component
public class StateGraph {

    private static final Logger log = LoggerFactory.getLogger(StateGraph.class);

    private final ResolveIntentNode resolveIntent;
    private final ValidateInputNode validateInput;
    private final ExecuteToolNode executeTool;
    private final ReportResultNode reportResult;
    private final ErrorHandlerNode errorHandler;

    public StateGraph(ResolveIntentNode resolveIntent,
                      ValidateInputNode validateInput,
                      ExecuteToolNode executeTool,
                      ReportResultNode reportResult,
                      ErrorHandlerNode errorHandler) {
        this.resolveIntent = resolveIntent;
        this.validateInput = validateInput;
        this.executeTool = executeTool;
        this.reportResult = reportResult;
        this.errorHandler = errorHandler;
    }

    /**
     * Execute the full 4+1 pipeline for a user request.
     *
     * @param userRequest raw user input
     * @return completed execution context with result or error
     */
    public ExecutionContext execute(String userRequest) {
        log.info("═══ StateGraph: starting pipeline for '{}' ═══", userRequest);
        ExecutionContext context = new ExecutionContext(userRequest);

        try {
            // Node 1: Resolve Intent
            context = resolveIntent.execute(context);
            if (context.hasError()) {
                log.warn("Pipeline branching to ErrorHandler after ResolveIntent");
                return errorHandler.handle(context);
            }

            // Node 2: Validate Input
            context = validateInput.execute(context);
            if (context.hasError()) {
                log.warn("Pipeline branching to ErrorHandler after ValidateInput");
                return errorHandler.handle(context);
            }

            // Node 3: Execute Tool
            context = executeTool.execute(context);
            if (context.hasError()) {
                log.warn("Pipeline branching to ErrorHandler after ExecuteTool");
                return errorHandler.handle(context);
            }

            // Node 4: Report Result
            context = reportResult.execute(context);
            log.info("═══ StateGraph: pipeline completed successfully ═══");

        } catch (Exception e) {
            log.error("Pipeline caught unexpected exception: {}", e.getMessage(), e);
            context.withError(e);
            context = errorHandler.handle(context);
        }

        return context;
    }
}
