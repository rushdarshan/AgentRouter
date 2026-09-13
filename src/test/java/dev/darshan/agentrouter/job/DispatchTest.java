package dev.darshan.agentrouter.job;

import dev.darshan.agentrouter.job.BackendRuntime.BackendHandle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** sci-job-create-start: identity naming, crash recovery, never-restart, no delete/recreate. */
class DispatchTest {
    @Test
    void identityDerivedNamingRace() throws Exception {
        InMemoryBackendRuntime backend = new InMemoryBackendRuntime();
        DispatchService dispatch = new DispatchService(backend);
        String id = UUID.randomUUID().toString();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<BackendHandle>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return dispatch.dispatch(id);
                }));
            }
            start.countDown();
            List<BackendHandle> handles = new ArrayList<>();
            for (Future<BackendHandle> future : futures) handles.add(future.get(30, TimeUnit.SECONDS));
            assertEquals(handles.get(0).identity(), handles.get(1).identity());
            assertEquals(1, backend.size());
            assertEquals(1, backend.executionCount());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void crashBeforeIdentityPersist() {
        InMemoryBackendRuntime backend = new InMemoryBackendRuntime();
        String id = UUID.randomUUID().toString();
        backend.createIfAbsent("sci-" + id, id);
        BackendHandle recovered = new DispatchService(backend).dispatch(id);
        assertEquals("sci-" + id, recovered.identity());
        assertEquals(id, backend.inspect("sci-" + id).orElseThrow().operationId());
        assertEquals(1, backend.size());
    }

    @Test
    void crashBeforeStartPersist() {
        InMemoryBackendRuntime backend = new InMemoryBackendRuntime();
        DispatchService dispatch = new DispatchService(backend);
        String id = UUID.randomUUID().toString();
        dispatch.dispatch(id);
        backend.markStarted("sci-" + id);
        BackendHandle again = dispatch.dispatch(id);
        assertEquals("sci-" + id, again.identity());
        assertTrue(backend.inspect("sci-" + id).orElseThrow().started());
        assertEquals(1, backend.size());
        assertEquals(1, backend.executionCount());
    }

    @Test
    void crashBeforeCompletionPersist() {
        InMemoryBackendRuntime backend = new InMemoryBackendRuntime();
        DispatchService dispatch = new DispatchService(backend);
        String id = UUID.randomUUID().toString();
        BackendHandle handle = dispatch.dispatch(id);
        backend.markStarted("sci-" + id);
        assertTrue(backend.cancel(handle.identity()));
        BackendHandle again = dispatch.dispatch(id);
        assertEquals(handle.identity(), again.identity());
        assertEquals("CANCELLED", backend.inspect("sci-" + id).orElseThrow().state());
        assertEquals(1, backend.size());
    }

    @Test
    void backendDisappear() {
        InMemoryBackendRuntime backend = new InMemoryBackendRuntime();
        Optional<BackendRuntime.BackendObservation> missing =
                backend.inspect("sci-" + UUID.randomUUID());
        assertTrue(missing.isEmpty());
        assertEquals(0, backend.size());
    }

    @Test
    void uncertainNoReexecute() {
        InMemoryBackendRuntime backend = new InMemoryBackendRuntime();
        String other = UUID.randomUUID().toString();
        String id = UUID.randomUUID().toString();
        backend.createIfAbsent("sci-" + id, other);
        assertThrows(IllegalStateException.class, () -> new DispatchService(backend).dispatch(id));
        assertEquals(1, backend.size());
        assertEquals(other, backend.inspect("sci-" + id).orElseThrow().operationId());
    }

    @Test
    void neverRestartExecuted() {
        InMemoryBackendRuntime backend = new InMemoryBackendRuntime();
        DispatchService dispatch = new DispatchService(backend);
        String id = UUID.randomUUID().toString();
        assertTrue(dispatch.dispatch(id).created());
        backend.markStarted("sci-" + id);
        assertEquals("sci-" + id, dispatch.dispatch(id).identity());
        assertEquals("sci-" + id, dispatch.dispatch(id).identity());
        assertEquals(1, backend.size());
        assertEquals(1, backend.executionCount());
    }
}
