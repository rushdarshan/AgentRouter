package dev.darshan.agentrouter.reliability;

import dev.darshan.agentrouter.job.AcceptanceResult;
import dev.darshan.agentrouter.job.JobRequest;
import dev.darshan.agentrouter.job.JobService;
import dev.darshan.agentrouter.job.PrincipalResolver;
import dev.darshan.agentrouter.job.SqliteJobStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** sci-exec-bounds + sci-job-control-queue: atomic last-slot, resubmit, preservation, cancel. */
class AdmissionTest {
    private static String memoryUrl() {
        return "jdbc:sqlite:file:admission-" + UUID.randomUUID() + "?mode=memory&cache=shared";
    }

    private static JobService service(SqliteJobStore store) {
        return new JobService(store, PrincipalResolver.forTests());
    }

    private static JobRequest request() {
        return new JobRequest(UUID.randomUUID().toString(), "roofit", Map.of(), null);
    }

    @Test
    void concurrentLastSlot() throws Exception {
        try (SqliteJobStore store = new SqliteJobStore(memoryUrl(), "i1", 2)) {
            JobService jobs = service(store);
            assertEquals(202, jobs.submit("operator", "prefill", request()).status());
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                List<Future<Integer>> futures = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    final String key = "race-" + i;
                    final JobRequest req = request();
                    futures.add(pool.submit(() -> {
                        assertTrue(start.await(10, TimeUnit.SECONDS));
                        return jobs.submit("operator", key, req).status();
                    }));
                }
                start.countDown();
                List<Integer> statuses = new ArrayList<>();
                for (Future<Integer> future : futures) statuses.add(future.get(30, TimeUnit.SECONDS));
                assertTrue(statuses.contains(202));
                assertTrue(statuses.contains(429));
                assertEquals(2, store.countOperations());
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void saturationResubmitResolves() {
        try (SqliteJobStore store = new SqliteJobStore(memoryUrl(), "i1", 1)) {
            JobService jobs = service(store);
            String id = UUID.randomUUID().toString();
            assertEquals(202, jobs.submit("operator", "k1",
                    new JobRequest(id, "roofit", Map.of(), null)).status());
            assertEquals(429, jobs.submit("operator", "k2", request()).status());
            AcceptanceResult resubmit = jobs.submit("operator", "k1",
                    new JobRequest(id, "roofit", Map.of(), null));
            assertEquals(200, resubmit.status());
        }
    }

    @Test
    void acceptedPreservedUnderSaturation() {
        try (SqliteJobStore store = new SqliteJobStore(memoryUrl(), "i1", 1)) {
            JobService jobs = service(store);
            String id = UUID.randomUUID().toString();
            assertEquals(202, jobs.submit("operator", "k1",
                    new JobRequest(id, "roofit", Map.of(), null)).status());
            assertEquals(429, jobs.submit("operator", "k2", request()).status());
            assertTrue(jobs.get("operator", id).isPresent());
            assertEquals(OperationState.ACCEPTED,
                    jobs.get("operator", id).orElseThrow().operationState());
        }
    }

    @Test
    void cancelUnderSaturation() {
        try (SqliteJobStore store = new SqliteJobStore(memoryUrl(), "i1", 1)) {
            JobService jobs = service(store);
            String id = UUID.randomUUID().toString();
            assertEquals(202, jobs.submit("operator", "k1",
                    new JobRequest(id, "roofit", Map.of(), null)).status());
            assertEquals(429, jobs.submit("operator", "k2", request()).status());
            assertTrue(jobs.cancel("operator", id).isPresent());
            assertTrue(jobs.cancel("operator", id).orElseThrow().cancellationRequested());
        }
    }

    @Test
    void rejectedReservationLeavesNoRow() {
        try (SqliteJobStore store = new SqliteJobStore(memoryUrl(), "i1", 1)) {
            JobService jobs = service(store);
            assertEquals(202, jobs.submit("operator", "k1", request()).status());
            String contender = UUID.randomUUID().toString();
            assertEquals(429, jobs.submit("operator", "k2",
                    new JobRequest(contender, "roofit", Map.of(), null)).status());
            assertEquals(1, store.countOperations());
            assertTrue(store.findAny(contender).isEmpty());
        }
    }
}
