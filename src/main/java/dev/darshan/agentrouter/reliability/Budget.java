package dev.darshan.agentrouter.reliability;

import dev.darshan.agentrouter.monitoring.Clock;

import java.time.Duration;

public final class Budget {
    private final Clock clock;
    private final long deadlineNanos;

    public Budget(Clock clock, Duration duration) {
        if (duration == null || duration.isNegative()) throw new IllegalArgumentException("duration");
        this.clock = clock;
        this.deadlineNanos = clock.monotonicNanos() + duration.toNanos();
    }

    public boolean expired() {
        return clock.monotonicNanos() >= deadlineNanos;
    }

    public Duration remaining() {
        return Duration.ofNanos(Math.max(0L, deadlineNanos - clock.monotonicNanos()));
    }
}
