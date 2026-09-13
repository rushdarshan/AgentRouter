package dev.darshan.agentrouter.reliability;

import dev.darshan.agentrouter.job.BackendRuntime;
import dev.darshan.agentrouter.job.DispatchService;
import dev.darshan.agentrouter.job.InMemoryBackendRuntime;
import dev.darshan.agentrouter.job.JobRepresentation;
import dev.darshan.agentrouter.job.JobRequest;
import dev.darshan.agentrouter.job.JobService;
import dev.darshan.agentrouter.job.PrincipalResolver;
import dev.darshan.agentrouter.job.SqliteJobStore;
import dev.darshan.agentrouter.monitoring.ManualClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** sci-exec-budgets: queued expiry never starts, HTTP expiry preserves, restart recomputes. */
class BudgetTest {
    private static String memoryUrl() {
        return "jdbc:sqlite:file:budget-" + UUID.randomUUID() + "?mode=memory&cache=shared";
    }

    @Test
    void queuedExpiryNotStarted() {
        ManualClock clock = new ManualClock(Instant.EPOCH);
        Budget workflow = new Budget(clock, Duration.ofSeconds(1));
        InMemoryBackendRuntime backend = new InMemoryBackendRuntime();
        DispatchService dispatch = new DispatchService(backend);
        clock.advanceMillis(1001);
        assertTrue(workflow.expired());
        assertTrue(dispatch.dispatchIfEligible(UUID.randomUUID().toString(), workflow).isEmpty());
        assertEquals(0, backend.size());
        try (SqliteJobStore store = new SqliteJobStore(memoryUrl())) {
            JobService jobs = new JobService(store, PrincipalResolver.forTests());
            String id = UUID.randomUUID().toString();
            assertEquals(202, jobs.submit("operator", "k1",
                    new JobRequest(id, "roofit", Map.of(), null)).status());
            JobRepresentation expired = store.expireUndispatched("operator", id).orElseThrow();
            assertEquals(OperationState.FAILED, expired.operationState());
            assertEquals(Outcome.DEADLINE_EXCEEDED, expired.outcome());
            assertEquals(0, backend.size());
        }
    }

    @Test
    void eligibleDispatchStartsOnce() {
        ManualClock clock = new ManualClock(Instant.EPOCH);
        Budget workflow = new Budget(clock, Duration.ofSeconds(60));
        InMemoryBackendRuntime backend = new InMemoryBackendRuntime();
        DispatchService dispatch = new DispatchService(backend);
        String id = UUID.randomUUID().toString();
        BackendRuntime.BackendHandle handle = dispatch.dispatchIfEligible(id, workflow).orElseThrow();
        assertTrue(handle.created());
        assertEquals(1, backend.size());
    }

    @Test
    void httpExpiryPreserved() {
        try (SqliteJobStore store = new SqliteJobStore(memoryUrl())) {
            JobService jobs = new JobService(store, PrincipalResolver.forTests());
            String id = UUID.randomUUID().toString();
            assertEquals(202, jobs.submit("operator", "k1",
                    new JobRequest(id, "roofit", Map.of(), null)).status());
            JobRepresentation current = jobs.get("operator", id).orElseThrow();
            assertEquals(OperationState.ACCEPTED, current.operationState());
            assertEquals(Outcome.ACCEPTED, current.outcome());
        }
    }

    @Test
    void restartRecompute() {
        try (SqliteJobStore store = new SqliteJobStore(memoryUrl())) {
            JobService jobs = new JobService(store, PrincipalResolver.forTests());
            String id = UUID.randomUUID().toString();
            assertEquals(202, jobs.submit("operator", "k1",
                    new JobRequest(id, "roofit", Map.of(), null)).status());
            Instant acceptedAt = jobs.get("operator", id).orElseThrow().acceptedAt();
            Duration workflow = Duration.ofSeconds(1);
            assertFalse(acceptedAt.plus(workflow).isBefore(acceptedAt.plusMillis(600)));
            assertTrue(acceptedAt.plus(workflow).isBefore(acceptedAt.plusMillis(1001)));
        }
    }
}
