package dev.darshan.agentrouter.reliability;

import dev.darshan.agentrouter.job.JobRepresentation;
import dev.darshan.agentrouter.job.JobRequest;
import dev.darshan.agentrouter.job.JobService;
import dev.darshan.agentrouter.job.PrincipalResolver;
import dev.darshan.agentrouter.job.SqliteJobStore;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** sci-exec-cancel: terminal wins, idempotent re-cancel, UNKNOWN records bounded intent. */
class CancelTest {
    private static String memoryUrl() {
        return "jdbc:sqlite:file:cancel-" + UUID.randomUUID() + "?mode=memory&cache=shared";
    }

    private static void setState(String url, String id, String state, String outcome) throws Exception {
        try (var connection = DriverManager.getConnection(url);
             var update = connection.prepareStatement(
                     "UPDATE operations SET state = ?, outcome = ? WHERE operation_id = ?")) {
            update.setString(1, state);
            update.setString(2, outcome);
            update.setString(3, id);
            assertEquals(1, update.executeUpdate());
        }
    }

    @Test
    void completionWins() throws Exception {
        String url = memoryUrl();
        try (SqliteJobStore store = new SqliteJobStore(url)) {
            JobService jobs = new JobService(store, PrincipalResolver.forTests());
            String id = UUID.randomUUID().toString();
            assertEquals(202, jobs.submit("operator", "k1",
                    new JobRequest(id, "roofit", Map.of(), null)).status());
            setState(url, id, "COMPLETED", "SUCCESS");
            JobRepresentation after = jobs.cancel("operator", id).orElseThrow();
            assertEquals(OperationState.COMPLETED, after.operationState());
            assertEquals(Outcome.SUCCESS, after.outcome());
        }
    }

    @Test
    void failureWins() throws Exception {
        String url = memoryUrl();
        try (SqliteJobStore store = new SqliteJobStore(url)) {
            JobService jobs = new JobService(store, PrincipalResolver.forTests());
            String id = UUID.randomUUID().toString();
            assertEquals(202, jobs.submit("operator", "k1",
                    new JobRequest(id, "roofit", Map.of(), null)).status());
            setState(url, id, "FAILED", "EXECUTION_FAILURE");
            JobRepresentation after = jobs.cancel("operator", id).orElseThrow();
            assertEquals(OperationState.FAILED, after.operationState());
        }
    }

    @Test
    void alreadyCancelledIdempotent() {
        try (SqliteJobStore store = new SqliteJobStore(memoryUrl())) {
            JobService jobs = new JobService(store, PrincipalResolver.forTests());
            String id = UUID.randomUUID().toString();
            assertEquals(202, jobs.submit("operator", "k1",
                    new JobRequest(id, "roofit", Map.of(), null)).status());
            assertTrue(jobs.cancel("operator", id).orElseThrow().cancellationRequested());
            JobRepresentation again = jobs.cancel("operator", id).orElseThrow();
            assertTrue(again.cancellationRequested());
        }
    }

    @Test
    void unknownReconcileBounded() throws Exception {
        String url = memoryUrl();
        try (SqliteJobStore store = new SqliteJobStore(url)) {
            JobService jobs = new JobService(store, PrincipalResolver.forTests());
            String id = UUID.randomUUID().toString();
            assertEquals(202, jobs.submit("operator", "k1",
                    new JobRequest(id, "roofit", Map.of(), null)).status());
            setState(url, id, "UNKNOWN", "UNKNOWN_OUTCOME");
            JobRepresentation reconciling = jobs.cancel("operator", id).orElseThrow();
            assertTrue(reconciling.cancellationRequested());
            BoundedReconciler reconciler = new BoundedReconciler();
            assertTrue(reconciler.mayLookup(0));
            assertFalse(reconciler.mayLookup(20));
            assertEquals(Outcome.UNKNOWN_OUTCOME, reconciler.terminalForExhaustion());
        }
    }
}
