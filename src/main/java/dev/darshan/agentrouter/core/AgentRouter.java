package dev.darshan.agentrouter.core;

import dev.darshan.agentrouter.reporting.RouterResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Main entry point for the AgentRouter framework.
 * Thin wrapper around StateGraph that converts ExecutionContext to RouterResponse.
 */
@Component
public class AgentRouter {

    private static final Logger log = LoggerFactory.getLogger(AgentRouter.class);

    private final StateGraph stateGraph;

    public AgentRouter(StateGraph stateGraph) {
        this.stateGraph = stateGraph;
    }

    /**
     * Route a user request through the 4+1 pipeline and return a structured response.
     *
     * @param userRequest raw user input
     * @return structured response with result/error and metrics
     */
    public RouterResponse route(String userRequest) {
        log.info("AgentRouter: routing request '{}'", userRequest);

        ExecutionContext context = stateGraph.execute(userRequest);

        if (context.hasError()) {
            return RouterResponse.failure(
                    context.getErrorMessage(),
                    context.getMetrics().toMap());
        }

        return RouterResponse.success(
                context.getIntent(),
                context.getSelectedTool() != null ? context.getSelectedTool().getName() : null,
                context.getResult() != null ? context.getResult().getData() : null,
                context.getMetrics().toMap());
    }
}
