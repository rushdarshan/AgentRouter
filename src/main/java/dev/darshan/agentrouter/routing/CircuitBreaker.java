package dev.darshan.agentrouter.routing;

import dev.darshan.agentrouter.monitoring.CircuitState;
import dev.darshan.agentrouter.monitoring.Clock;
import dev.darshan.agentrouter.monitoring.MetricsCollector;
import dev.darshan.agentrouter.tools.Tool;
import dev.darshan.agentrouter.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Atomic per-tool circuit breaker. State is reserved under a small lock, but
 * tool execution always happens outside that lock.
 */
@Component
public class CircuitBreaker {
    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    private final MetricsCollector metricsCollector;
    private final int failureThreshold;
    private final int successThreshold;
    private final long timeoutSeconds;
    private final Clock clock;
    private final Map<String, CircuitInfo> circuits = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public CircuitBreaker(MetricsCollector metricsCollector) {
        this(metricsCollector, 5, 1, 30, Clock.system());
    }

    public CircuitBreaker(MetricsCollector metricsCollector,
                          int failureThreshold, int successThreshold, long timeoutSeconds) {
        this(metricsCollector, failureThreshold, successThreshold, timeoutSeconds, Clock.system());
    }

    public CircuitBreaker(MetricsCollector metricsCollector,
                          int failureThreshold, int successThreshold, long timeoutSeconds,
                          Clock clock) {
        if (failureThreshold < 1 || successThreshold < 1 || timeoutSeconds < 0) {
            throw new IllegalArgumentException("Invalid circuit configuration");
        }
        this.metricsCollector = Objects.requireNonNull(metricsCollector, "metricsCollector");
        this.failureThreshold = failureThreshold;
        this.successThreshold = successThreshold;
        this.timeoutSeconds = timeoutSeconds;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ToolResult execute(Tool tool, Map<String, Object> params) throws Exception {
        String toolName = tool.getName();
        CircuitInfo info = circuits.computeIfAbsent(toolName, key -> new CircuitInfo());
        long generation;
        synchronized (info) {
            if (info.state == CircuitState.OPEN) {
                if (clock.monotonicNanos() - info.openedAtNanos < timeoutSeconds * 1_000_000_000L) {
                    throw open(toolName);
                }
                info.state = CircuitState.HALF_OPEN;
                info.generation++;
                info.halfOpenProbeInFlight = false;
                metricsCollector.recordCircuitState(toolName, CircuitState.HALF_OPEN);
            }
            if (info.state == CircuitState.HALF_OPEN) {
                if (info.halfOpenProbeInFlight) throw open(toolName);
                info.halfOpenProbeInFlight = true;
            }
            generation = info.generation;
        }

        try {
            ToolResult result = tool.execute(params);
            complete(toolName, info, generation, result != null && result.isSuccess());
            return result;
        } catch (Exception error) {
            complete(toolName, info, generation, false);
            throw error;
        }
    }

    public CircuitState getState(String toolName) {
        CircuitInfo info = circuits.get(toolName);
        if (info == null) return CircuitState.CLOSED;
        synchronized (info) {
            return info.state;
        }
    }

    public long getGeneration(String toolName) {
        CircuitInfo info = circuits.get(toolName);
        if (info == null) return 0L;
        synchronized (info) {
            return info.generation;
        }
    }

    private void complete(String toolName, CircuitInfo info, long generation, boolean success) {
        synchronized (info) {
            if (generation != info.generation) return; // stale completion
            if (info.state == CircuitState.HALF_OPEN) {
                info.halfOpenProbeInFlight = false;
                if (success) {
                    info.state = CircuitState.CLOSED;
                    info.consecutiveFailures = 0;
                    info.consecutiveSuccesses = 0;
                    metricsCollector.recordCircuitState(toolName, CircuitState.CLOSED);
                } else {
                    open(info);
                    metricsCollector.recordCircuitState(toolName, CircuitState.OPEN);
                }
                return;
            }
            if (success) {
                info.consecutiveFailures = 0;
            } else if (++info.consecutiveFailures >= failureThreshold) {
                open(info);
                metricsCollector.recordCircuitState(toolName, CircuitState.OPEN);
                log.warn("Circuit for {} opened after {} counted failures",
                        toolName, info.consecutiveFailures);
            }
        }
    }

    private void open(CircuitInfo info) {
        info.state = CircuitState.OPEN;
        info.openedAtNanos = clock.monotonicNanos();
        info.halfOpenProbeInFlight = false;
    }

    private CircuitBreakerOpenException open(String toolName) {
        return new CircuitBreakerOpenException(
                "Circuit breaker OPEN for " + toolName + ". Too many failures.");
    }

    static class CircuitInfo {
        CircuitState state = CircuitState.CLOSED;
        int consecutiveFailures;
        int consecutiveSuccesses;
        long openedAtNanos;
        long generation;
        boolean halfOpenProbeInFlight;
    }

    public static class CircuitBreakerOpenException extends Exception {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
}
