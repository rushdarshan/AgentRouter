package dev.darshan.agentrouter.reliability;

import java.util.SplittableRandom;

/** Bounded full-jitter retry calculation; scheduling is owned by the caller. */
public final class RetryPolicy {
    private final int maxAttempts;
    private final long seed;
    private final SplittableRandom random;

    public RetryPolicy() {
        this(3, System.nanoTime());
    }

    public RetryPolicy(int maxAttempts, long seed) {
        if (maxAttempts < 1 || maxAttempts > 3) throw new IllegalArgumentException("maxAttempts");
        this.maxAttempts = maxAttempts;
        this.seed = seed;
        this.random = new SplittableRandom(seed);
    }

    public boolean mayRetry(int attemptsAlreadyUsed, boolean transientFailure, boolean safeContract) {
        return attemptsAlreadyUsed < maxAttempts && transientFailure && safeContract;
    }

    public long nextDelayMillis(int attemptsAlreadyUsed) {
        long ceiling = Math.min(1000L, 100L << Math.max(0, attemptsAlreadyUsed));
        return random.nextLong(ceiling + 1);
    }

    public int maxAttempts() { return maxAttempts; }
    public long seed() { return seed; }
}
