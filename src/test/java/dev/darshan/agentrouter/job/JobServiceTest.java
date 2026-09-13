package dev.darshan.agentrouter.job;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JobServiceTest {
    @Test
    void acceptsAndDeduplicatesAtomically() {
        try (SqliteJobStore store = new SqliteJobStore(
                "jdbc:sqlite:file:job-contract-" + UUID.randomUUID() + "?mode=memory&cache=shared")) {
            JobService service = new JobService(store, PrincipalResolver.forTests());
            String operationId = UUID.randomUUID().toString();
            JobRequest request = new JobRequest(operationId, "roofit", Map.of("seed", 7), "forged");
            AcceptanceResult first = service.submit("operator", "k1", request);
            AcceptanceResult duplicate = service.submit("operator", "k1", request);
            assertEquals(202, first.status());
            assertEquals(200, duplicate.status());
            assertEquals(1, store.countOperations());
            assertEquals("operator", duplicate.representation().principalRef());
        }
    }

    @Test
    void viewerCannotSubmitAndCrossPrincipalReadIsHidden() {
        try (SqliteJobStore store = new SqliteJobStore(
                "jdbc:sqlite:file:job-auth-" + UUID.randomUUID() + "?mode=memory&cache=shared")) {
            JobService service = new JobService(store, PrincipalResolver.forTests());
            JobRequest request = new JobRequest(UUID.randomUUID().toString(), "roofit", Map.of(), "operator");
            assertEquals(403, service.submit("viewer", "k", request).status());
            assertTrue(service.get("viewer", request.operationId()).isEmpty());
        }
    }

    @Test
    void conflictsAndCancellationPreserveDurableIdentity() {
        try (SqliteJobStore store = new SqliteJobStore(
                "jdbc:sqlite:file:job-conflict-" + UUID.randomUUID() + "?mode=memory&cache=shared")) {
            JobService service = new JobService(store, PrincipalResolver.forTests());
            String id = UUID.randomUUID().toString();
            JobRequest first = new JobRequest(id, "roofit", Map.of("seed", 1), null);
            assertEquals(202, service.submit("operator", "key", first).status());
            assertEquals(409, service.submit("operator", "key", new JobRequest(id, "roofit",
                    Map.of("seed", 2), null)).status());
            assertEquals(409, service.submit("operator", "other-key", first).status());
            assertTrue(service.get("operator", id).isPresent());
            assertTrue(service.cancel("operator", id).isPresent());
            assertTrue(service.get("operator", id).get().cancellationRequested());
        }
    }

    @Test
    void queueCapacityRejectsOnlyNewOperations() {
        try (SqliteJobStore store = new SqliteJobStore(
                "jdbc:sqlite:file:job-capacity-" + UUID.randomUUID() + "?mode=memory&cache=shared",
                "capacity-test", 1)) {
            JobService service = new JobService(store, PrincipalResolver.forTests());
            JobRequest first = new JobRequest(UUID.randomUUID().toString(), "roofit", Map.of(), null);
            JobRequest second = new JobRequest(UUID.randomUUID().toString(), "roofit", Map.of(), null);
            assertEquals(202, service.submit("operator", "one", first).status());
            assertEquals(429, service.submit("operator", "two", second).status());
            assertEquals(200, service.submit("operator", "one", first).status());
        }
    }
}
