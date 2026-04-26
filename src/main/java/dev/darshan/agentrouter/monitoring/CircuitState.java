package dev.darshan.agentrouter.monitoring;

/**
 * Circuit breaker states for fault tolerance.
 */
public enum CircuitState {
    /** Normal operation — requests pass through. */
    CLOSED,
    /** Failing — requests are rejected immediately. */
    OPEN,
    /** Recovering — one test request allowed to check recovery. */
    HALF_OPEN
}
