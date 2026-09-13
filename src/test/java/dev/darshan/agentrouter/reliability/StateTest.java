package dev.darshan.agentrouter.reliability;

import dev.darshan.agentrouter.job.AcceptanceResult;
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

/** sci-exec-identities ack semantics + terminal-no-regression. */
class StateTest {
    private static String memoryUrl() {
        return "jdbc:sqlite:file:state-" + UUID.randomUUID() + "?mode=memory&cache=shared";
    }

    private static JobService service(SqliteJobStore store) {
        return new JobService(store, PrincipalResolver.forTests());
    }

    private static String opId() {
        return UUID.randomUUID().toString();
    }

    @Test
    void ackNotSuccess() {
        try (SqliteJobStore store = new SqliteJobStore(memoryUrl())) {
            AcceptanceResult accepted = service(store).submit("operator", "k1",
                    new JobRequest(opId(), "roofit", Map.of("seed", 7), null));
            assertEquals(202, accepted.status());
            assertEquals(Outcome.ACCEPTED, accepted.representation().outcome());
            assertEquals(OperationState.ACCEPTED, accepted.representation().operationState());
            assertNotEquals(Outcome.SUCCESS, accepted.representation().outcome());
        }
    }

    @Test
    void terminalNoRegress() throws Exception {
        String url = memoryUrl();
        try (SqliteJobStore store = new SqliteJobStore(url)) {
            JobService jobs = service(store);
            String id = opId();
            assertEquals(202, jobs.submit("operator", "k1",
                    new JobRequest(id, "roofit", Map.of(), null)).status());
            try (var connection = DriverManager.getConnection(url);
                 var update = connection.prepareStatement(
                         "UPDATE operations SET state = 'COMPLETED', outcome = 'SUCCESS' WHERE operation_id = ?")) {
                update.setString(1, id);
                assertEquals(1, update.executeUpdate());
            }
            assertTrue(jobs.get("operator", "00000000-0000-4000-8000-000000000000").isEmpty());
            JobRepresentation current = jobs.get("operator", id).orElseThrow();
            assertEquals(OperationState.COMPLETED, current.operationState());
            // A failed backend observation after terminal must not regress it.
            JobRepresentation afterFailed = store.recordBackendObservation("operator", id,
                    new dev.darshan.agentrouter.job.BackendRuntime.BackendObservation(
                            "sci-" + id, id, true, true, "FAILED", 1)).orElseThrow();
            assertEquals(OperationState.COMPLETED, afterFailed.operationState());
            JobRepresentation afterCancel = jobs.cancel("operator", id).orElseThrow();
            assertEquals(OperationState.COMPLETED, afterCancel.operationState());
        }
    }
}
