package dev.darshan.agentrouter.reliability;

import java.util.SplittableRandom;

/**
 * Bounded status-lookup reconciliation: at most 20 lookups per invocation,
 * polling no faster than 500ms plus jitter, total duration bounded by the
 * workflow budget recomputed from the accepted-at wall-clock time.
 * Exhaustion without a confirmed backend state yields UNKNOWN_OUTCOME,
 * never a guessed terminal state.
 */
public final class BoundedReconciler {
    private final int maxLookups;
    private final long baseIntervalMillis;
    private final SplittableRandom random;

    public BoundedReconciler() {
        this(SciConfig.defaults().maxReconciliationLookups(),
                SciConfig.defaults().pollingInterval().toMillis(), System.nanoTime());
    }

    public BoundedReconciler(int maxLookups, long baseIntervalMillis, long seed) {
        if (maxLookups < 1 || maxLookups > 20) {
            throw new IllegalArgumentException("maxLookups must be within 1..20");
        }
        if (baseIntervalMillis < 500) {
            throw new IllegalArgumentException("polling interval must be >= 500ms");
        }
        this.maxLookups = maxLookups;
        this.baseIntervalMillis = baseIntervalMillis;
        this.random = new SplittableRandom(seed);
    }

    public boolean mayLookup(int lookupsUsed) {
        return lookupsUsed < maxLookups;
    }

    /** Next poll delay in [base, 2*base]ms; minimum 500ms plus jitter, never a sleep here. */
    public long pollDelayMillis() {
        return baseIntervalMillis + random.nextLong(baseIntervalMillis + 1);
    }

    public Outcome terminalForExhaustion() {
        return Outcome.UNKNOWN_OUTCOME;
    }

    public int maxLookups() {
        return maxLookups;
    }

    public long baseIntervalMillis() {
        return baseIntervalMillis;
    }
}
