package dev.darshan.agentrouter.monitoring;

import java.time.Instant;

/**
 * Single time source for elapsed-time decisions. Wall time is retained only
 * for audit timestamps; callers must use monotonicNanos for deadlines,
 * cooldowns, backoff and latency measurements.
 */
public interface Clock {
    Instant wallTime();

    long monotonicNanos();

    default long elapsedMillis(long startNanos) {
        return Math.max(0L, (monotonicNanos() - startNanos) / 1_000_000L);
    }

    static Clock system() {
        return SystemClock.INSTANCE;
    }

    final class SystemClock implements Clock {
        private static final SystemClock INSTANCE = new SystemClock();

        private SystemClock() {
        }

        @Override
        public Instant wallTime() {
            return Instant.now();
        }

        @Override
        public long monotonicNanos() {
            return System.nanoTime();
        }
    }
}
