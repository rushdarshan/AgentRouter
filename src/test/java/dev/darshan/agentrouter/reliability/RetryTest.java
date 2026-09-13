package dev.darshan.agentrouter.reliability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** sci-exec-retry: safe-contract gating + three attempts per invocation. */
class RetryTest {
    @Test
    void needsSafeContract() {
        RetryPolicy policy = new RetryPolicy(3, 7L);
        assertFalse(policy.mayRetry(0, true, false));
        assertFalse(policy.mayRetry(0, false, true));
        assertFalse(policy.mayRetry(0, false, false));
        assertTrue(policy.mayRetry(0, true, true));
    }

    @Test
    void threeAttemptsPerInvocation() {
        RetryPolicy policy = new RetryPolicy(3, 7L);
        assertTrue(policy.mayRetry(0, true, true));
        assertTrue(policy.mayRetry(1, true, true));
        assertTrue(policy.mayRetry(2, true, true));
        assertFalse(policy.mayRetry(3, true, true));
        for (int used = 0; used < 3; used++) {
            long delay = policy.nextDelayMillis(used);
            assertTrue(delay >= 0 && delay <= 1000, "delay out of full-jitter bounds: " + delay);
        }
        assertEquals(7L, policy.seed());
    }
}
