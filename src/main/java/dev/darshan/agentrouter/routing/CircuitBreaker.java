package dev.darshan.agentrouter.routing;

import dev.darshan.agentrouter.monitoring.CircuitState;
import dev.darshan.agentrouter.monitoring.MetricsCollector;
import dev.darshan.agentrouter.tools.Tool;
import dev.darshan.agentrouter.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Circuit Breaker implementation for fault-tolerant tool execution.
 *
 * State machine:
 * <pre>
 *   CLOSED  ──(N failures)──→  OPEN  ──(timeout)──→  HALF_OPEN
 *     ↑                                                  │
 *     └────────(M successes)─────────────────────────────┘
 *                                   │
 *                          (failure) → OPEN
 * </pre>
 *
 * Configuration:
 * - failureThreshold: consecutive failures to trip open (default: 5)
 * - successThreshold: consecutive successes to close from half-open (default: 3)
 * - timeoutSeconds:   seconds before OPEN transitions to HALF_OPEN (default: 30)
 */
@Component
public class CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    private final MetricsCollector metricsCollector;

    private final int failureThreshold;
    private final int successThreshold;
    private final long timeoutSeconds;

    /** Per-tool circuit state tracking. */
    private final Map<String, CircuitInfo> circuits = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public CircuitBreaker(MetricsCollector metricsCollector) {
        this(metricsCollector, 5, 3, 30);
    }

    public CircuitBreaker(MetricsCollector metricsCollector,
                          int failureThreshold, int successThreshold, long timeoutSeconds) {
        this.metricsCollector = metricsCollector;
        this.failureThreshold = failureThreshold;
        this.successThreshold = successThreshold;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Execute a tool through the circuit breaker.
     *
     * @param tool   the tool to execute
     * @param params validated input parameters
     * @return tool result
     * @throws CircuitBreakerOpenException if circuit is OPEN and timeout hasn't elapsed
     */
    public ToolResult execute(Tool tool, Map<String, Object> params) throws CircuitBreakerOpenException {
        String toolName = tool.getName();
        CircuitInfo info = circuits.computeIfAbsent(toolName, k -> new CircuitInfo());

        // Check circuit state
        switch (info.state) {
            case OPEN:
                if (hasTimeoutElapsed(info)) {
                    log.info("Circuit for {} transitioning OPEN → HALF_OPEN", toolName);
                    info.state = CircuitState.HALF_OPEN;
                    metricsCollector.recordCircuitState(toolName, CircuitState.HALF_OPEN);
                } else {
                    throw new CircuitBreakerOpenException(
                            "Circuit breaker OPEN for " + toolName + ". Too many failures.");
                }
                break;

            case HALF_OPEN:
                log.info("Circuit for {} is HALF_OPEN — allowing test request", toolName);
                break;

            case CLOSED:
            default:
                break;
        }

        // Execute the tool
        try {
            ToolResult result = tool.execute(params);

            if (result.isSuccess()) {
                onSuccess(toolName, info);
            } else {
                onFailure(toolName, info);
            }

            return result;

        } catch (Exception e) {
            onFailure(toolName, info);
            throw e;
        }
    }

    /**
     * Get the current circuit state for a tool.
     */
    public CircuitState getState(String toolName) {
        CircuitInfo info = circuits.get(toolName);
        return info != null ? info.state : CircuitState.CLOSED;
    }

    private void onSuccess(String toolName, CircuitInfo info) {
        if (info.state == CircuitState.HALF_OPEN) {
            info.consecutiveSuccesses++;
            if (info.consecutiveSuccesses >= successThreshold) {
                log.info("Circuit for {} transitioning HALF_OPEN → CLOSED", toolName);
                info.state = CircuitState.CLOSED;
                info.consecutiveFailures = 0;
                info.consecutiveSuccesses = 0;
                metricsCollector.recordCircuitState(toolName, CircuitState.CLOSED);
            }
        } else {
            info.consecutiveFailures = 0;
        }
    }

    private void onFailure(String toolName, CircuitInfo info) {
        info.consecutiveFailures++;
        info.consecutiveSuccesses = 0;

        if (info.state == CircuitState.HALF_OPEN) {
            log.warn("Circuit for {} transitioning HALF_OPEN → OPEN (test request failed)", toolName);
            info.state = CircuitState.OPEN;
            info.lastFailureTime = Instant.now();
            metricsCollector.recordCircuitState(toolName, CircuitState.OPEN);
        } else if (info.consecutiveFailures >= failureThreshold) {
            log.warn("Circuit for {} transitioning CLOSED → OPEN ({} consecutive failures)",
                    toolName, info.consecutiveFailures);
            info.state = CircuitState.OPEN;
            info.lastFailureTime = Instant.now();
            metricsCollector.recordCircuitState(toolName, CircuitState.OPEN);
        }
    }

    private boolean hasTimeoutElapsed(CircuitInfo info) {
        if (info.lastFailureTime == null) return true;
        return Instant.now().isAfter(info.lastFailureTime.plusSeconds(timeoutSeconds));
    }

    /** Mutable state holder for a single tool's circuit. */
    static class CircuitInfo {
        CircuitState state = CircuitState.CLOSED;
        int consecutiveFailures = 0;
        int consecutiveSuccesses = 0;
        Instant lastFailureTime = null;
    }

    /**
     * Exception thrown when circuit breaker is in OPEN state.
     */
    public static class CircuitBreakerOpenException extends Exception {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
}
