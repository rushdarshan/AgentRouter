package dev.darshan.agentrouter.monitoring;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/** Deterministic clock for deadline, circuit and metrics tests. */
public final class ManualClock implements Clock {
    private final AtomicLong nanos;
    private volatile Instant wallTime;

    public ManualClock(Instant wallTime) {
        this(wallTime, 0L);
    }

    public ManualClock(Instant wallTime, long monotonicNanos) {
        this.wallTime = wallTime;
        this.nanos = new AtomicLong(monotonicNanos);
    }

    public void advanceMillis(long millis) {
        wallTime = wallTime.plusMillis(millis);
        nanos.addAndGet(millis * 1_000_000L);
    }

    @Override
    public Instant wallTime() {
        return wallTime;
    }

    @Override
    public long monotonicNanos() {
        return nanos.get();
    }
}
