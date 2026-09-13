package dev.darshan.agentrouter.job;

import dev.darshan.agentrouter.reliability.BoundedReconciler;
import dev.darshan.agentrouter.reliability.Outcome;
import dev.darshan.agentrouter.reliability.SciConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** sci-job-reconcile-bounds: max lookups, minimum polling interval, config validation. */
class ReconcileTest {
    @Test
    void maxLookups() {
        BoundedReconciler reconciler = new BoundedReconciler();
        assertEquals(20, reconciler.maxLookups());
        assertTrue(reconciler.mayLookup(0));
        assertTrue(reconciler.mayLookup(19));
        assertFalse(reconciler.mayLookup(20));
        assertEquals(Outcome.UNKNOWN_OUTCOME, reconciler.terminalForExhaustion());
    }

    @Test
    void pollInterval() {
        BoundedReconciler reconciler = new BoundedReconciler();
        assertEquals(SciConfig.defaults().pollingInterval().toMillis(), reconciler.baseIntervalMillis());
        for (int i = 0; i < 25; i++) {
            long delay = reconciler.pollDelayMillis();
            assertTrue(delay >= 500, "poll below minimum: " + delay);
        }
        assertThrows(IllegalArgumentException.class, () -> new BoundedReconciler(21, 500, 1L));
        assertThrows(IllegalArgumentException.class, () -> new BoundedReconciler(20, 499, 1L));
    }
}
