package dev.darshan.agentrouter.execution;

import dev.darshan.agentrouter.core.ExecutionContext;
import dev.darshan.agentrouter.monitoring.MetricsCollector;
import dev.darshan.agentrouter.monitoring.Clock;
import dev.darshan.agentrouter.routing.CircuitBreaker;
import dev.darshan.agentrouter.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Pipeline Node 3: ExecuteTool
 *
 * Wraps tool execution in CircuitBreaker for fault tolerance.
 * Records latency and errors in MetricsCollector.
 *
 * On success: populates context.result
 * On failure: populates context.error
 */
@Component
public class ExecuteToolNode {

    private static final Logger log = LoggerFactory.getLogger(ExecuteToolNode.class);

    private final CircuitBreaker circuitBreaker;
    private final MetricsCollector metricsCollector;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public ExecuteToolNode(CircuitBreaker circuitBreaker, MetricsCollector metricsCollector) {
        this(circuitBreaker, metricsCollector, Clock.system());
    }

    public ExecuteToolNode(CircuitBreaker circuitBreaker, MetricsCollector metricsCollector, Clock clock) {
        this.circuitBreaker = circuitBreaker;
        this.metricsCollector = metricsCollector;
        this.clock = clock;
    }

    /**
     * Execute the selected tool through the circuit breaker.
     */
    public ExecutionContext execute(ExecutionContext context) {
        String toolName = context.getSelectedTool().getName();
        log.info("ExecuteTool: executing '{}' with params {}", toolName, context.getToolInput());

        long startTime = clock.monotonicNanos();

        try {
            ToolResult result = circuitBreaker.execute(
                    context.getSelectedTool(), context.getToolInput());

            long elapsed = clock.elapsedMillis(startTime);
            context.getMetrics().setLatencyMs(elapsed);

            if (result.isSuccess()) {
                metricsCollector.recordAttempt(toolName, "SUCCESS", elapsed);
                context.markAttemptRecorded();
                log.info("ExecuteTool: '{}' completed successfully in {}ms", toolName, elapsed);
                return context.withResult(result);
            } else {
                log.warn("ExecuteTool: '{}' returned failure: {}", toolName, result.getErrorMessage());
                metricsCollector.recordAttempt(toolName, "FAILURE", elapsed);
                context.markAttemptRecorded();
                return context.withError(
                        new ToolExecutionException("Tool execution failed: " + result.getErrorMessage()));
            }

        } catch (CircuitBreaker.CircuitBreakerOpenException e) {
            long elapsed = clock.elapsedMillis(startTime);
            log.error("ExecuteTool: circuit breaker OPEN for '{}'", toolName);
            // Rejection is not an attempt: no call/error recorded, separate counter.
            metricsCollector.recordRejection(toolName);
            context.markAttemptRecorded();
            context.getMetrics().setLatencyMs(elapsed);
            context.getMetrics().setErrorType("CircuitBreakerOpen");
            return context.withError(e);

        } catch (Exception e) {
            long elapsed = clock.elapsedMillis(startTime);
            log.error("ExecuteTool: unexpected error executing '{}': {}", toolName, e.getMessage());
            metricsCollector.recordAttempt(toolName, "FAILURE", elapsed);
            context.markAttemptRecorded();
            context.getMetrics().setLatencyMs(elapsed);
            context.getMetrics().setErrorType(e.getClass().getSimpleName());
            return context.withError(new ToolExecutionException(
                    "Tool execution failed: " + e.getMessage(), e));
        }
    }
}
