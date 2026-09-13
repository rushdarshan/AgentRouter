package dev.darshan.agentrouter.job;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * sci-job-control-queue: reserved control capacity. Saturated submission never
 * starves control; a full control queue rejects explicitly with no backend
 * contact; an admitted cancel always reaches the backend.
 */
class ControlTest {
    private static String memoryUrl() {
        return "jdbc:sqlite:file:control-" + UUID.randomUUID() + "?mode=memory&cache=shared";
    }

    @Test
    void fullControlQueueRejectsWithoutBackendContact() {
        ControlAdmission control = new ControlAdmission(1);
        assertTrue(control.tryAcquireControl());
        InMemoryBackendRuntime backend = new InMemoryBackendRuntime();
        DispatchService dispatch = new DispatchService(backend);
        assertTrue(dispatch.cancelIfAdmitted("sci-absent", control).isEmpty());
        assertEquals(0, backend.size());
        control.releaseControl();
    }

    @Test
    void admittedCancelReachesBackendUnderSaturation() {
        try (SqliteJobStore store = new SqliteJobStore(memoryUrl(), "i1", 1)) {
            JobService jobs = new JobService(store, PrincipalResolver.forTests());
            String id = UUID.randomUUID().toString();
            assertEquals(202, jobs.submit("operator", "k1",
                    new JobRequest(id, "roofit", Map.of(), null)).status());
            assertEquals(429, jobs.submit("operator", "k2",
                    new JobRequest(UUID.randomUUID().toString(), "roofit", Map.of(), null)).status());
            InMemoryBackendRuntime backend = new InMemoryBackendRuntime();
            DispatchService dispatch = new DispatchService(backend);
            dispatch.dispatchIfOwned(id, store::isOwner);
            backend.markStarted("sci-" + id);
            assertTrue(jobs.cancel("operator", id).orElseThrow().cancellationRequested());
            ControlAdmission control = new ControlAdmission(8);
            assertTrue(dispatch.cancelIfAdmitted("sci-" + id, control).orElseThrow());
            assertEquals("CANCELLED", backend.inspect("sci-" + id).orElseThrow().state());
            assertTrue(control.availableControlPermits() == 8);
        }
    }

    @Test
    void dispatchWithoutOwnershipNeverTouchesBackend() {
        InMemoryBackendRuntime backend = new InMemoryBackendRuntime();
        DispatchService dispatch = new DispatchService(backend);
        assertThrows(IllegalStateException.class,
                () -> dispatch.dispatchIfOwned(UUID.randomUUID().toString(), () -> false));
        assertEquals(0, backend.size());
    }
}
