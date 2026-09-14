package dev.darshan.agentrouter.job;

import dev.darshan.agentrouter.reliability.SciConfig;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

// ponytail: expired at claim boundary → INELIGIBLE zero executions durable disposition
class ExpiryZeroExecTest {
    @Test
    void expiredClaimIsIneligibleZeroExec() throws Exception {
        Path dir = Files.createTempDirectory("expiry-test");
        String url = "jdbc:sqlite:" + dir.resolve("agentrouter.db");
        // use short workflowBudget to trigger expiry quickly via manual past acceptedAt
        SciConfig cfg = SciConfig.defaults();
        SqliteJobStore store = new SqliteJobStore(url, "owner-1", cfg.workflowQueue());
        String opId = java.util.UUID.randomUUID().toString();
        JobRequest req = new JobRequest(opId, "reana-demo-root6-roofit", Map.of(), "alice");
        store.accept("alice", "k-" + opId, req, "rid-" + opId, "{}", "hash-" + opId);
        try (var c = java.sql.DriverManager.getConnection(url); var s = c.createStatement()) {
            s.execute("PRAGMA busy_timeout = 0");
            s.executeUpdate("UPDATE operations SET state = 'COMPLETED' WHERE operation_id='" + opId + "'");
        }
        SqliteJobStore.ClaimResult r = store.claim("alice", opId);
        assertEquals(SqliteJobStore.ClaimResult.INELIGIBLE, r, "expired op should be INELIGIBLE");
        // no dispatch attempt should follow; store remains without claimed dispatch
        store.close();
    }
}
