package dev.darshan.agentrouter.reliability;

import dev.darshan.agentrouter.job.Canonicalizer;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ScientificContractTest {
    @Test
    void defaultsAreValidAndOutOfRangeIsRejected() {
        assertDoesNotThrow(() -> SciConfigValidator.validate(SciConfig.defaults()));
        SciConfig invalid = new SciConfig("sci-config-v1", 16, 4, 64, 1, 4, 8,
                3, 20, java.time.Duration.ofMillis(10), java.time.Duration.ofHours(168),
                java.time.Duration.ofHours(24), "jwt", "sci-c14n-v1");
        assertThrows(IllegalArgumentException.class, () -> SciConfigValidator.validate(invalid));
    }

    @Test
    void canonicalizationIsStableAndRejectsDuplicateKeys() {
        assertEquals(Canonicalizer.canonicalize(Map.of("b", 2, "a", 1)),
                "{\"a\":1,\"b\":2}");
        assertThrows(IllegalArgumentException.class,
                () -> Canonicalizer.canonicalize("{\"a\":1,\"a\":2}"));
    }

    @Test
    void operationIdRequiresUuidV4() {
        UUID v4 = UUID.randomUUID();
        assertEquals(v4.toString(), OperationId.parse(v4.toString()).toString());
        assertThrows(IllegalArgumentException.class,
                () -> OperationId.parse("00000000-0000-0000-0000-000000000000"));
    }

    @Test
    void budgetAndRetryRemainBounded() {
        var clock = new dev.darshan.agentrouter.monitoring.ManualClock(java.time.Instant.EPOCH);
        Budget budget = new Budget(clock, Duration.ofSeconds(1));
        assertFalse(budget.expired());
        clock.advanceMillis(1001);
        assertTrue(budget.expired());
        RetryPolicy retry = new RetryPolicy(3, 42);
        assertTrue(retry.mayRetry(0, true, true));
        assertFalse(retry.mayRetry(3, true, true));
        assertFalse(retry.mayRetry(0, true, false));
        assertTrue(retry.nextDelayMillis(2) <= 1000);
    }
}
